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

Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.

--%><%--

After include you are here: /body/div#page/div#content/

--%>
<%@page import="org.json.simple.JSONArray"%>
<%@page import="org.opensolaris.opengrok.configuration.messages.Message"%>
<%@page import="java.util.SortedSet"%>
<%@page import="org.opensolaris.opengrok.configuration.RuntimeEnvironment"%>
<%@ page session="false" errorPage="error.jsp" import="
java.io.File,
java.io.IOException,

org.opensolaris.opengrok.configuration.Project,
org.opensolaris.opengrok.history.HistoryGuru,
org.opensolaris.opengrok.web.EftarFileReader,
org.opensolaris.opengrok.web.PageConfig,
org.opensolaris.opengrok.web.Prefix,
org.opensolaris.opengrok.web.Util"%><%
/* ---------------------- mast.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    String redir = cfg.canProcess();
    if (redir == null || redir.length() > 0) {
        if (redir == null) {            
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            response.sendRedirect(redir);
        }
        return;
    }

    // jel: hmmm - questionable for dynamic content
    long flast = cfg.getLastModified();
    if (request.getDateHeader("If-Modified-Since") >= flast) {
        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        return;
    }
    response.setDateHeader("Last-Modified", flast);

    // Use UTF-8 if no encoding is specified in the request
    if (request.getCharacterEncoding() == null) {
        request.setCharacterEncoding("UTF-8");
    }

    // set the default page title
    String path = cfg.getPath();
    cfg.setTitle(cfg.getPathTitle());

    String context = request.getContextPath();
    cfg.getEnv().setUrlPrefix(context + Prefix.SEARCH_R + "?");
}
%>
<%@

include file="httpheader.jspf"

        %><body>
<script type="text/javascript">/* <![CDATA[ */
    document.rev = function() { return getParameter("r"); };
    document.annotate = <%= PageConfig.get(request).annotate() %>;
    document.domReady.push(function() { domReadyMast(); });
    document.pageReady.push(function() { pageReadyMast(); });
/* ]]> */</script>
<div id="page">
    <div id="whole_header">
<div id="header"><%@

include file="pageheader.jspf"

%>
</div>
<div id="Masthead">
    <%
{
    PageConfig cfg = PageConfig.get(request);
    String path = cfg.getPath();
    String context = request.getContextPath();
    String rev = cfg.getRequestedRevision();

    JSONArray messages = new JSONArray();
    if (cfg.getProject() != null) {
        messages = Util.messagesToJson(cfg.getProject(),
                    RuntimeEnvironment.MESSAGES_MAIN_PAGE_TAG);
    }
    %>
    <% if (!messages.isEmpty()) { %>
    <span class="important-note">
    <% } %>
        <a href="<%= context + Prefix.XREF_P %>/">xref</a>: <%= Util
        .breadcrumbPath(context + Prefix.XREF_P, path,'/',"",true,cfg.isDir()) %>
        <% if (rev.length() != 0) { %>
        (revision <%= Util.htmlize(rev) %>)
        <% } %>
    <span id="dtag">
    <%
    String dtag = cfg.getDefineTagsIndex();
    if (dtag.length() > 0) {
        %> (<%= dtag %>)<%
    }
    %></span>
    <% if (!messages.isEmpty()) { %>
    </span>
    <span class="important-note important-note-rounded"
          data-messages='<%= messages %>'>!</span>
    <% }
}
%>
</div>
<%@

include file="minisearch.jspf"

%>
<%
/* ---------------------- mast.jsp end --------------------- */
%>
