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

Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.

--%><%--

After include you are here: /body/div#page/div#content/

--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="org.opengrok.indexer.web.messages.MessagesContainer"%>
<%@ page session="false" errorPage="error.jsp" import="
org.opengrok.web.PageConfig,
org.opengrok.indexer.web.Prefix,
org.opengrok.indexer.web.Util"%>
<%@ page import="org.opengrok.indexer.web.messages.MessagesUtils" %>
<%@ page import="jakarta.servlet.http.HttpServletResponse" %>
<%
/* ---------------------- mast.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    if (cfg.isUnreadable()) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
    }

    String redir = cfg.canProcess();
    if (redir == null || redir.length() > 0) {
        if (redir == null) {            
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            response.sendRedirect(redir);
        }
        return;
    }

    if (cfg.isNotModified(request, response)) {
        // the resource was not modified
        // the code 304 NOT MODIFIED has been inserted to the response
        return;
    }

    // Use UTF-8 if no encoding is specified in the request
    if (request.getCharacterEncoding() == null) {
        request.setCharacterEncoding("UTF-8");
    }

    // set the default page title
    String path = cfg.getPath();
    cfg.setTitle(cfg.getPathTitle());
}
%>
<%@

include file="/httpheader.jspf"

        %><body>
<script type="text/javascript">/* <![CDATA[ */
    document.rev = function() { return getParameter("r"); };
    document.annotate = <%= PageConfig.get(request).annotate() %>;
    document.domReady.push(function() { domReadyMast(); });
    document.pageReady.push(function() { pageReadyMast(); });
/* ]]> */</script>
<div id="page">
    <header id="whole_header">
        <%@include file="/pageheader.jspf" %>
<div id="Masthead">
    <%
{
    PageConfig cfg = PageConfig.get(request);
    String path = cfg.getPath();
    String context = request.getContextPath();
    String rev = cfg.getRequestedRevision();

    String messages = "";
    if (cfg.getProject() != null) {
        messages = MessagesUtils.messagesToJson(cfg.getProject(),
                    MessagesContainer.MESSAGES_MAIN_PAGE_TAG);
    }
    %>
    <a href="<%= context + Prefix.XREF_P %>/">xref</a>:
    <%= Util.breadcrumbPath(context + Prefix.XREF_P, path,'/',"",true,cfg.isDir()) %>
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
    <span class="note-<%= MessagesUtils.getMessageLevel(cfg.getProject().getName(), MessagesContainer.MESSAGES_MAIN_PAGE_TAG) %> important-note important-note-rounded"
          data-messages='<%= messages %>'>!</span>
    <% }
}
%>
</div>
<%@

include file="/minisearch.jspf"

%>
<%
/* ---------------------- mast.jsp end --------------------- */
%>
