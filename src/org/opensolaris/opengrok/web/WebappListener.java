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
 * Copyright 2006 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.MercurialRepository;

/**
 * Populate the Mercurial Repositories
 * @author Trond Norbye
 */
public final class WebappListener  implements ServletContextListener  {
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        ServletContext context = servletContextEvent.getServletContext();

        String scanrepos = context.getInitParameter("SCAN_REPOS");
        if (scanrepos != null && scanrepos.equalsIgnoreCase("true")) {
            System.out.println("Scanning for repositories...");
            final String source = context.getInitParameter("SRC_ROOT");
            if (source != null) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        HistoryGuru.getInstance().addExternalRepositories((new File(source)).listFiles());
                    }
                });
                
                t.setDaemon(true);
                t.start();
            }
        } else {
            System.out.println("Will not scan for external repositories...");
        }
    }
    
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
    }
}
