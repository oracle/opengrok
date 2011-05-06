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

ident	"@(#)rss.jsp 1.2     05/12/02 SMI"

--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.history.*,
org.opensolaris.opengrok.index.IgnoredNames,
org.opensolaris.opengrok.configuration.*,
org.apache.lucene.analysis.*,
org.apache.lucene.document.*,
org.apache.lucene.index.*,
org.apache.lucene.search.*,
org.apache.lucene.queryParser.*,
java.text.*"
%><%@ page session="false" %><%@ page errorPage="error.jsp"%><%
String context = request.getContextPath();
String servlet = request.getServletPath();
String reqURI = request.getRequestURI();
String path = request.getPathInfo();
if(path == null) path = "";
RuntimeEnvironment env = RuntimeEnvironment.getInstance();
env.setUrlPrefix(context + Constants.searchR+"?");
env.register();
String rawSource = env.getSourceRootPath();
String resourcePath = rawSource + path;
File resourceFile = new File(resourcePath);
resourcePath = resourceFile.getAbsolutePath();
boolean valid;
String basename = resourceFile.getName();
if("/".equals(path)) {
    basename = "Cross Reference";
}
boolean isDir = false;
String parent = null;
String parentBasename = resourceFile.getParentFile().getName();
IgnoredNames ignoredNames = env.getIgnoredNames();
if (resourcePath.length() < rawSource.length()
|| !resourcePath.startsWith(rawSource)
|| !resourceFile.canRead()
|| ignoredNames.ignore(basename) || ignoredNames.ignore(parentBasename)) {
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
    if (isDir && !reqURI.endsWith("/")) {
        response.sendRedirect(context + servlet + path +"/");
    } else {
        String dtag = "";
        
        try {
            EftarFileReader ef = new EftarFileReader(env.getDataRootPath() + "/index/dtags.eftar");
            dtag = ef.get(path);
	    ef.close();
        } catch (Exception e) {
            dtag = "";
        }
        int lastSlash = path.lastIndexOf('/');
        parent = (lastSlash != -1) ? path.substring(0, lastSlash) : "";
        int pLastSlash = parent.lastIndexOf('/');
        parentBasename = pLastSlash != -1 ? parent.substring(pLastSlash+1) : parent;
        response.setContentType("text/xml");
        Date start = new Date();
%><?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="<%=context%>/rss.xsl.xml"?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
<channel>
    <title>Changes in <%=Util.htmlize(basename)%></title>
    <link><%=Util.htmlize(request.getRequestURL())%></link>
    <description><%=Util.htmlize(dtag)%></description>
    <language>en</language>
    <copyright>Copyright 2005</copyright>
    <generator>Java</generator>
    <%
    Format df = new SimpleDateFormat("dd-MMM-yyyy");
    History hist = null;
    if(isDir) {
        String[] apaths = request.getParameterValues("also");
        String apath = path;
        if (apaths!= null && apaths.length>0) {
            StringBuilder paths = new StringBuilder(path);
            for(int i=0; i< apaths.length; i++) {
                paths.append(' ');
                paths.append(apaths[i]);
            }
            apath = paths.toString();
        }
        hist = new DirectoryHistoryReader(apath).getHistory();
    } else {
        File f = new File(rawSource + parent, basename);
        hist = HistoryGuru.getInstance().getHistory(f);
    }
    if (hist != null) {
        int i = 20;
        for (HistoryEntry entry : hist.getHistoryEntries()) {
            if (i-- <= 0) break;
            String rev = entry.getRevision();
            if (entry.isActive()) {
%>
<item>
    <title><%=Util.htmlize(entry.getMessage())%></title>
    <description><%
    if(isDir) {
        Set<String> files = entry.getFiles();
        if(files != null) {
            for (String ifile : files) {
    %><%=Util.htmlize(ifile)%>
<%
            }
        }
    } else {
    %><%=Util.htmlize(path)%> - <%=Util.htmlize(entry.getRevision())%><%
    }
    %></description>
    <pubDate><%=Util.htmlize(entry.getDate().toString())%></pubDate>
    <dc:creator><%=Util.htmlize(entry.getAuthor())%></dc:creator>
</item>
<%
            }
        }
    }
%></channel></rss>
<%
    }
}
%>
