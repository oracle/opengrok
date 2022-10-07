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

Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2017-2018, 2020, Chris Fraire <cfraire@me.com>.

--%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="jakarta.servlet.http.HttpServletResponse"%>
<%@page session="false" errorPage="error.jsp" import="
org.apache.lucene.queryparser.classic.QueryParser,
org.opengrok.indexer.search.Results,
org.opengrok.web.api.v1.suggester.provider.service.SuggesterServiceFactory,
org.opengrok.indexer.web.QueryParameters,
org.opengrok.indexer.web.SearchHelper,
org.opengrok.indexer.web.SortOrder,
org.opengrok.indexer.web.Suggestion,

java.util.List"
%>
<%@ page import="jakarta.servlet.http.HttpServletRequest" %>
<%@ page import="jakarta.servlet.http.Cookie" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%
{
    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();
}
%><%@

include file="/projects.jspf"

%><%!
    private StringBuilder createUrl(HttpServletRequest request, SearchHelper sh, boolean menu) {
        StringBuilder url = new StringBuilder(64);
        QueryBuilder qb = sh.getBuilder();
        if (menu) {
            url.append("search?");
        } else {
            Util.appendQuery(url, QueryParameters.SORT_PARAM, sh.getOrder().toString());
        }
        if (qb != null) {
            Util.appendQuery(url, QueryParameters.FULL_SEARCH_PARAM, qb.getFreetext());
            Util.appendQuery(url, QueryParameters.DEFS_SEARCH_PARAM, qb.getDefs());
            Util.appendQuery(url, QueryParameters.REFS_SEARCH_PARAM, qb.getRefs());
            Util.appendQuery(url, QueryParameters.PATH_SEARCH_PARAM, qb.getPath());
            Util.appendQuery(url, QueryParameters.HIST_SEARCH_PARAM, qb.getHist());
            Util.appendQuery(url, QueryParameters.TYPE_SEARCH_PARAM, qb.getType());
        }
        if (sh.getProjects() != null && !sh.getProjects().isEmpty()) {
            if (Boolean.parseBoolean(request.getParameter(QueryParameters.ALL_PROJECT_SEARCH))) {
                Util.appendQuery(url, QueryParameters.ALL_PROJECT_SEARCH, Boolean.TRUE.toString());
            } else {
                Util.appendQuery(url, QueryParameters.PROJECT_SEARCH_PARAM,
                        PageConfig.get(request).getRequestedProjectsAsString());
            }
        }
        return url;
    }
