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

Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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
    cfg = PageConfig.get(request);
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
    cfg.setTitle("Cross Reference: " + path);

    String context = request.getContextPath();
    cfg.getEnv().setUrlPrefix(context + Prefix.SEARCH_R + "?");

    String uriEncodedPath = cfg.getUriEncodedPath();
    String rev = cfg.getRequestedRevision();
%>
<%@

include file="httpheader.jspf"

        %><body>
<script type="text/javascript">/* <![CDATA[ */
    document.rev = getParameter("r");
    document.annotate = <%= cfg.annotate() %>;
    document.domReady.push(domReadyMast);
    document.pageReady.push(pageReadyMast);
/* ]]> */</script>
<div id="page">
    <div id="whole_header">
        <form action="<%= context + Prefix.SEARCH_P %>">
<div id="header"><%@

include file="pageheader.jspf"

%>
    <div id="pagetitle"><span id="filename"
                    >Cross Reference: <%= cfg.getCrossFilename() %></span><%
    String dtag = cfg.getDefineTagsIndex();
    if (dtag.length() > 0) {
                    %><br/><%= dtag %><%
    }
    %></div>
</div>
<div id="Masthead">
    <kbd>
    <%
    JSONArray messages = new JSONArray();
    if (cfg.getProject() != null) {
        messages = Util.messagesToJson(RuntimeEnvironment.MESSAGES_MAIN_PAGE_TAG,
                            cfg.getProject().getDescription());
    }
    %>
    <% if (!messages.isEmpty()) { %>
    <span class="important-note">
    <% } %>
        <a href="<%= context + Prefix.XREF_P %>/">xref</a>: <%= Util
        .breadcrumbPath(context + Prefix.XREF_P, path,'/',"",true,cfg.isDir()) %>
    <% if (!messages.isEmpty()) { %>
    </span>
    <span class="important-note important-note-rounded"
          data-messages='<%= messages %>'>!</span>
    <% } %>
</kbd>
</div>
<div id="bar">
    <ul>
        <li><a href="<%= context %>/"><span id="home"></span>Home</a></li><%
    if (!cfg.hasHistory()) {
        %><li><span id="history"></span><span class="c">History</span></li><%
    } else {
        %><li><a href="<%= context + Prefix.HIST_L + uriEncodedPath
            %>"><span id="history"></span>History</a></li><%
    }
    if (!cfg.hasAnnotations() /* || cfg.getPrefix() == Prefix.HIST_S */ ) {
        %><li><span class="c"><span class="annotate"></span>Annotate</span></li><%
    } else if (cfg.annotate()) {
        %><li><span id="toggle-annotate-by-javascript" style="display: none"><a
            href="#" onclick="javascript:toggle_annotations(); return false;"
            title="Show or hide line annotation(commit revisions,authors)."
            ><span class="annotate"></span>Annotate</a></span><span
            id="toggle-annotate"><a href="#"><span class="annotate"></span>
            Annotate</a></span></li><%
    } else if (cfg.getPrefix() == Prefix.HIST_L) {
        %><li>
            <a href="#"
               onclick="javascript:
                           window.location = '<%= context + Prefix.XREF_P + uriEncodedPath %>'
                           // taken from get_annotations() from utils.js
                           + '?a=true'
                           + (document.rev ? '&amp;r=' + encodeURIComponent(document.rev) : '')
                           + (window.location.hash ? '&amp;h=' + window.location.hash.substring(1, window.location.hash.length) : '');
                           return false;">
                <span class="annotate"></span>
                Annotate
            </a>
        </li><%
    } else {
        %><li><a href="#" onclick="javascript:get_annotations(); return false;"
            ><span class="annotate"></span>Annotate</a></li><%
    }
    if (!cfg.isDir()) {
        if (cfg.getPrefix() == Prefix.XREF_P) {
        %><li><a href="#" onclick="javascript:lntoggle();return false;"
            title="<%= "Show or hide line numbers (might be slower if "
                + "file has more than 10 000 lines)."
            %>"><span id="line"></span>Line#</a></li><li><a
            href="#" onclick="javascript:lsttoggle();return false;"
            title="Show or hide symbol list."><%--
            --%><span id="defbox"></span>Navigate</a></li><%
        }
        %>
	<li><a href="<%= context + Prefix.RAW_P + uriEncodedPath
            + (rev.length() == 0 ? "" : "?r=" + Util.URIEncode(rev))
            %>"><span id="raw"></span>Raw</a></li>
	<li><a href="<%= context + Prefix.DOWNLOAD_P + uriEncodedPath
            + (rev.length() == 0 ? "" : "?r=" + Util.URIEncode(rev))
            %>"><span id="download"></span>Download</a></li>
	<%
    }
        %><li><input type="text" id="search" name="q" class="q" />
            <input type="submit" value="Search" class="submit" /></li><%
    Project proj = cfg.getProject();
    String[] vals = cfg.getSearchOnlyIn();
        %><li><input type="checkbox" name="path" value='"<%= vals[0]
            %>"' <%= vals[2] %>/> only in <b><%= vals[1] %></b></li>
    </ul><%
    if (proj != null) {
    %>
    <input type="hidden" name="project" value="<%=proj.getDescription()%>" /><%
    }
%>
    <input type="hidden" id="contextpath" value="<%=request.getContextPath()%>" />
</div>
<div id="scope"><span id="scope_content">&nbsp;</span></div>
        </form>
    </div>
<div id="content">
<%
}
/* ---------------------- mast.jsp end --------------------- */
%>
