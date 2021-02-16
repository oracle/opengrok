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

Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
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
<%
{
    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();
}
%><%@

include file="projects.jspf"

%><%!
    private StringBuilder createUrl(HttpServletRequest request, SearchHelper sh, boolean menu) {
        StringBuilder url = new StringBuilder(64);
        QueryBuilder qb = sh.builder;
        if (menu) {
            url.append("search?");
        } else {
            Util.appendQuery(url, QueryParameters.SORT_PARAM, sh.order.toString());
        }
        if (qb != null) {
            Util.appendQuery(url, QueryParameters.FULL_SEARCH_PARAM, qb.getFreetext());
            Util.appendQuery(url, QueryParameters.DEFS_SEARCH_PARAM, qb.getDefs());
            Util.appendQuery(url, QueryParameters.REFS_SEARCH_PARAM, qb.getRefs());
            Util.appendQuery(url, QueryParameters.PATH_SEARCH_PARAM, qb.getPath());
            Util.appendQuery(url, QueryParameters.HIST_SEARCH_PARAM, qb.getHist());
            Util.appendQuery(url, QueryParameters.TYPE_SEARCH_PARAM, qb.getType());
        }
        if (sh.projects != null && sh.projects.size() != 0) {
            Util.appendQuery(url, QueryParameters.PROJECT_SEARCH_PARAM,
                    PageConfig.get(request).getRequestedProjectsAsString());
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
    SuggesterServiceFactory.getDefault().onSearch(cfg.getRequestedProjects(), searchHelper.query);
    if (searchHelper.redirect != null) {
        response.sendRedirect(searchHelper.redirect);
        return;
    }
    if (searchHelper.errorMsg != null) {
        cfg.setTitle("Search Error");
        // Set status to Internal error. This should help to avoid caching
        // the page by some proxies.
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } else {
        cfg.setTitle(cfg.getSearchTitle());
    }
    response.addCookie(new Cookie("OpenGrokSorting", URLEncoder.encode(searchHelper.order.toString(), "utf-8")));
}
%><%@

include file="httpheader.jspf"

%><body>
<div id="page">
    <div id="whole_header">
        <div id="header"><%@

include file="pageheader.jspf"

%>
        </div>
        <div id="Masthead">
            <a href="<%= request.getContextPath() %>/"><span id="home"></span>Home</a>
            <%-- TODO: jel: IMHO it should be move to menu.jspf as combobox --%>
            <div id="sortfield">
                <label for="sortby">Sort by</label>
                <%
{
    PageConfig cfg = PageConfig.get(request);
    SearchHelper searchHelper = (SearchHelper) request.getAttribute(SearchHelper.REQUEST_ATTR);
    StringBuilder url = createUrl(request, searchHelper, true).append("&amp;").
            append(QueryParameters.SORT_PARAM_EQ);
    int ordcnt = 0;
    for (SortOrder o : SortOrder.values()) {
        if (searchHelper.order == o) {
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

include file="menu.jspf"

%>
        </div>
    </div>

    <div id="results"> <%
{
    PageConfig cfg = PageConfig.get(request);
    SearchHelper searchHelper = (SearchHelper) request.getAttribute(SearchHelper.REQUEST_ATTR);
    // TODO spellchecking cycle below is not that great and we only create
    // suggest links for every token in query, not for a query as whole
    if (searchHelper.errorMsg != null) {
        %><h3>Error</h3><p class="pagetitle"><%
        if (searchHelper.errorMsg.startsWith((SearchHelper.PARSE_ERROR_MSG))) {
            %><%= Util.htmlize(SearchHelper.PARSE_ERROR_MSG) %>
            <br/>You might try to enclose your search term in quotes,
            <a href="help.jsp#escaping">escape special characters</a>
            with <b>\</b>, or read the <a href="help.jsp">Help</a>
            on the query language. Error message from parser:<br/>
            <%= Util.htmlize(searchHelper.errorMsg.substring(
                        SearchHelper.PARSE_ERROR_MSG.length())) %><%
        } else {
            %><%= Util.htmlize(searchHelper.errorMsg) %><%
        }%></p><%
    } else if (searchHelper.hits == null) {
        %><p class="pagetitle">No hits</p><%
    } else if (searchHelper.hits.length == 0) {
        List<Suggestion> hints = searchHelper.getSuggestions();
        for (Suggestion hint : hints) {
        %><p class="suggestions"><font color="#cc0000">Did you mean (for <%= hint.name %>)</font>:<%
	  if (hint.freetext!=null) { 
	    for (String word : hint.freetext) {
            %> <a href="search?<%= QueryParameters.FULL_SEARCH_PARAM_EQ %>
<%= Util.URIEncode(QueryParser.escape(word)) %>"><%=
                Util.htmlize(word) %></a> &nbsp; <%
	    }
	  }
	  if (hint.refs!=null)  {
	    for (String word : hint.refs) {
            %> <a href="search?<%= QueryParameters.REFS_SEARCH_PARAM_EQ %>
<%= Util.URIEncode(QueryParser.escape(word)) %>"><%=
                Util.htmlize(word) %></a> &nbsp; <%
	    }
	  }
	  if (hint.defs!=null) {
	    for (String word : hint.defs) {
            %> <a href="search?<%= QueryParameters.DEFS_SEARCH_PARAM_EQ %>
<%= Util.URIEncode(QueryParser.escape(word)) %>"><%=
                Util.htmlize(word) %></a> &nbsp; <%
            }
	  }
        %></p><%
        }
        %>
        <p class="pagetitle"> Your search <b><%
            Util.htmlize(searchHelper.query.toString(), out); %></b>
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
        int start = searchHelper.start;
        int max = searchHelper.maxItems;
        long totalHits = searchHelper.totalHits;
        long thispage = Math.min(totalHits - start, max);  // number of items to display on the current page
        // We have a lots of results to show: create a slider for
        String slider = Util.createSlider(start, max, totalHits, request);
        %>
        <p class="pagetitle">Searched <b><%
            Util.htmlize(searchHelper.query.toString(), out);
            %></b> (Results <b> <%= start + 1 %> – <%= thispage + start
            %></b> of <b><%= totalHits %></b>) sorted by <%=
            searchHelper.order.getDesc() %></p><%
        if (slider.length() > 0) {
        %>
        <p class="slider"><%= slider %></p><%
        }
        %>
        <table><%
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

include file="foot.jspf"

%>
