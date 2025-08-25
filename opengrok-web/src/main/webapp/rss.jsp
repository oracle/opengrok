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

    String redirectLocation = cfg.canProcess();
    if (redirectLocation == null || !redirectLocation.isEmpty()) {
        if (redirectLocation != null) {
            response.sendRedirect(redirectLocation);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        return;
    }
    String path = cfg.getPath();
    response.setContentType("text/xml");
%><?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="<%= request.getContextPath()
    %>/rss.xsl.xml"?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
<channel>
    <title>Changes in <%= path.isEmpty()
        ? "Cross Reference"
        : Util.htmlize(cfg.getResourceFile().getName()) %></title>
    <description><%= Util.htmlize(cfg.getDefineTagsIndex()) %></description>
    <language>en</language>
    <copyright>Copyright 2025</copyright>
    <generator>Java</generator><%
    History history;
    if(cfg.isDir()) {
        history = new DirectoryHistoryReader(cfg.getHistoryDirs()).getHistory();
    } else {
        history = HistoryGuru.getInstance().getHistory(cfg.getResourceFile());
    }
    if (history != null) {
        int i = 20;
        for (HistoryEntry entry : history.getHistoryEntries()) {
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
            String requestURL = request.getScheme() +
                    "://" +
                    cfg.getServerName() +
                    ":" +
                    request.getLocalPort() +
                    Util.uriEncodePath(request.getContextPath()) +
                    Prefix.HIST_L +
                    Util.uriEncodePath(cfg.getPath()) +
                    "#" +
                    Util.uriEncode(entry.getRevision());
        %><%= requestURL %></link>
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
                    for (String entryFile : files) {
            %>
            <%= Util.htmlize(entryFile) %><%
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
