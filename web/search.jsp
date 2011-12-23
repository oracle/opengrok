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

Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.

--%><%@page session="false" errorPage="error.jsp" import="
org.opensolaris.opengrok.search.Results,
org.opensolaris.opengrok.web.SearchHelper,
org.opensolaris.opengrok.web.SortOrder,
org.opensolaris.opengrok.web.Suggestion"
%><%@

include file="projects.jspf"

%><%!
    private StringBuilder createUrl(SearchHelper sh, boolean menu) {
        StringBuilder url = new StringBuilder(64);
        QueryBuilder qb = sh.builder;
        if (menu) {
            url.append("search?");
        } else {
            Util.appendQuery(url, "sort", sh.order.toString());
        }
        if (qb != null) {
            Util.appendQuery(url, "q", qb.getFreetext());
            Util.appendQuery(url, "defs", qb.getDefs());
            Util.appendQuery(url, "refs", qb.getRefs());
            Util.appendQuery(url, "path", qb.getPath());
            Util.appendQuery(url, "hist", qb.getHist());
        }
        if (sh.projects != null && sh.projects.size() != 0) {
            Util.appendQuery(url, "project", cfg.getRequestedProjectsAsString());
        }
        return url;
    }
%><%
/* ---------------------- search.jsp start --------------------- */
{
    cfg = PageConfig.get(request);

    long starttime = System.currentTimeMillis();

    SearchHelper searchHelper = cfg.prepareSearch()
        .prepareExec(cfg.getRequestedProjects()).executeQuery().prepareSummary();
    if (searchHelper.redirect != null) {
        response.sendRedirect(searchHelper.redirect);
    }
    if (searchHelper.errorMsg != null
        && searchHelper.errorMsg.startsWith(SearchHelper.PARSE_ERROR_MSG))
    {
        searchHelper.errorMsg = SearchHelper.PARSE_ERROR_MSG
            + "<br/>You might try to enclose your search term in quotes, "
            + "<a href=\"help.jsp#escaping\">escape special characters</a> "
            + "with <b>\\</b>, or read the <a href=\"help.jsp\">Help</a> "
            + "on the query language."
            + "Error message from parser:<br/>" + searchHelper.errorMsg
                .substring(SearchHelper.PARSE_ERROR_MSG.length());
    }
    if (searchHelper.errorMsg != null) {
        cfg.setTitle("Search Error");
    } else {
        cfg.setTitle("Search");
    }
    response.addCookie(new Cookie("OpenGrokSorting", searchHelper.order.toString()));
%><%@

include file="httpheader.jspf"

%><body>
<div id="page">
    <div id="whole_header">
        <div id="header"><%@

include file="pageheader.jspf"

%>
        </div>
        <div id="Masthead"></div>
        <div id="bar">
            <ul>
                <li><a href="<%= request.getContextPath()
                    %>/"><span id="home"></span>Home</a></li>
            </ul>
            <%-- TODO: jel: IMHO it should be move to menu.jspf as combobox --%>
            <div id="sortfield">
                <label for="sortby">Sort by</label>
                <ul id="sortby"><%
    StringBuilder url = createUrl(searchHelper, true).append("&amp;sort=");
    for (SortOrder o : SortOrder.values()) {
        if (searchHelper.order == o) {
                    %><li><span class="active"><%= o.getDesc() %></span></li><%
        } else {
                    %><li><a href="<%= url %><%= o %>"><%= o.getDesc() %></a></li><%
        }
    }
                %></ul>
            </div>
        </div>
        <div id="menu"><%@

include file="menu.jspf"

%>
        </div>
    </div>
    <div id="results"><%
    // TODO spellchecking cycle below is not that great and we only create
    // suggest links for every token in query, not for a query as whole
    if (searchHelper.errorMsg != null) {
        %><h3>Error</h3>
        <p><%= Util.htmlize(searchHelper.errorMsg) %></p><%
    } else if (searchHelper.hits == null) {
        %><p>No hits</p><%
    } else if (searchHelper.hits.length == 0) {
        List<Suggestion> hints = searchHelper.getSuggestions();
        for (Suggestion hint : hints) {
        %><p><font color="#cc0000">Did you mean (for <%= hint.name %>)</font>:<%
            for (String word : hint.freetext) {
            %> <a href=search?q=<%= word %>><%= word %></a> &nbsp;  <%
            }
            for (String word : hint.refs) {
            %> <a href=search?refs=<%= word %>><%= word %></a> &nbsp;  <%
            }
            for (String word : hint.defs) {
            %> <a href=search?defs=<%= word %>><%= word %></a> &nbsp;  <%
            }
        %></p><%
        }
        %>
        <p> Your search <b><%= searchHelper.query %></b> did not match any files.
            <br/> Suggestions:<br/>
        </p>
        <ul>
            <li>Make sure all terms are spelled correctly.</li>
            <li>Try different keywords.</li>
            <li>Try more general keywords.</li>
            <li>Use 'wil*' cards if you are looking for partial match.</li>
        </ul><%
    } else {
        // We have a lots of results to show: create a slider for
        String slider = "";
        int thispage;  // number of items to display on the current page
        int start = searchHelper.start;
        int max = searchHelper.maxItems;
        int totalHits = searchHelper.totalHits;
        if (searchHelper.maxItems < searchHelper.totalHits) {
            StringBuilder buf = new StringBuilder(4096);
            thispage = (start + max) < totalHits ? max : totalHits - start;
            StringBuilder urlp = createUrl(searchHelper, false);
            int labelStart = 1;
            int sstart = start - max * (start / max % 10 + 1) ;
            if (sstart < 0) {
                sstart = 0;
                labelStart = 1;
            } else {
                labelStart = sstart / max + 1;
            }
            int label = labelStart;
            int labelEnd = label + 11;
            for (int i = sstart; i < totalHits && label <= labelEnd; i+= max) {
                if (i <= start && start < i + max) {
                    buf.append("<span class=\"sel\">").append(label).append("</span>");
                } else {
                    buf.append("<a class=\"more\" href=\"s?n=").append(max)
                        .append("&amp;start=").append(i).append(urlp).append("\">");
                    if (label == labelStart && label != 1) {
                        buf.append("&lt;&lt");
                    } else if (label == labelEnd && i < totalHits) {
                        buf.append("&gt;&gt;");
                    } else {
                        buf.append(label);
                    }
                    buf.append("</a>");
                }
                label++;
            }
            slider = buf.toString();
        } else {
            // set the max index to max or last
            thispage = totalHits - start;
        }
        %>
        <p class="pagetitle">Searched <b><%= searchHelper.query
            %></b> (Results <b> <%= start + 1 %> - <%= thispage + start
            %></b> of <b><%= totalHits %></b>) sorted by <%=
            searchHelper.order %></p><%
        if (slider.length() > 0) {
        %>
        <p class="slider"><%= slider %></p><%
        }
        %>
        <table><%
        Results.prettyPrint(out, searchHelper, start, start + thispage);
        %>
        </table>
        <p><b>Completed in <%= System.currentTimeMillis() - starttime
            %> milliseconds</b></p><%
        if (slider.length() > 0) {
        %>
        <p class="slider"><%= slider %></p><%
        }
        %>
    </div><%
    }
    searchHelper.destroy();
}
/* ---------------------- search.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>