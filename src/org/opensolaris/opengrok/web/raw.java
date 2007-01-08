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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)raw.java 1.3     06/02/22 SMI"
 */

package org.opensolaris.opengrok.web;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.index.IgnoredNames;
/**
 * Gets different versions of a file
 *
 * @author Chandan
 */
public class raw extends HttpServlet {
    protected long getLastModified(HttpServletRequest request) {
	String path = request.getPathInfo();
	if(path == null) {
            path = "";
        }
	String rawSource = getServletContext().getInitParameter("SRC_ROOT");
	String resourcePath = rawSource + path;
	File resourceFile = new File(resourcePath);
	resourcePath = resourceFile.getAbsolutePath();

        long ret;
        
	if (resourcePath.length() < rawSource.length()
	|| !resourcePath.startsWith(rawSource)
	|| !resourceFile.canRead()
	|| IgnoredNames.ignore(resourceFile)
	|| resourceFile.isDirectory()) {
	    ret = 0;
	} else {
	    ret = resourceFile.lastModified();
	}
        return ret;
    }
    
    public void doGet(HttpServletRequest request,
	HttpServletResponse response)
	throws IOException, ServletException {
	
	String context = request.getContextPath();
	String reqURI = request.getRequestURI();
	String path = request.getPathInfo();
	if(path == null) { 
            path = ""; 
        }
	String rawSource = getServletContext().getInitParameter("SRC_ROOT");
	String resourcePath = rawSource + path;
	File resourceFile = new File(resourcePath);
	resourcePath = resourceFile.getAbsolutePath();
	String basename = resourceFile.getName();
	if (resourcePath.length() < rawSource.length()
	|| !resourcePath.startsWith(rawSource)
	|| !resourceFile.canRead()
	|| IgnoredNames.ignore(basename)) {
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
