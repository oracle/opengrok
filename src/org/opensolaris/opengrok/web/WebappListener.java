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
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.WatchService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import org.json.simple.parser.ParseException;
import org.opensolaris.opengrok.authorization.AuthorizationFramework;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Initialize webapp context
 *
 * @author Trond Norbye
 */
public final class WebappListener
        implements ServletContextListener, ServletRequestListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebappListener.class);
    private static final String ENABLE_AUTHORIZATION_WATCH_DOG = "enableAuthorizationWatchDog";
    private static final String AUTHORIZATION_PLUGIN_DIRECTORY = "authorizationPluginDirectory";

    private Thread thread;
    private WatchService watcher;

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        ServletContext context = servletContextEvent.getServletContext();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        String config = context.getInitParameter("CONFIGURATION");
        if (config == null) {
            LOGGER.severe("CONFIGURATION section missing in web.xml");
        } else {
            try {
                env.readConfiguration(new File(config));
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "OpenGrok Configuration error. Failed to read config file: ", ex);
            }
        }

        /**
         * Create a new instance of authorization framework. If the code above
         * (reading the configuration) failed then the plugin directory is
         * possibly {@code null} causing the framework to allow every request.
         */
        env.setAuthorizationFramework(new AuthorizationFramework(env.getPluginDirectory()));

        String address = context.getInitParameter("ConfigAddress");
        if (address != null && address.length() > 0) {
            LOGGER.log(Level.CONFIG, "Will listen for configuration on [{0}]", address);
            String[] cfg = address.split(":");
            if (cfg.length == 2) {
                try {
                    SocketAddress addr = new InetSocketAddress(InetAddress.getByName(cfg[0]), Integer.parseInt(cfg[1]));
                    if (!RuntimeEnvironment.getInstance().startConfigurationListenerThread(addr)) {
                        LOGGER.log(Level.SEVERE, "OpenGrok: Failed to start configuration listener thread");
                    }
                } catch (NumberFormatException | UnknownHostException ex) {
                    LOGGER.log(Level.SEVERE, "OpenGrok: Failed to start configuration listener thread:", ex);
                }
            } else {
                LOGGER.log(Level.SEVERE, "Incorrect format for the configuration address: ");
                for (int i = 0; i < cfg.length; ++i) {
                    LOGGER.log(Level.SEVERE, "[{0}]", cfg[i]);
                }
            }
        }

        try {
            RuntimeEnvironment.getInstance().loadStatistics();
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "Could not load statistics from a file.", ex);
        } catch (ParseException ex) {
            LOGGER.log(Level.SEVERE, "Could not parse statistics from a file.", ex);
        }

        if (env.getConfiguration().getPluginDirectory() != null && env.isAuthorizationWatchdog()) {
            RuntimeEnvironment.getInstance().startWatchDogService(new File(env.getConfiguration().getPluginDirectory()));
        }

        RuntimeEnvironment.getInstance().startExpirationTimer();

        try {
            RuntimeEnvironment.getInstance().loadStatistics();
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "Could not load statistics from a file.", ex);
        } catch (ParseException ex) {
            LOGGER.log(Level.SEVERE, "Could not parse statistics from a file.", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        RuntimeEnvironment.getInstance().stopConfigurationListenerThread();
        RuntimeEnvironment.getInstance().stopWatchDogService();
        RuntimeEnvironment.getInstance().stopExpirationTimer();
        try {
            RuntimeEnvironment.getInstance().saveStatistics();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not save statistics into a file.", ex);
        }
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestInitialized(ServletRequestEvent e) {
        // pass through
    }
}
