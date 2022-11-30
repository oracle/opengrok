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
Portions Copyright (c) 2018-2020, Chris Fraire <cfraire@me.com>.
--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page errorPage="error.jsp" import="
java.io.IOException,
java.io.File,
java.text.Format,
java.text.SimpleDateFormat,
java.util.Date,
java.util.logging.Level,
java.util.logging.Logger,
java.util.Objects,
java.util.Set,
java.util.regex.Pattern,

org.opengrok.indexer.configuration.RuntimeEnvironment,
org.opengrok.indexer.history.History,
org.opengrok.indexer.history.HistoryEntry,
org.opengrok.indexer.history.HistoryGuru,
org.opengrok.indexer.logger.LoggerFactory,
org.opengrok.indexer.util.ForbiddenSymlinkException,
org.opengrok.indexer.web.QueryParameters,
org.opengrok.indexer.web.SearchHelper,
org.opengrok.indexer.web.Util"
%>
<%@ page import="jakarta.servlet.http.HttpServletResponse" %>
<%@ page import="org.opengrok.indexer.web.SortOrder" %>
<%/* ---------------------- history.jsp start --------------------- */
{
    final Logger LOGGER = LoggerFactory.getLogger(getClass());

    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();

    // Need to set the title before including httpheader.jspf
    cfg.setTitle(cfg.getHistoryTitle());

    String path = cfg.getPath();

    if (path.length() > 0) {
        String primePath = path;
        Project project = cfg.getProject();
        if (project != null) {
            SearchHelper searchHelper = cfg.prepareInternalSearch(SortOrder.RELEVANCY);
            /*
             * N.b. searchHelper.destroy() is called via
             * WebappListener.requestDestroyed() on presence of the following
             * REQUEST_ATTR.
             */
            request.setAttribute(SearchHelper.REQUEST_ATTR, searchHelper);
            searchHelper.prepareExec(project);

            try {
                primePath = searchHelper.getPrimeRelativePath(project.getName(), path);
            } catch (IOException | ForbiddenSymlinkException ex) {
                LOGGER.log(Level.WARNING, String.format(
                        "Error getting prime relative for %s", path), ex);
            }
        }

        File file = cfg.getResourceFile(primePath);
        History hist;
        try {
            hist = HistoryGuru.getInstance().getHistoryUI(file);
        } catch (Exception e) {
            // should not happen
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        }

        if (hist == null) {
            /*
             * The history is not available even for a renamed file.
             * Send 404 Not Found.
             */
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute(cfg.getHistoryAttrName(), hist);
    }
}
%>
<%@

include file="/httpheader.jspf"

%>
<%
{
    PageConfig cfg = PageConfig.get(request);
    if ((request.getAttribute(cfg.getHistoryAttrName())) != null) {
%>
<body>
<script type="text/javascript">/* <![CDATA[ */
    document.rev = function() { return getParameter("<%= QueryParameters.REVISION_PARAM %>"); };
    document.annotate = <%= PageConfig.get(request).annotate() %>;
    document.domReady.push(function() { domReadyMast(); });
    document.pageReady.push(function() { pageReadyMast(); });
/* ]]> */</script>
<div id="page">
    <header id="whole_header">
<%
    }
}
{
    if (request.getAttribute(PageConfig.get(request).getHistoryAttrName()) != null) {
%>
    <%@include file="/pageheader.jspf" %>
<%
    }
}
{
    PageConfig cfg = PageConfig.get(request);
    String context = request.getContextPath();
    String path = cfg.getPath();

    History hist;
    if ((hist = (History) request.getAttribute(cfg.getHistoryAttrName())) != null) {

        int startIndex = cfg.getStartIndex();
        int max = cfg.getMaxItems();
        long totalHits = hist.getHistoryEntries().size();
        long thisPageIndex = Math.min(totalHits - startIndex, max);

        // We have potentially a lots of results to show: create a slider for them
        request.setAttribute("history.jsp-slider", Util.createSlider(startIndex, max, totalHits, request));
%>
        <div id="Masthead">History log of 
        <%= Util.breadcrumbPath(context + Prefix.XREF_P, path,'/',"",true,cfg.isDir()) %>
        (Results <span class="bold"> <%= totalHits != 0 ? startIndex + 1 : 0 %> – <%= startIndex + thisPageIndex
            %></span> of <span class="bold"><%= totalHits %></span>)
        </div>
<%
    }
}
{
    if (request.getAttribute(PageConfig.get(request).getHistoryAttrName()) != null) {
%>
        <%@

include file="/minisearch.jspf"

%>
<%
    }
}
{
    PageConfig cfg = PageConfig.get(request);
    String context = request.getContextPath();
    String path = cfg.getPath();
    History hist;

    if ((hist = (History) request.getAttribute(cfg.getHistoryAttrName())) != null) {
        RuntimeEnvironment env = cfg.getEnv();
        String uriEncodedName = cfg.getUriEncodedPath();
        Project project = cfg.getProject();

        boolean striked = false;
        String userPage = env.getUserPage();
        String userPageSuffix = env.getUserPageSuffix();
        String bugPage = project != null ? project.getBugPage() : env.getBugPage();
        String bugRegex = project != null ? project.getBugPattern() : env.getBugPattern();
        Pattern bugPattern = null;
        if (bugRegex != null) {
            bugPattern = Pattern.compile(bugRegex);
        }
        String reviewPage = project != null ? project.getReviewPage() : env.getReviewPage();
        String reviewRegex = project != null ? project.getReviewPattern() : env.getReviewPattern();
        Pattern reviewPattern = null;
        if (reviewRegex != null) {
            reviewPattern = Pattern.compile(reviewRegex);
        }

        Format df = new SimpleDateFormat("dd-MMM-yyyy");

        int revision2Index = Math.max(cfg.getIntParam(QueryParameters.REVISION_2_PARAM, -1), 0);
        int revision1Index = cfg.getIntParam(QueryParameters.REVISION_1_PARAM, -1) < revision2Index ?
                revision2Index + 1 : cfg.getIntParam(QueryParameters.REVISION_1_PARAM, -1);
        revision2Index = revision2Index >= hist.getHistoryEntries().size() ? hist.getHistoryEntries().size() - 1 : revision2Index;

        int startIndex = cfg.getStartIndex();
        int maxItems = cfg.getMaxItems();
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
<table class="src" id="revisions" aria-label="table of revisions">
    <thead>
        <tr>
            <th>Revision <%
            if (hist.hasTags()) {
                %><a href="#" onclick="toggle_revtags(); return false;">
                    <span class="revtags-hidden">
                    (&lt;&lt;&lt; Hide revision tags)</span>
                    <span class="revtags">
                    (Show revision tags &gt;&gt;&gt;)</span></a><%
            }
            %></th><%
            if (!cfg.isDir()) {
            %>
            <th><input type="submit" value=" Compare "/>
            <% if (hist.getHistoryEntries().size() > revision1Index && revision1Index >= 0) { %>
                <input type="hidden" id="input_r1" name="<%= QueryParameters.REVISION_1_PARAM %>"
                value="<%= path + '@' + hist.getHistoryEntries().get(revision1Index).getRevision() %>"/>
            <% } %>
            <% if (hist.getHistoryEntries().size() > revision2Index && revision2Index >= 0) { %>
                <input type="hidden" id="input_r2" name="<%= QueryParameters.REVISION_2_PARAM %>"
                value="<%= path + '@' + hist.getHistoryEntries().get(revision2Index).getRevision() %>"/>
            <% } %>
            </th><%
            }
            %>
            <th>Date</th>
            <th>Author</th>
            <th>Comments <%
            if (hist.hasFileList()) {
                %><a href="#" onclick="toggle_filelist(); return false;">
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
            for (HistoryEntry entry : hist.getHistoryEntries(maxItems, startIndex)) {
                String rev = entry.getRevision();
                if (rev == null || rev.length() == 0) {
                    rev = "";
                }
                String tags = hist.getTags().get(rev);

                if (tags != null) {
                    int colspan;
                    if (cfg.isDir())
                        colspan = 4;
                    else
                        colspan = 5;
                    %>
        <tr class="revtags-hidden">
            <td colspan="<%= colspan %>" class="revtags">
                <span class="bold">Revision tags:</span> <%= tags %>
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
                        StringBuffer urlBuffer = request.getRequestURL();
                        if (request.getQueryString() != null) {
                            urlBuffer.append('?').append(request.getQueryString());
                        }
                        urlBuffer.append('#').append(rev);
            %>
            <td><a href="<%= urlBuffer %>"
                title="link to revision line">#</a>
                <a href="<%= context + Prefix.XREF_P + uriEncodedName + "?" +
                        QueryParameters.REVISION_PARAM_EQ + Util.uriEncode(rev) %>"><%= rev %>
                </a></td>
            <td><%
                %><input type="radio"
                        aria-label="From"
                        data-revision-1="<%= (startIndex + count) %>"
                        data-revision-2="<%= revision2Index %>"
                        data-diff-revision="<%= QueryParameters.REVISION_1_PARAM %>"
                        data-revision-path="<%= path + '@' + hist.getHistoryEntries().get(startIndex + count).getRevision()%>"
                <%
                if (count + startIndex > revision1Index || (count + startIndex > revision2Index && count + startIndex <= revision1Index - 1)) {
                    // revision1 enabled
                } else if (count + startIndex == revision1Index) {
                    // revision1 selected
                    %> checked="checked"<%
                } else if (count + startIndex <= revision2Index) {
                    // revision1 disabled
                    %> disabled="disabled" <%
                }
                %>/><%

                %><input type="radio"
                        aria-label="To"
                        data-revision-1="<%= revision1Index %>"
                        data-revision-2="<%= (startIndex + count) %>"
                        data-diff-revision="<%= QueryParameters.REVISION_2_PARAM %>"
                        data-revision-path="<%= path + '@' + hist.getHistoryEntries().get(startIndex + count).getRevision() %>"
                <%
                if (count + startIndex < revision2Index || (count + startIndex > revision2Index && count + startIndex <= revision1Index - 1)) {
                    // revision2 enabled
                } else if (count + startIndex == revision2Index) {
                    // revision2 selected
                    %> checked="checked" <%
                } else if (count + startIndex >= revision1Index) {
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
                %><%= Util.htmlize(author) %><%
                }
                %></td>
            <td><a name="<%= rev %>"></a><%
                // revision message collapse threshold minimum of 10
                int summaryLength = Math.max(10, cfg.getRevisionMessageCollapseThreshold());
                String cout = Util.htmlize(entry.getMessage());

                if (bugPage != null && bugPage.length() > 0 && bugPattern != null) {
                    cout = Util.linkifyPattern(cout, bugPattern, "$1", Util.completeUrl(bugPage + "$1", request));
                }
                if (reviewPage != null && reviewPage.length() > 0 && reviewPattern != null) {
                    cout = Util.linkifyPattern(cout, reviewPattern, "$1", Util.completeUrl(reviewPage + "$1", request));
                }
                
                boolean showSummary = false;
                String coutSummary = entry.getMessage();
                if (coutSummary.length() > summaryLength) {
                    showSummary = true;
                    coutSummary = coutSummary.substring(0, summaryLength - 1);
                    coutSummary = Util.htmlize(coutSummary);
                    if (bugPage != null && bugPage.length() > 0 && bugPattern != null) {
                        coutSummary = Util.linkifyPattern(coutSummary, bugPattern, "$1", Util.completeUrl(bugPage + "$1", request));
                    }
                    if (reviewPage != null && reviewPage.length() > 0 && reviewPattern != null) {
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
                        if (Objects.equals(rev, "")) {
                %>
<a class="h" href="<%= context + Prefix.XREF_P + ifile %>"><%= jfile %></a><br/><%
                        } else {
                %>
<a class="h" href="<%= context + Prefix.XREF_P + ifile %>?<%= QueryParameters.REVISION_PARAM_EQ %>
<%= rev %>"><%= jfile %></a><br/><%
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
    String slider;
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
%><p><span class="bold">Note:</span> No associated file changes are available for
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

include file="/foot.jspf"

%>
