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

Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.

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
        History hist=null;
        try {
            hist = HistoryGuru.getInstance().getHistory(f);
        } catch (Exception e)    {
            // should not happen
            %><h3>Problem</h3><p class="error"><%= e.getMessage() %></p><%
        }
        if (hist != null) {
%><script type="text/javascript">/* <![CDATA[ */
document.domReady.push(function() {domReadyHistory();});
/* ]]> */</script>
<form action="<%= context + Prefix.DIFF_P + uriEncodedName %>">
<table class="src" id="revisions">
    <caption>History log of <a href="<%= context + Prefix.XREF_P
        + uriEncodedName %>"><%= path %></a></caption>
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
            <th><input type="submit" value=" Compare "/></th><%
            }
            %>
            <th>Date</th>
            <th>Author</th>
            <th>Comments <%
            if (hist.hasFileList()) {
                %><a href="#" onclick="javascript: toggle_filelist(); return false;">
                    <span class="filelist-hidden">
                    (&lt;&lt;&lt; Hide modified files)</span>
                    <span class="filelist">
                    (Show modified files &gt;&gt;&gt;)</span></a><%
            }
            %>
            </th>
        </tr>
    </thead>
    <tbody>
    <%
            int count=0;
            for (HistoryEntry entry : hist.getHistoryEntries()) {
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
            <td><a name="<%= rev %>" href="<%=
                context + Prefix.XREF_P + rp + "?r=" + Util.URIEncode(rev) %>"><%=
                    rev %></a></td>
            <td>
                <input type="radio"<%
                        if (count == 0 ) {
                    %> disabled="disabled"<%
                        } else if (count == 1) {
                    %> checked="checked"<%
                        }
                    %> name="r1" value="<%= path %>@<%= rev%>"/>
                <input type="radio"
                    name="r2"<%
                        if (count == 0) {
                    %> checked="checked"<%
                        }
                    %> value="<%= path %>@<%= rev %>"/></td><%
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
            <td><%
                String cout = Util.htmlize(entry.getMessage());
                if (bugPage != null && bugPage.length() > 0) {
                    cout = bugPattern.matcher(cout).replaceAll("<a href=\""
                        + bugPage + "$1\">$1</a>");
                }
                if (reviewPage != null && reviewPage.length() > 0) {
                    cout = reviewPattern.matcher(cout).replaceAll("<a href=\""
                        + reviewPage + "$1\">$1</a>");
                }
                %><%= cout %><%
                Set<String> files = entry.getFiles();
                if (files != null) {
                %><span class="filelist-hidden"><br/><%
                    for (String ifile : files) {
                        String jfile = ifile;
                        if ("/".equals(path)) {
                            jfile = ifile.substring(1);
                        } else if (ifile.startsWith(path)
                            && ifile.length() > (path.length() + 1))
                        {
                            jfile = ifile.substring(path.length() + 1);
                        }
                        if (rev == "") {
                %>
<a class="h" href="<%= context + Prefix.XREF_P + ifile %>"><%= jfile %></a><br/><%
                        } else {
                %>
<a class="h" href="<%= context + Prefix.XREF_P + ifile %>?r=<%= rev %>"><%= jfile %></a><br/><%
                        }
                    }
                %></span><%
                }
                %></td>
        </tr><%
                count++;
            }
        %>
    </tbody>
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
