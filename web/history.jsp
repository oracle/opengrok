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

--%><%@page import="
java.text.Format,
java.text.SimpleDateFormat,
java.util.Date,
java.util.Set,
java.util.regex.Pattern,

org.opensolaris.opengrok.history.History,
org.opensolaris.opengrok.history.HistoryEntry,
org.opensolaris.opengrok.history.HistoryException,
org.opensolaris.opengrok.configuration.RuntimeEnvironment"
%><%@

include file="mast.jsp"

%><%/* ---------------------- history.jsp start --------------------- */
{
    PageConfig cfg = PageConfig.get(request);
    String path = cfg.getPath();

    if (path.length() > 0) {
        String context = request.getContextPath();
        RuntimeEnvironment env = cfg.getEnv();
        String uriEncodedName = cfg.getUriEncodedPath();

        boolean striked = false;
        String userPage = env.getUserPage();
        String userPageSuffix = env.getUserPageSuffix();
        if (userPageSuffix == null) {
            // Set to empty string so we can append it to the URL
            // unconditionally later.
            userPageSuffix = "";
        }
        String bugPage = env.getBugPage();
        String bugRegex = env.getBugPattern();
        if (bugRegex == null || bugRegex.equals("")) {
            bugRegex = "\\b([12456789][0-9]{6})\\b";
        }
        Pattern bugPattern = Pattern.compile(bugRegex);
        String reviewPage = env.getReviewPage();
        String reviewRegex = env.getReviewPattern();
        if(reviewRegex == null || reviewRegex.equals("")) {
            reviewRegex = "\\b(\\d{4}/\\d{3})\\b";
        }
        Pattern reviewPattern = Pattern.compile(reviewRegex);
        Format df = new SimpleDateFormat("dd-MMM-yyyy");
        File f = cfg.getResourceFile();
        History hist = null;
        try {
            hist = HistoryGuru.getInstance().getHistoryUI(f);
        } catch (Exception e) {
            // should not happen
            %><h3>Problem</h3><p class="error"><%= e.getMessage() %></p><%
        }
        if (hist != null) {
%><script type="text/javascript">/* <![CDATA[ */
document.domReady.push(function() {domReadyHistory();});
/* ]]> */</script>
<!--[if IE]>
<style type="text/css">
  table#revisions tbody tr td p {
        word-break: break-all;
    }
</style>
<![endif]-->
<%
// We have a lots of results to show: create a slider for
String slider = "";
int thispage;  // number of items to display on the current page
int start = cfg.getSearchStart();
int max = cfg.getSearchMaxItems();
int totalHits = hist.getHistoryEntries().size();
if (max < totalHits) {
    StringBuilder buf = new StringBuilder(4096);
    thispage = (start + max) < totalHits ? max : totalHits - start;
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
            buf.append("<a class=\"more\" href=\"?n=").append(max)
                .append("&amp;start=").append(i);
            // append revision parameters
            if (cfg.getIntParam("r1", -1) != -1) {
                buf.append("&amp;r1=").append(cfg.getIntParam("r1", -1));
            }
            if (cfg.getIntParam("r2", -1) != -1) {
                buf.append("&amp;r2=").append(cfg.getIntParam("r2", -1));
            }
            buf.append("\">");
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

int revision2 = cfg.getIntParam("r2", -1) < 0 ? 0 : cfg.getIntParam("r2", -1);
int revision1 = cfg.getIntParam("r1", -1) < revision2 ? revision2 + 1 : cfg.getIntParam("r1", -1);
revision2 = revision2 >= hist.getHistoryEntries().size() ? hist.getHistoryEntries().size() - 1 : revision2; 


%>

<form action="<%= context + Prefix.DIFF_P + uriEncodedName %>">
<table class="src" id="revisions">
    <caption>History log of <a href="<%= context + Prefix.XREF_P
        + uriEncodedName %>"><%= path %></a>
    (Results <b> <%= start + 1 %> - <%= thispage + start
            %></b> of <b><%= totalHits %></b>)
    <p class="slider"><%= slider %></p>
    </caption>
   
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
            %>
            <td><a href="<%= context + Prefix.HIST_L + rp %>#<%= rev %>"
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
                    cout = bugPattern.matcher(cout).replaceAll("<a href=\""
                        + bugPage + "$1\">$1</a>");
                }
                if (reviewPage != null && reviewPage.length() > 0) {
                    cout = reviewPattern.matcher(cout).replaceAll("<a href=\""
                        + reviewPage + "$1\">$1</a>");
                }
                
                boolean showSummary = false;
                String coutSummary = entry.getMessage();
                if (coutSummary.length() > summaryLength) {
                    showSummary = true;
                    coutSummary = coutSummary.substring(0, summaryLength - 1);
                    coutSummary = Util.htmlize(coutSummary);
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
                <p class="slider"><%= slider %></p>
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
%>" title="RSS XML Feed of latest changes"><span id="rssi"></span></a></p><%
        }

    }
}
/* ---------------------- history.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>
