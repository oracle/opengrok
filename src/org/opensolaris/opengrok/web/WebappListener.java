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
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.auth.AccessControl;
import org.opensolaris.opengrok.auth.DenyAccessControl;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Populate the Mercurial Repositories
 * @author Trond Norbye
 */
public final class WebappListener implements ServletContextListener {

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        ServletContext context = servletContextEvent.getServletContext();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        String config = context.getInitParameter("CONFIGURATION");
        if (config == null) {
            OpenGrokLogger.getLogger().severe("CONFIGURATION section missing in web.xml");
        } else {
            try {
                env.readConfiguration(new File(config));
            } catch (IOException ex) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "OpenGrok Configuration error. Failed to read config file: ", ex);
            }
        }

        String address = context.getInitParameter("ConfigAddress");
        if (address != null && address.length() > 0) {
            OpenGrokLogger.getLogger().log(Level.CONFIG, "Will listen for configuration on [{0}]", address);
            String[] cfg = address.split(":");
            if (cfg.length == 2) {
                try {
                    SocketAddress addr = new InetSocketAddress(InetAddress.getByName(cfg[0]), Integer.parseInt(cfg[1]));
                    if (!RuntimeEnvironment.getInstance().startConfigurationListenerThread(addr)) {
                        OpenGrokLogger.getLogger().log(Level.SEVERE, "OpenGrok: Failed to start configuration listener thread");
                    }
                } catch (NumberFormatException ex) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "OpenGrok: Failed to start configuration listener thread:", ex);
                } catch (UnknownHostException ex) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "OpenGrok: Failed to start configuration listener thread:", ex);
                }
            } else {
                OpenGrokLogger.getLogger().log(Level.SEVERE, "Incorrect format for the configuration address: ");
                for (int i = 0; i < cfg.length; ++i) {
                    OpenGrokLogger.getLogger().log(Level.SEVERE, "[{0}]", cfg[i]);
                }
            }
        }

        String accessController = context.getInitParameter("org.opensolaris.opengrok.auth.AccessControl");
        if (accessController != null && accessController.length() > 0) {
            try {
                ClassLoader loader = AccessControl.class.getClassLoader();
                Class<?> clazz = loader.loadClass(accessController);
                env.setServletAccessController((AccessControl)clazz.newInstance());
            } catch (Throwable t) {
                env.setServletAccessController(new DenyAccessControl());
                OpenGrokLogger.getLogger().log(Level.SEVERE, "OpenGrok: Failed instanciate Servlet Access Controller:", t);
            }
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        RuntimeEnvironment.getInstance().stopConfigurationListenerThread();
    }
}
