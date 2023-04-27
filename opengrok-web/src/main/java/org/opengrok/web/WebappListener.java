/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import io.micrometer.core.instrument.Timer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import org.opengrok.indexer.Info;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.OpenGrokThreadFactory;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.IndexCheck;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Statistics;
import org.opengrok.indexer.web.SearchHelper;
import org.opengrok.web.api.ApiTaskManager;
import org.opengrok.web.api.v1.controller.ConfigurationController;
import org.opengrok.web.api.v1.controller.ProjectsController;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterServiceFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initialize webapp context.
 *
 * @author Trond Norbye
 */
public final class WebappListener implements ServletContextListener, ServletRequestListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebappListener.class);
    private final Timer startupTimer = Timer.builder("webapp.startup.latency").
                description("web application startup latency").
                register(Metrics.getPrometheusRegistry());

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        Instant start = Instant.now();

        ServletContext context = servletContextEvent.getServletContext();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        LOGGER.log(Level.INFO, "Starting webapp with version {0} ({1})",
                    new Object[]{Info.getVersion(), Info.getRevision()});

        String config = context.getInitParameter("CONFIGURATION");
        if (config == null) {
            throw new Error("CONFIGURATION parameter missing in the web.xml file");
        } else {
            try {
                env.readConfiguration(new File(config), CommandTimeoutType.WEBAPP_START);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Configuration error. Failed to read config file: ", ex);
            }
        }

        String serverInfo = context.getServerInfo();
        LOGGER.log(Level.INFO, "running inside {0}", serverInfo);
        if (serverInfo.startsWith("Apache Tomcat")) {
            int idx;
            if ((idx = serverInfo.indexOf('/')) > 0) {
                String version = serverInfo.substring(idx + 1);
                if (!version.startsWith("10.")) {
                    LOGGER.log(Level.SEVERE, "Unsupported Tomcat version: {0}", version);
                    throw new Error("Unsupported Tomcat version");
                }
            }
        }

        /*
         * Create a new instance of authorization framework. If the code above
         * (reading the configuration) failed then the plugin directory is
         * possibly {@code null} causing the framework to allow every request.
         */
        env.setAuthorizationFramework(new AuthorizationFramework(env.getPluginDirectory(), env.getPluginStack()));
        env.getAuthorizationFramework().reload();

        if (env.isWebappCtags() && !env.validateUniversalCtags()) {
            LOGGER.warning("Didn't find Universal Ctags for --webappCtags");
        }

        String pluginDirectory = env.getPluginDirectory();
        if (pluginDirectory != null && env.isAuthorizationWatchdog()) {
            env.getWatchDog().start(new File(pluginDirectory));
        }

        // Check index(es).
        checkIndex(env);

        env.startExpirationTimer();

        ApiTaskManager.getInstance().setContextPath(context.getContextPath());
        // register API task queues
        ApiTaskManager.getInstance().addPool(ProjectsController.PROJECTS_PATH, 1);
        // Used by ConfigurationController#reloadAuthorization()
        ApiTaskManager.getInstance().addPool("authorization", 1);
        ApiTaskManager.getInstance().addPool(ConfigurationController.PATH, 1);

        startupTimer.record(Duration.between(start, Instant.now()));
    }

    /**
     * Checks the index(es). If projects are enabled then each project with invalid index
     * is marked as not being indexed.
     * @param env runtime environment
     */
    private void checkIndex(RuntimeEnvironment env) {
        if (env.isProjectsEnabled()) {
            Map<String, Project> projects = env.getProjects();
            Path indexRoot = Path.of(env.getDataRootPath(), IndexDatabase.INDEX_DIR);
            if (indexRoot.toFile().exists()) {
                LOGGER.log(Level.FINE, "Checking index versions for all projects");
                Statistics statistics = new Statistics();
                ExecutorService executor = Executors.newFixedThreadPool(env.getRepositoryInvalidationParallelism(),
                        new OpenGrokThreadFactory("webapp-index-check"));
                for (Map.Entry<String, Project> projectEntry : projects.entrySet()) {
                    executor.submit(() -> {
                        try {
                            IndexCheck.checkDir(Path.of(indexRoot.toString(), projectEntry.getKey()),
                                    IndexCheck.IndexCheckMode.VERSION, projectEntry.getKey());
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING,
                                    String.format("Project %s index check failed, marking as not indexed",
                                            projectEntry.getKey()), e);
                            projectEntry.getValue().setIndexed(false);
                        }
                    });
                }
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(env.getIndexCheckTimeout(), TimeUnit.SECONDS)) {
                        LOGGER.log(Level.WARNING, "index version check took more than {0} seconds",
                                env.getIndexCheckTimeout());
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "failed to await termination of index version check");
                    executor.shutdownNow();
                }
                statistics.report(LOGGER, Level.FINE, "Index version check for all projects done");
            }
        } else {
            LOGGER.log(Level.FINE, "Checking index");
            try {
                IndexCheck.checkDir(Path.of(env.getDataRootPath(), IndexDatabase.INDEX_DIR),
                        IndexCheck.IndexCheckMode.VERSION, "");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "index check failed", e);
            }
            LOGGER.log(Level.FINE, "Index check done");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.getIndexerParallelizer().bounce();
        env.getWatchDog().stop();
        env.stopExpirationTimer();
        try {
            env.shutdownRevisionExecutor();
            env.shutdownSearchExecutor();
            env.shutdownDirectoryListingExecutor();
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Could not shutdown revision executor", e);
        }

        // need to explicitly close the suggester service because it might have scheduled rebuild which could prevent
        // the web application from closing
        SuggesterServiceFactory.getDefault().close();

        // destroy queue(s) of API tasks
        try {
            ApiTaskManager.getInstance().shutdown();
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "could not shutdown API task manager cleanly", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestInitialized(ServletRequestEvent e) {
        // pass
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDestroyed(ServletRequestEvent e) {
        PageConfig.cleanup(e.getServletRequest());
        SearchHelper sh = (SearchHelper) e.getServletRequest().getAttribute(SearchHelper.REQUEST_ATTR);
        if (sh != null) {
            sh.destroy();
        }

        AnalyzerGuru.returnAnalyzers();
    }
}
