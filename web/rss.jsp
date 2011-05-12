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

Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

Portions Copyright 2011 Jens Elkner.

--%><%@page import="
java.io.File,
java.text.SimpleDateFormat,
java.util.Set,

org.opensolaris.opengrok.history.DirectoryHistoryReader,
org.opensolaris.opengrok.history.History,
org.opensolaris.opengrok.history.HistoryEntry,
org.opensolaris.opengrok.history.HistoryGuru,
org.opensolaris.opengrok.web.Util,
org.opensolaris.opengrok.web.Prefix,
org.opensolaris.opengrok.web.PageConfig"
%><%@ page session="false" errorPage="error.jsp"%><%@

include file="pageconfig.jspf"

%><%
/* ---------------------- rss.jsp start --------------------- */
{
	cfg = PageConfig.get(request);
	String redir = cfg.canProcess();
	if (redir == null || redir.length() > 0) {
		if (redir != null) {
			response.sendRedirect(redir);
		} else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
		return;
	}
	cfg.getEnv().setUrlPrefix(request.getContextPath() + Prefix.SEARCH_R + '?');
	String path = cfg.getPath();
	String dtag = cfg.getDefineTagsIndex();
	response.setContentType("text/xml");
%><?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="<%= request.getContextPath() 
	%>/rss.xsl.xml"?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
<channel>
	<title>Changes in <%= path.length() == 0 
		? "Cross Reference" 
		: Util.htmlize(cfg.getResourceFile().getName()) %></title>
	<link><%= Util.htmlize(request.getRequestURL()) %></link>
	<description><%= Util.htmlize(dtag) %></description>
	<language>en</language>
	<copyright>Copyright 2005</copyright>
	<generator>Java</generator><%
	History hist = null;
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
		<title><%= Util.htmlize(entry.getMessage()) %></title>
		<description><%
				if (cfg.isDir()) {
					Set<String> files = entry.getFiles();
					if (files != null) {
						for (String ifile : files) {
			%><%= Util.htmlize(ifile) %><%
						}
					}
				} else {
			%><%= Util.htmlize(path) %> - <%= 
				Util.htmlize(entry.getRevision()) %><%
				}
		%></description>
		<pubDate><%
				SimpleDateFormat df = 
					new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
				Util.htmlize(df.format(entry.getDate()));
		%></pubDate>
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
