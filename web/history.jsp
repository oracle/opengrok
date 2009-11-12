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

Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
Use is subject to license terms.

ident	"@(#)history.jsp 1.1     05/11/11 SMI"

--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
java.text.*,  
org.opensolaris.opengrok.analysis.*,
org.opensolaris.opengrok.web.*,
org.opensolaris.opengrok.history.*,
java.util.regex.*
"
%><%@include file="mast.jsp"%><script type="text/javascript">
// <![CDATA[
function toggle_filelist() {
  var spans = document.getElementsByTagName("span");
  for (var i = 0; i < spans.length; i++) {
    var span = spans[i];
    if (span.className == "filelist") {
      span.setAttribute("style", "display: none;");
      span.className = "filelist-hidden";
    } else if (span.className == "filelist-hidden") {
      span.setAttribute("style", "display: inline;");
      span.className = "filelist";
    }
  }
}
// ]]>
</script><%
if (path.length() > 0 && valid) {
    boolean striked = false;
    String userPage = environment.getUserPage();
    String bugPage = environment.getBugPage();
    String bugRegex = environment.getBugPattern();
    if(bugRegex == null || bugRegex.equals("")) {
        bugRegex = "\\b([12456789][0-9]{6})\\b";
    }
    Pattern bugPattern = Pattern.compile(bugRegex);
    String reviewPage = environment.getReviewPage();
    String reviewRegex = environment.getReviewPattern();
    if(reviewRegex == null || reviewRegex.equals("")) {
        reviewRegex = "\\b(\\d{4}/\\d{3})\\b";
    }
    Pattern reviewPattern = Pattern.compile(reviewRegex);
    Format df = new SimpleDateFormat("dd-MMM-yyyy");
    Date tstart = new Date();
    File f = new File(rawSource + path);
    if (!HistoryGuru.getInstance().hasHistory(f)) {
        response.sendError(404, "No history");
        return;        
    }
    History hist = HistoryGuru.getInstance().getHistory(f);

    if (hist == null) {
        response.sendError(404, "No history");
        return;
    }
    
%><form action="<%=context%>/diff<%=path%>">
<table cellspacing="0" cellpadding="2" border="0" width="100%" class="src">
<tr>
    <td colspan="4"><span class="pagetitle">History log of <a href="<%= context +"/xref" + path %>"><%=path%></a></span></td>
</tr>
<tr class="thead">
    <td>Revision</td><%
    if (!isDir) {
        %><th><input type="submit" value=" Compare "/></th><%
    }
    %><td>Date</td><td>Author</td><td>Comments<%
    if (hist.hasFileList()) {
      %> <a href="#" onclick="javascript: toggle_filelist(); return false;">
      <span class="filelist-hidden" style="display: none;">
        (&lt;&lt;&lt; Hide modified files)
      </span>
      <span class="filelist" style="display: inline;">
        (Show modified files &gt;&gt;&gt;)
      </span>
      </a><%
    }
    %></td>
</tr><%
boolean alt = true;
for (HistoryEntry entry : hist.getHistoryEntries()) {
    String rev = entry.getRevision();
    if (rev == null || rev.length() == 0) {
        rev = "";
    }
    alt = !alt;
    %><tr  valign="top" <%= alt ?  "class=\"alt\"" : "" %>><%
    if (isDir) {
    %><td>&nbsp;<%=rev%>&nbsp;</td><%
    } else {
        if (entry.isActive()) {
            String rp = Util.URIEncodePath(path);
%><td>&nbsp;<a name="<%=rev%>" href="<%= context +"/xref" + rp + "?r=" + Util.URIEncode(rev) %>"><%=rev%></a>&nbsp;</td><td align="center"><input type="radio" name="r1" value="<%=rp%>@<%=rev%>"/>
<input type="radio" name="r2" value="<%=rp%>@<%=rev%>"/></td><%
        } else {
            striked = true;
  %><td><strike>&nbsp;<%=rev%>&nbsp; </strike></td><td>&nbsp;</td><%
        }
}
%><td><% 
        Date date = entry.getDate();
        if (date != null) {
            %><%=df.format(date)%><%
        } else {
            %>&nbsp;<%
        }
%>&nbsp;</td>
<td>
<%

String author = entry.getAuthor();
if (author == null) {
        %>(no author)<%
} else if (userPage != null && ! userPage.equals("")) {
        %><a href="<%= userPage + author %>"><%= author %></a><%
} else {
        %><%= author %><%
}

%>&nbsp;</td><td><%
String cout=Util.htmlize(entry.getMessage());
if (bugPage != null && ! bugPage.equals("")){
        cout=bugPattern.matcher(cout).replaceAll("<a href=\"" + bugPage + "$1\">$1</a>"); }
if (reviewPage != null && ! reviewPage.equals("")) {
    cout=reviewPattern.matcher(cout).replaceAll("<a href=\"" + reviewPage + "$1\">$1</a>"); }
	%><%= cout  %>
<%
Set<String> files = entry.getFiles();
if(files != null) {%><span class="filelist-hidden" style="display: none;"><br/><%
    for (String ifile : files) {
        String jfile = ifile;
        if ("/".equals(path)) {
            jfile = ifile.substring(1);
        } else if (ifile.startsWith(path) && ifile.length() > (path.length()+1)) {
            jfile = ifile.substring(path.length()+1);
        }
        if (rev == "") {
            %><a class="h" href="<%=context%>/xref<%=ifile%>"><%=jfile%></a><br/><%
        } else {
            %><a class="h" href="<%=context%>/xref<%=ifile%>?r=<%=rev%>"><%=jfile%></a><br/><%            
        }
    }%></span><%
}
%></td></tr><%
}
	%></table></form><%
        if(striked) {
            %><p><b>Note:</b> No associated file changes are available for revisions with strike-through numbers (eg. <strike>1.45</strike>)</p><%
        }
        %><p class="rssbadge"><a href="<%=context%>/rss<%=Util.URIEncodePath(path)%>"><img src="<%=context%>/<%=environment.getWebappLAF()%>/img/rss.png" width="80" height="15" alt="RSS XML Feed" title="RSS XML Feed of latest changes"/></a></p><%
}
%><%@include file="foot.jspf"%>
