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

--%>
<%@page import="org.opensolaris.opengrok.web.Util"%>
<%@page import="org.opensolaris.opengrok.history.HistoryGuru"%>
<%@page import="java.io.File"%>
<%@page errorPage="error.jsp" import="
java.text.Format,
java.text.SimpleDateFormat,
java.util.Date,
java.util.Set,
java.util.regex.Pattern,

org.opensolaris.opengrok.history.History,
org.opensolaris.opengrok.history.HistoryEntry,
org.opensolaris.opengrok.history.HistoryException,
org.opensolaris.opengrok.configuration.RuntimeEnvironment"
%>
<%/* ---------------------- history.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);

    cfg.checkSourceRootExistence();

    // Need to set the title before including httpheader.jspf
    cfg.setTitle(cfg.getHistoryTitle());

    String path = cfg.getPath();

    if (path.length() > 0) {
        File f = cfg.getResourceFile();
        History hist = null;
        try {
            hist = HistoryGuru.getInstance().getHistoryUI(f);
        } catch (Exception e) {
            // should not happen
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        }

        if (hist == null) {
            /**
             * The history is not available even for a renamed file.
             * Send 404 Not Found.
             */
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("history.jsp-hist", hist);
    }
}
%>
<%@

include file="httpheader.jspf"

