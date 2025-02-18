<%--
$Id$

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

Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.

--%><%@page import="
java.text.SimpleDateFormat,
java.util.Set,

org.opengrok.indexer.history.DirectoryHistoryReader,
org.opengrok.indexer.history.History,
org.opengrok.indexer.history.HistoryEntry,
org.opengrok.indexer.history.HistoryGuru,
org.opengrok.indexer.web.Util,
org.opengrok.indexer.web.Prefix,
org.opengrok.web.PageConfig"
%>
<%@ page import="jakarta.servlet.http.HttpServletResponse" %>
<%@ page session="false" errorPage="error.jsp"%><%
/* ---------------------- rss.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();

    String redir = cfg.canProcess();
    if (redir == null || !redir.isEmpty()) {
        if (redir != null) {
            response.sendRedirect(redir);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        return;
    }
    String path = cfg.getPath();
    String dtag = cfg.getDefineTagsIndex();
    response.setContentType("text/xml");
%><?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="<%= request.getContextPath()
    %>/rss.xsl.xml"?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
<channel>
    <title>Changes in <%= path.isEmpty()
        ? "Cross Reference"
        : Util.htmlize(cfg.getResourceFile().getName()) %></title>
    <description><%= Util.htmlize(dtag) %></description>
    <language>en</language>
    <copyright>Copyright 2025</copyright>
    <generator>Java</generator><%
    History hist;
    if(cfg.isDir()) {
        hist = new DirectoryHistoryReader(cfg.getHistoryDirs()).getHistory();
    } else {
        hist = HistoryGuru.getInstance().getHistory(cfg.getResourceFile());
    }
    if (hist != null) {
        int i = 20;
        for (HistoryEntry entry : hist.getHistoryEntries()) {
            if (i-- <= 0) {
                break;
            }
            if (entry.isActive()) {
    %>
    <item>
        <title><%
            /*
             * Newlines would result in HTML tags inside the 'title' which
             * causes the title to be displayed as 'null'. Print first line
             * of the message. The whole message will be printed in description.
             */
            String replaced = entry.getMessage().split("\n")[0];
        %><%= Util.htmlize(entry.getRevision()) %> - <%= Util.htmlize(replaced) %></title>
        <link><%
            String requestURL = request.getScheme() + "://";
            String serverName = cfg.getServerName();
            requestURL += serverName;
            String port = Integer.toString(request.getLocalPort());
            if (!port.isEmpty()) {
                requestURL += ":" + port;
            }

            requestURL += request.getContextPath();
            requestURL += Prefix.HIST_L + cfg.getPath() + "#" + entry.getRevision();
        %><%= Util.htmlize(requestURL) %></link>
        <description><%
            for (String e : entry.getMessage().split("\n")) {
            %>
            <%= Util.htmlize(e) %><%
            }
            %>

            List of files:
            <%
            if (cfg.isDir()) {
                Set<String> files = entry.getFiles();
                if (files != null) {
                    for (String ifile : files) {
            %>
            <%= Util.htmlize(ifile) %><%
                    }
                }
            } else {
            %><%= Util.htmlize(path) %><%
            }
        %>
        </description>
        <pubDate><%
            SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
        %><%= Util.htmlize(df.format(entry.getDate())) %></pubDate>
        <dc:creator><%= Util.htmlize(entry.getAuthor()) %></dc:creator>
    </item>
<%
            }
        }
    }
%>
</channel>
</rss>
<%
}
/* ---------------------- rss.jsp end --------------------- */
%>
