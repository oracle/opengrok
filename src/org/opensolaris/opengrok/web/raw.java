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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryReader;

/**
 * Gets different versions of a file
 *
 * @author Chandan
 */
public class raw extends HttpServlet {
    /**
     * Get the modification time of the file. For the &amp;head&amp; version
     * this will be the file modification time, but for a named revision I need
     * to search the hisstory log.
     *
     * @param request the http request
     * @return the number of milliseconds since epoch
     *         (or -1 if the time is not known)
     */
    protected long getLastModified(HttpServletRequest request) {
        String path = request.getPathInfo();
        if(path == null) {
            path = "";
        }
        
        RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
        environment.register();
        String rawSource = environment.getSourceRootPath();
        String resourcePath = rawSource + path;
        File resourceFile = new File(resourcePath);
        resourcePath = resourceFile.getAbsolutePath();
        
        long ret = -1;        
        if (!(resourcePath.length() < rawSource.length()
                || !resourcePath.startsWith(rawSource)
                || !resourceFile.canRead()
                || environment.getIgnoredNames().ignore(resourceFile)
                || resourceFile.isDirectory())) {
            
            String rev;
            if ((rev = request.getParameter("r")) != null && !rev.equals("")) {
                HistoryReader reader = null;
                try {
                    reader = HistoryGuru.getInstance().getHistoryReader(resourceFile);
                    if (reader == null) {
                        ret = resourceFile.lastModified();
                    } else {
                        while (reader.next()) {
                            if (reader.getRevision().equals(rev)) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(reader.getDate());
                                ret = cal.getTimeInMillis();
                            }
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                    } catch (IOException ex) {}
                }
            } else {
                ret = resourceFile.lastModified();
            }
        }
        
        return ret;
    }
    
    /**
     * Handle a GET request
     * @param request The object containing the request
     * @param response The object containing the response
     * @throws ServletException
     * @throws IOException
     */
    public void doGet(HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException {
        RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
        environment.register();
        String rawSource = environment.getSourceRootPath();
        
        String context = request.getContextPath();
        String reqURI = request.getRequestURI();
        String path = request.getPathInfo();
        if(path == null) {
            path = "";
        }
        String resourcePath = rawSource + path;
        File resourceFile = new File(resourcePath);
        resourcePath = resourceFile.getAbsolutePath();
        String basename = resourceFile.getName();
        if (resourcePath.length() < rawSource.length()
                || !resourcePath.startsWith(rawSource)
                || !resourceFile.canRead()
                || environment.getIgnoredNames().ignore(basename)) {
            response.sendError(404);
        } else if (resourceFile.isDirectory()) {
            if(!reqURI.endsWith("/")) {
                response.sendRedirect(context + "/xref" + path + "/");
            } else {
                response.sendRedirect(context + "/xref" + path);
            }
        } else {
            InputStream in = null;
            String rev;
            if ((rev = request.getParameter("r")) != null && !rev.equals("")) {
                try{
                    in = HistoryGuru.getInstance().getRevision(resourceFile.getParent(), basename, rev);
                } catch (Exception e) {
                    response.sendError(404, "Revision not found");
                }
            } else {
                in = new BufferedInputStream(new FileInputStream(resourceFile));
            }
            if (in != null) {
                try{
                    String contentType = null;
                    if ((contentType = AnalyzerGuru.getContentType(in, path)) != null) {
                        response.setContentType(contentType);
                    } else if (getServletContext().getMimeType(basename) != null) {
                        response.setContentType(getServletContext().getMimeType(basename));
                    }
                    int len = 0;
                    byte[] buf = new byte[8192];
                    OutputStream out = response.getOutputStream();
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                } catch (IOException e) {
                    response.sendError(404, "Not found");
                }
            }
        }
    }
}