%>
<%
{
    PageConfig cfg = PageConfig.get(request);
    if ((request.getAttribute("history.jsp-hist")) != null) {
%>
<body>
<script type="text/javascript">/* <![CDATA[ */
    document.rev = function() { return getParameter("r"); };
    document.annotate = <%= PageConfig.get(request).annotate() %>;
    document.domReady.push(function() { domReadyMast(); });
    document.pageReady.push(function() { pageReadyMast(); });
/* ]]> */</script>
<div id="page">
    <div id="whole_header">
        <div id="header">
<%
    }
}
{
    if (request.getAttribute("history.jsp-hist") != null) {
%><%@

include file="pageheader.jspf"

%>
<%
    }
}
{
    PageConfig cfg = PageConfig.get(request);
    String context = request.getContextPath();
    String path = cfg.getPath();

    History hist = null;
    if ((hist = (History) request.getAttribute("history.jsp-hist")) != null) {

        int start = cfg.getSearchStart();
        int max = cfg.getSearchMaxItems();
        int totalHits = hist.getHistoryEntries().size();
        int thispage = Math.min(totalHits - start, max);

        // We have a lots of results to show: create a slider for them
        request.setAttribute("history.jsp-slider", Util.createSlider(start, max, totalHits, request));
%>
        </div>
        <div id="Masthead">History log of 
        <%= Util.breadcrumbPath(context + Prefix.XREF_P, path,'/',"",true,cfg.isDir()) %>
        (Results <b> <%= start + 1 %> - <%= thispage + start
            %></b> of <b><%= totalHits %></b>)
        </div>
<%
    }
}
{
    if (request.getAttribute("history.jsp-hist") != null) {
%>
        <%@

include file="minisearch.jspf"

%>
<%
    }
}
{
    PageConfig cfg = PageConfig.get(request);
    String context = request.getContextPath();
    String path = cfg.getPath();
    History hist = null;
    if ((hist = (History) request.getAttribute("history.jsp-hist")) != null) {
        RuntimeEnvironment env = cfg.getEnv();
        String uriEncodedName = cfg.getUriEncodedPath();

        boolean striked = false;
        String userPage = env.getUserPage();
        String userPageSuffix = env.getUserPageSuffix();
        String bugPage = env.getBugPage();
        String bugRegex = env.getBugPattern();
        Pattern bugPattern = Pattern.compile(bugRegex);
        String reviewPage = env.getReviewPage();
        String reviewRegex = env.getReviewPattern();
        Pattern reviewPattern = Pattern.compile(reviewRegex);

        Format df = new SimpleDateFormat("dd-MMM-yyyy");

        int revision2 = cfg.getIntParam("r2", -1) < 0 ? 0 : cfg.getIntParam("r2", -1);
        int revision1 = cfg.getIntParam("r1", -1) < revision2 ? revision2 + 1 : cfg.getIntParam("r1", -1);
        revision2 = revision2 >= hist.getHistoryEntries().size() ? hist.getHistoryEntries().size() - 1 : revision2;

        int start = cfg.getSearchStart();
        int max = cfg.getSearchMaxItems();
%>
<script type="text/javascript">/* <![CDATA[ */
document.domReady.push(function() {domReadyHistory();});
/* ]]> */</script>
<!--[if IE]>
<style type="text/css">
  table#revisions tbody tr td p {
        word-break: break-all;
    }
</style>
<![endif]-->
<form action="<%= context + Prefix.DIFF_P + uriEncodedName %>">
<table class="src" id="revisions">
    <thead>
        <tr>
            <th>Revision <%
            if (hist.hasTags()) {
                %><a href="#" onclick="javascript: toggle_revtags(); return false;">
                    <span class="revtags-hidden">
                    (&lt;&lt;&lt; Hide revision tags)</span>
                    <span class="revtags">
                    (Show revision tags &gt;&gt;&gt;)</span></a><%
            }
            %></th><%
            if (!cfg.isDir()) {
            %>
            <th><input type="submit" value=" Compare "/>
            <% if (hist.getHistoryEntries().size() > revision1 && revision1 >= 0) { %>
                <input type="hidden" id="input_r1" name="r1" value="<%= path + '@' + hist.getHistoryEntries().get(revision1).getRevision() %>" />
            <% } %>
            <% if (hist.getHistoryEntries().size() > revision2 && revision2 >= 0) { %>
                <input type="hidden" id="input_r2" name="r2" value="<%= path + '@' + hist.getHistoryEntries().get(revision2).getRevision() %>" />
            <% } %>
            </th><%
            }
            %>
            <th>Date</th>
            <th>Author</th>
            <th>Comments <%
            if (hist.hasFileList()) {
                %><a href="#" onclick="javascript: toggle_filelist(); return false;">
                    <div class="filelist-hidden">
                    (&lt;&lt;&lt; Hide modified files)</div>
                    <div class="filelist">
                    (Show modified files &gt;&gt;&gt;)</div></a><%
            }
            %>
            </th>
        </tr>
    </thead>
    <tbody>
            <%
            int count=0;
            for (HistoryEntry entry : hist.getHistoryEntries(max, start)) {
                String rev = entry.getRevision();
                if (rev == null || rev.length() == 0) {
                    rev = "";
                }
                String tags = entry.getTags();

                if (tags != null) {
			int colspan;
			if (cfg.isDir())
				colspan = 4;
			else
				colspan = 5;
                    %>
        <tr class="revtags-hidden">
            <td colspan="<%= colspan %>" class="revtags">
                <b>Revision tags:</b> <%= tags %>
            </td>
        </tr><tr style="display: none;"></tr><%
                }
    %>
        <tr><%
                if (cfg.isDir()) {
            %>
            <td><%= rev %></td><%
                } else {
                    if (entry.isActive()) {
                        String rp = uriEncodedName;
                        StringBuffer urlBuffer = request.getRequestURL();
                        if (request.getQueryString() != null) {
                            urlBuffer.append('?').append(request.getQueryString());
                        }
                        urlBuffer.append('#').append(rev);
            %>
            <td><a href="<%= urlBuffer %>"
                title="link to revision line">#</a>
                <a href="<%= context + Prefix.XREF_P + rp + "?r=" + Util.URIEncode(rev) %>"><%=
                    rev %></a></td>
            <td><%
                %><input type="radio"
                        data-revision-1="<%= (start + count) %>"
                        data-revision-2="<%= revision2 %>"
                        data-diff-revision="r1"
                        data-revision-path="<%= path + '@' + hist.getHistoryEntries().get(start + count).getRevision()%>"
                <%
                if (count + start > revision1 || (count + start > revision2 && count + start <= revision1 - 1)) {
                    // revision1 enabled
                } else if (count + start == revision1 ) {
                    // revision1 selected
                    %> checked="checked"<%
                } else if( count + start <= revision2 ) {
                    // revision1 disabled
                    %> disabled="disabled" <%
                }
                %>/><%

                %><input type="radio"
                        data-revision-1="<%= revision1 %>"
                        data-revision-2="<%= (start + count) %>"
                        data-diff-revision="r2"
                        data-revision-path="<%= path + '@' + hist.getHistoryEntries().get(start + count).getRevision() %>"
                <%
                if( count + start < revision2 || (count + start > revision2 && count + start <= revision1 - 1) ) {
                    // revision2 enabled
                } else if( count + start == revision2 ) {
                    // revision2 selected
                    %> checked="checked" <%
                } else if (count + start >= revision1 ) {
                    // revision2 disabled
                    %> disabled="disabled" <%
                }
                %>/><%
                %></td><%
                    } else {
                        striked = true;
                %>
            <td><del><%= rev %></del></td>
            <td></td><%
                    }
                }
            %>
            <td><%
                Date date = entry.getDate();
                if (date != null) {
            %><%= df.format(date) %><%
                }
                %></td>
            <td><%
                String author = entry.getAuthor();
                if (author == null) {
                %>(no author)<%
                } else if (userPage != null && userPage.length() > 0) {
		String alink = Util.getEmail(author);
                %><a href="<%= userPage + Util.htmlize(alink) + userPageSuffix
                %>"><%= Util.htmlize(author)%></a><%
                } else {
                %><%= author %><%
                }
                %></td>
            <td><a name="<%= rev %>"></a><%
                // revision message collapse threshold minimum of 10
                int summaryLength = Math.max(10, cfg.getRevisionMessageCollapseThreshold());
                String cout = Util.htmlize(entry.getMessage());

                if (bugPage != null && bugPage.length() > 0) {
                    cout = Util.linkifyPattern(cout, bugPattern, "$1", Util.completeUrl(bugPage + "$1", request));
                }
                if (reviewPage != null && reviewPage.length() > 0) {
                    cout = Util.linkifyPattern(cout, reviewPattern, "$1", Util.completeUrl(reviewPage + "$1", request));
                }
                
                boolean showSummary = false;
                String coutSummary = entry.getMessage();
                if (coutSummary.length() > summaryLength) {
                    showSummary = true;
                    coutSummary = coutSummary.substring(0, summaryLength - 1);
                    coutSummary = Util.htmlize(coutSummary);
                    if (bugPage != null && bugPage.length() > 0) {
                        coutSummary = Util.linkifyPattern(coutSummary, bugPattern, "$1", Util.completeUrl(bugPage + "$1", request));
                    }
                    if (reviewPage != null && reviewPage.length() > 0) {
                        coutSummary = Util.linkifyPattern(coutSummary, reviewPattern, "$1", Util.completeUrl(reviewPage + "$1", request));
                    }
                }

                if (showSummary) {
                    %>
                    <p class="rev-message-summary"><%= coutSummary %></p>
                    <p class="rev-message-full rev-message-hidden"><%= cout %></p>
                    <p class="rev-message-toggle" data-toggle-state="less"><a class="rev-toggle-a" href="#">show more ... </a></p>
                    <%
                }
                else {
                     %><p class="rev-message-full"><%= cout %></p><%
                }

                Set<String> files = entry.getFiles();
                if (files != null) {
                %><div class="filelist-hidden"><br/><%
                    for (String ifile : files) {
                        String jfile = Util.stripPathPrefix(path, ifile);
                        if (rev == "") {
                %>
<a class="h" href="<%= context + Prefix.XREF_P + ifile %>"><%= jfile %></a><br/><%
                        } else {
                %>
<a class="h" href="<%= context + Prefix.XREF_P + ifile %>?r=<%= rev %>"><%= jfile %></a><br/><%
                        }
                    }
                %></div><%
                }
                %></td>
        </tr><%
                count++;
            }
        %>
    </tbody>
    <tfoot>
        <tr>
            <td colspan="5">
<%
    String slider = null;
    if ((slider = (String) request.getAttribute("history.jsp-slider")) != null) {
        // NOTE: shouldn't happen that it doesn't have this attribute
        %><p class="slider"><%= slider %></p><%
    }
%>
            </td>
        </tr>
    </tfoot>
</table>

</form><%
            if (striked) {
%><p><b>Note:</b> No associated file changes are available for
revisions with strike-through numbers (eg. <del>1.45</del>)</p><%
            }
%>
<p class="rssbadge"><a href="<%=context + Prefix.RSS_P + uriEncodedName
%>" title="RSS XML Feed of latest changes"><span id="rssi"></span></a></p>

<%

    }
}
/* ---------------------- history.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>