%><%
/* ---------------------- search.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);

    SearchHelper searchHelper = cfg.prepareSearch();
    // N.b. searchHelper.destroy() is called via
    // WebappListener.requestDestroyed() on presence of the following
    // REQUEST_ATTR.
    request.setAttribute(SearchHelper.REQUEST_ATTR, searchHelper);
    searchHelper.prepareExec(cfg.getRequestedProjects()).executeQuery().prepareSummary();
    // notify suggester that query was searched
    SuggesterServiceFactory.getDefault().onSearch(cfg.getRequestedProjects(), searchHelper.getQuery());
    String redirect = searchHelper.getRedirect();
    if (redirect != null) {
        response.sendRedirect(redirect);
        return;
    }
    if (searchHelper.getErrorMsg() != null) {
        cfg.setTitle("Search Error");
        // Set status to Internal error. This should help to avoid caching
        // the page by some proxies.
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } else {
        cfg.setTitle(cfg.getSearchTitle());
    }
    response.addCookie(new Cookie("OpenGrokSorting",
            URLEncoder.encode(searchHelper.getOrder().toString(), StandardCharsets.UTF_8)));
}
%><%@

include file="/httpheader.jspf"

%><body>
<div id="page">
    <header id="whole_header">
        <%@include file="/pageheader.jspf" %>
        <div id="Masthead">
            <a href="<%= request.getContextPath() %>/"><span id="home"></span>Home</a>
            <%-- TODO: jel: IMHO it should be move to menu.jspf as combobox --%>
            <div id="sortfield">
                <label for="sortby">Sort by</label>
                <%
{
    SearchHelper searchHelper = (SearchHelper) request.getAttribute(SearchHelper.REQUEST_ATTR);
    StringBuilder url = createUrl(request, searchHelper, true).append("&amp;").
            append(QueryParameters.SORT_PARAM_EQ);
    int ordcnt = 0;
    for (SortOrder o : SortOrder.values()) {
        if (searchHelper.getOrder() == o) {
                    %><span class="active"><%= o.getDesc() %></span><%
        } else {
                    %><a href="<%= url %><%= o %>"><%= o.getDesc() %></a><%
        }
        ordcnt++;
        if (ordcnt != (SortOrder.values().length)) {
            %> | <%
        }
    }
}
                %>
            </div>
        </div>
        <div id="bar">
        </div>
        <div id="menu"><%@

include file="/menu.jspf"

%>
        </div>
    </header>

    <div id="results"> <%
{
    SearchHelper searchHelper = (SearchHelper) request.getAttribute(SearchHelper.REQUEST_ATTR);
    // TODO spellchecking cycle below is not that great and we only create
    // suggest links for every token in query, not for a query as whole
    String errorMsg = searchHelper.getErrorMsg();
    if (errorMsg != null) {
        %><h3>Error</h3><p class="pagetitle"><%
        if (searchHelper.getErrorMsg().startsWith((SearchHelper.PARSE_ERROR_MSG))) {
            %><%= Util.htmlize(SearchHelper.PARSE_ERROR_MSG) %>
            <br/>You might try to enclose your search term in quotes,
            <a href="help.jsp#escaping">escape special characters</a>
            with <code>\</code>, or read the <a href="help.jsp">Help</a>
            on the query language. Error message from parser:<br/>
            <%= Util.htmlize(errorMsg.substring(SearchHelper.PARSE_ERROR_MSG.length())) %><%
        } else {
            %><%= Util.htmlize(errorMsg) %><%
        }%></p><%
    } else if (searchHelper.getHits() == null) {
        %><p class="pagetitle">No hits</p><%
    } else if (searchHelper.getHits().length == 0) {
        List<Suggestion> hints = searchHelper.getSuggestions();
        for (Suggestion hint : hints) {
        %><p class="suggestions"><span style="color: #cc0000; ">Did you mean (for <%= hint.getName() %>)</span>:<%
	  if (hint.getFreetext() != null) {
	    for (String word : hint.getFreetext()) {
            %> <a href="search?<%= QueryParameters.FULL_SEARCH_PARAM_EQ %>
<%= Util.uriEncode(QueryParser.escape(word)) %>"><%=
                Util.htmlize(word) %></a> &nbsp; <%
	    }
	  }
	  if (hint.getRefs() != null)  {
	    for (String word : hint.getRefs()) {
            %> <a href="search?<%= QueryParameters.REFS_SEARCH_PARAM_EQ %>
<%= Util.uriEncode(QueryParser.escape(word)) %>"><%=
                Util.htmlize(word) %></a> &nbsp; <%
	    }
	  }
	  if (hint.getDefs() != null) {
	    for (String word : hint.getDefs()) {
            %> <a href="search?<%= QueryParameters.DEFS_SEARCH_PARAM_EQ %>
<%= Util.uriEncode(QueryParser.escape(word)) %>"><%=
                Util.htmlize(word) %></a> &nbsp; <%
            }
	  }
        %></p><%
        }
        %>
        <p class="pagetitle"> Your search <span class="bold"><%
            Util.htmlize(searchHelper.getQuery().toString(), out); %></span>
            did not match any files.
            <br/> Suggestions:<br/>
        </p>
        <ul>
            <li>Make sure all terms are spelled correctly.</li>
            <li>Try different keywords.</li>
            <li>Try more general keywords.</li>
            <li>Use 'wil*' cards if you are looking for partial match.</li>
        </ul>
	<%
    } else {
        int start = searchHelper.getStart();
        int max = searchHelper.getMaxItems();
        long totalHits = searchHelper.getTotalHits();
        long thispage = Math.min(totalHits - start, max);  // number of items to display on the current page
        // We have a lots of results to show: create a slider for
        String slider = Util.createSlider(start, max, totalHits, request);
        %>
        <p class="pagetitle">Searched <span class="bold"><%
            Util.htmlize(searchHelper.getQuery().toString(), out);
            %></span> (Results <span class="bold"> <%= start + 1 %> – <%= thispage + start
            %></span> of <span class="bold"><%= totalHits %></span>) sorted by <%=
            searchHelper.getOrder().getDesc() %></p><%
        if (slider.length() > 0) {
        %>
        <p class="slider"><%= slider %></p><%
        }
        %>
        <table aria-label="table of results"><%
        Results.prettyPrint(out, searchHelper, start, start + thispage);
        %>
        </table>
        <%
        if (slider.length() > 0) {
        %>
        <p class="slider"><%= slider %></p><%
        }
        %>
    </div><%
    }
    // Note that searchHelper.destroy() is called via WebappListener.requestDestroyed().
}
/* ---------------------- search.jsp end --------------------- */
%><%@

include file="/foot.jspf"

%>
