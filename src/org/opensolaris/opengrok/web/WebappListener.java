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
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
import org.opensolaris.opengrok.authorization.AuthorizationFramework;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;

import static org.opensolaris.opengrok.configuration.Configuration.PLUGIN_DIRECTORY_DEFAULT;

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

        String pluginDirectory = context.getInitParameter(AUTHORIZATION_PLUGIN_DIRECTORY);
        if (pluginDirectory != null) {
            env.getConfiguration().setPluginDirectory(pluginDirectory);
            AuthorizationFramework.getInstance(); // start + load
        } else {
            if (env.getDataRootPath() == null) {
                env.getConfiguration().setPluginDirectory(PLUGIN_DIRECTORY_DEFAULT);
            } else {
                env.getConfiguration().setPluginDirectory(env.getDataRootPath() + "/../" + PLUGIN_DIRECTORY_DEFAULT);
            }
            LOGGER.log(Level.INFO, AUTHORIZATION_PLUGIN_DIRECTORY + " is not set in web.xml. Default location will be used.");
        }

        String watchDog = context.getInitParameter(ENABLE_AUTHORIZATION_WATCH_DOG);
        if (pluginDirectory != null && watchDog != null && Boolean.parseBoolean(watchDog)) {
            RuntimeEnvironment.getInstance().startWatchDogService(new File(pluginDirectory));
        }

        RuntimeEnvironment.getInstance().startIndexReopenThread();
        RuntimeEnvironment.getInstance().startExpirationTimer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        RuntimeEnvironment.getInstance().stopConfigurationListenerThread();
        RuntimeEnvironment.getInstance().stopWatchDogService();
        RuntimeEnvironment.getInstance().stopIndexReopenThread();
        RuntimeEnvironment.getInstance().stopExpirationTimer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDestroyed(ServletRequestEvent e) {
        PageConfig.cleanup(e.getServletRequest());
        SearchHelper sh = (SearchHelper) e.getServletRequest().getAttribute("SearchHelper");
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
