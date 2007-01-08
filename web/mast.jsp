<%-- 
CDDL HEADER START

The contents of this file are subject to the terms of the
Common Development and Distribution License (the "License").  
You may not use this file except in compliance with the License.

See LICENSE.txt included in this distribution for the specific
language governing permissions and limitations under the License.

When distributing Covered Code, include this CDDL HEADER in each
file and include the License file at LICENSE.txt.
If applicable, add the following below this CDDL HEADER, with the
fields enclosed by brackets "[]" replaced with your own identifying
information: Portions Copyright [yyyy] [name of copyright owner]

CDDL HEADER END

Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

ident	"%Z%%M% %I%     %E% SMI"

--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
java.text.*,
org.opensolaris.opengrok.index.*
"
%><%@ page session="false" %><%@ page errorPage="error.jsp"%><%
String context = request.getContextPath();
String servlet = request.getServletPath();
String reqURI = request.getRequestURI();
String path = request.getPathInfo();
if (path == null) path = "";
String rawSource = getServletContext().getInitParameter("SRC_ROOT");
String resourcePath = rawSource + path;
File resourceFile = new File(resourcePath);
resourcePath = resourceFile.getAbsolutePath();
boolean valid;
boolean noHistory = true;
String basename = resourceFile.getName();
if("/".equals(path)) {
    basename = "Cross Reference";
}
boolean isDir = false;
EftarFileReader ef = null;
String parent = null;
String parentBasename = resourceFile.getParentFile().getName();
if(resourcePath.length() < rawSource.length()
|| IgnoredNames.ignore(basename)
|| IgnoredNames.ignore(parentBasename)
|| !resourcePath.startsWith(rawSource)) {
    valid = false;
    response.sendError(404);
    return;
} else if (!resourceFile.canRead() && resourcePath.startsWith(rawSource)) {
    String newPath = rawSource + "/on/" + path;
    File newFile = new File(newPath);
    if(newFile.canRead()) {
        if(newFile.isDirectory() && servlet.startsWith("/xref") && !path.endsWith("/")) {
            response.sendRedirect(context + servlet + "/on" + path + "/");
        } else {
            response.sendRedirect(context + servlet + "/on" + path);
        }
    }
    valid = false;
    response.sendError(404);
    return;
} else {
    valid = true;
    path = resourcePath.substring(rawSource.length());
    if (File.separatorChar == '\\') {
        path = path.replace('\\','/');
    }
    isDir = resourceFile.isDirectory();
    if (isDir && !servlet.startsWith("/xref") && !servlet.startsWith("/hist")) {	//if it is an existing directory perhaps people wanted directory xref
        if(!reqURI.endsWith("/")) {
            response.sendRedirect(context + "/xref" + path + "/");
        } else {
            response.sendRedirect(context + "/xref" + path);
        }
    } if (isDir && !reqURI.endsWith("/")) {
        response.sendRedirect(context + servlet + path +"/");
    } else {
        
        long flast = resourceFile.lastModified();
        String dtag = "";
        
        if (request.getDateHeader("If-Modified-Since") >= flast ) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            valid = false;
        } else {
            
            response.setDateHeader("Last-Modified", flast);
            int lastSlash = path.lastIndexOf('/');
            parent = (lastSlash != -1) ? path.substring(0, lastSlash) : "";
            int pLastSlash = parent.lastIndexOf('/');
            parentBasename = pLastSlash != -1 ? parent.substring(pLastSlash+1) : parent;
            noHistory = !(isDir || HistoryGuru.getInstance().hasHistory(rawSource + "/" + parent));
            try{
                ef = new EftarFileReader(getServletContext().getInitParameter("DATA_ROOT") + "/index/dtags.eftar");
                dtag = ef.get(path);
                if(servlet.startsWith("/xr")) {
                } else {
                    if(ef != null) {
                        try {
                            ef.close();
                        } catch (IOException e) {
                        } finally {
                            ef = null;
                        }
                    }
                }
            } catch (Exception e) {
                dtag = "";
            }
%><?xml version="1.0" encoding="iso-8859-1"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en-US" lang="en-US">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
    <title><%=basename%></title>
    <link rel="icon" href="<%=context%>/img/icon.png" type="image/png"/>
    <link rel="stylesheet" type="text/css" href="<%=context%>/style.css"/>
    <link rel="stylesheet" type="text/css" href="<%=context%>/print.css" media="print" />
    <link rel="alternate stylesheet" type="text/css" media="all" title="Paper White" href="<%=context%>/print.css"/>
</head>
<body><div id="page">
<form action="<%=context%>/search">
    <div id="header">
        <%= getServletContext().getInitParameter("HEADER") %>
        <div id="pagetitle"><b id="filename"><%=basename%><%= isDir ? "/" : "" %></b><br/><%=dtag%></div>
    </div>
<div id="Masthead"><tt><a href="<%=context%>/xref/">xref</a>: <%=org.opensolaris.opengrok.web.Util.breadcrumbPath(context + "/xref", path)%><%= isDir ? "/" : "" %></tt></div>    
<div id="bar"><a href="<%=context%>" id="home">Home</a> | 
<%

if ((!isDir && noHistory) || servlet.startsWith("/hi")) {
	%> <span class="c" id="history">History</span> |<%
} else {
	%><a id="history" href="<%=context%>/history<%=path%>">History</a> |<%
}
        if (!isDir) {
        %> <a id="download" href="<%=context%>/raw<%=path%>">Download</a> | <%
        }

%> <input id="search" name="q" class="q"/>
<input type="submit" value="Search" class="submit"/><%

if(isDir) {
                if(path.length() > 0) {
	%><input type="checkbox" name="path" value="<%=path%>"/> only in <b><%=basename%></b><%
  }
} else {
	%><input type="checkbox" name="path" value="<%=parent%>"/> only in <b><%=parentBasename%></b><%
}

%></div></form><%

} // date check
    } // not a directory redirect
}
%>
