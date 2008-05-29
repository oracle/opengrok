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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;

/**
 * Populate the Mercurial Repositories
 * @author Trond Norbye
 */
public final class WebappListener  implements ServletContextListener  {
    
    private String getFileName(ServletContext context, String variable, boolean directory) {
        String value = context.getInitParameter(variable);
        if (value == null) {
            System.err.println("OpenGrok: configuration error. " + variable + " not specified in web.xml");
            return null;
        }
        File file = new File(value);
        if (!file.exists()) {
            System.err.println("OpenGrok: " + variable + " configuration error. " + value + " does not exist.");
            return null;
        }
        
        if (directory) {
            if (!file.isDirectory()) {
                System.err.println("OpenGrok: " + variable + " configuration error. " + value + " is not a directory.");
                return null;
            }
        }
        
        String can = null;
        try {
            can = file.getCanonicalPath();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        if (can == null) {
            System.err.println("OpenGrok: " + variable + " configuration error. Failed to get canonical name for " + value + " is not a directory.");
            return null;
        }
        
        return can;
    }
    
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        ServletContext context = servletContextEvent.getServletContext();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        
        String config = context.getInitParameter("CONFIGURATION");
        if (config != null) {
            try {
                env.readConfiguration(new File(config));
            } catch (IOException ex) {
                System.err.println("OpenGrok Configuration error. Failed to read config file: ");
                ex.printStackTrace();
            }
        } else {
            String value;
            
            if ((value = getFileName(context, "SRC_ROOT", true)) == null) {
                return;
            }
            env.setSourceRoot(value);
            
            if ((value = getFileName(context, "DATA_ROOT", true)) == null) {
                return;
            }
            env.setDataRoot(value);
            
            String scanrepos = context.getInitParameter("SCAN_REPOS");
            if (scanrepos != null && scanrepos.equalsIgnoreCase("true")) {
                final String source = env.getSourceRootPath();
                if (source != null) {
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            System.out.println("Scanning for repositories...");
                            long start = System.currentTimeMillis();
                            HistoryGuru.getInstance().addRepositories(source);
                            long stop = System.currentTimeMillis();
                            System.out.println("Done searching for repositories: " + ((stop - start)/1000) + "s");
                        }
                    });
                    
                    t.setDaemon(true);
                    t.start();
                }
            } else {
                System.out.println("Will not scan for external repositories...");
            }
        }
        
        String address = context.getInitParameter("ConfigAddress");
        if (address != null && address.length() > 0) {
            System.out.println("Will listen for configuration on [" + address + "]");
            String[] cfg = address.split(":");
            if (cfg.length == 2) {
                try {
                    InetAddress host = InetAddress.getByName(cfg[0]);
                    SocketAddress addr = new InetSocketAddress(InetAddress.getByName(cfg[0]), Integer.parseInt(cfg[1]));
                    if (!RuntimeEnvironment.getInstance().startConfigurationListenerThread(addr)) {
                        System.err.println("OpenGrok: Failed to start configuration listener thread");
                    }
                } catch (NumberFormatException ex) {
                    System.err.println("OpenGrok: Failed to start configuration listener thread:");
                    ex.printStackTrace();
                } catch (UnknownHostException ex) {
                    System.err.println("OpenGrok: Failed to start configuration listener thread:");
                    ex.printStackTrace();
                }
            } else {
                
                for (int i = 0; i < cfg.length; ++i) {
                    System.out.println("[" + cfg[i] + "]");
                }
            }
        }
    }
    
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        RuntimeEnvironment.getInstance().stopConfigurationListenerThread();
    }
}

