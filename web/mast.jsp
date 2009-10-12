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
--%><%@ page import = "javax.servlet.*,
             java.lang.*,
             javax.servlet.http.*,
             java.util.*,
             java.io.*,
             org.opensolaris.opengrok.index.*,
             org.opensolaris.opengrok.configuration.*,
             org.opensolaris.opengrok.web.EftarFileReader,
             org.opensolaris.opengrok.web.Util,
             org.opensolaris.opengrok.history.HistoryGuru
             "
             %><%@ page session="false" %><%@ page errorPage="error.jsp"%><%
String context = request.getContextPath();
String servlet = request.getServletPath();
String reqURI = request.getRequestURI();
String path = request.getPathInfo();
if (path == null) path = "";
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
environment.setUrlPrefix(context + "/s?");
environment.register();
String rawSource = environment.getSourceRootPath();
String resourcePath = rawSource + path;
File resourceFile = new File(resourcePath);
resourcePath = resourceFile.getAbsolutePath();
boolean valid = true;
boolean noHistory = true;
boolean noAnnotation = true;
boolean annotate = false;
String basename = resourceFile.getName();
boolean isDir = false;
EftarFileReader ef = null;
String parent = null;
String parentBasename = resourceFile.getParentFile().getName();
IgnoredNames ignoredNames = environment.getIgnoredNames();
String uriEncodedName = Util.URIEncodePath(path);

if(resourcePath.length() < rawSource.length()
|| ignoredNames.ignore(path)
|| ignoredNames.ignore(parentBasename)
|| !resourcePath.startsWith(rawSource)) {
    valid = false;
    response.sendError(404);
    return;
} else if (!resourceFile.canRead() && resourcePath.startsWith(rawSource)) {
    String newPath = rawSource + "/on/" + path;
    File newFile = new File(newPath);
    if(newFile.canRead()) {
        if(newFile.isDirectory() && servlet.startsWith("/xref") && !path.endsWith("/")) {
            response.sendRedirect(context + servlet + "/on" + path + "/");
        } else {
            response.sendRedirect(context + servlet + "/on" + path);
        }
    }
    valid = false;
    response.sendError(404);
    return;
} else {
    valid = true;
    path = resourcePath.substring(rawSource.length());
    if ("".equals(path)) {
        path = "/";
    }
    if (File.separatorChar == '\\') {
        path = path.replace('\\','/');
    }
    isDir = resourceFile.isDirectory();
    if (isDir && !servlet.startsWith("/xref") && !servlet.startsWith("/hist")) {	//if it is an existing directory perhaps people wanted directory xref
        if(!reqURI.endsWith("/")) {
            response.sendRedirect(context + "/xref" + path + "/");
        } else {
            response.sendRedirect(context + "/xref" + path);
        }
    } if (isDir && !reqURI.endsWith("/")) {
        response.sendRedirect(context + servlet + path +"/");
    } else {
        
        long flast = resourceFile.lastModified();
        String dtag = "";
        
        if (request.getDateHeader("If-Modified-Since") >= flast ) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            valid = false;
        } else {
            
            response.setDateHeader("Last-Modified", flast);
            int lastSlash = path.lastIndexOf('/');
            parent = (lastSlash != -1) ? path.substring(0, lastSlash) : "";
            int pLastSlash = parent.lastIndexOf('/');
            parentBasename = pLastSlash != -1 ? parent.substring(pLastSlash+1) : parent;
            noHistory = !HistoryGuru.getInstance().hasHistory(resourceFile);
            noAnnotation = isDir ||
                    !HistoryGuru.getInstance().hasAnnotation(resourceFile);
            annotate = !noAnnotation &&
                    Boolean.parseBoolean(request.getParameter("a"));
            try{
                ef = new EftarFileReader(environment.getDataRootPath() + "/index/dtags.eftar");
                dtag = ef.get(path);
                if(servlet.startsWith("/xr")) {
                } else {
                    if(ef != null) {
                        try {
                            ef.close();
                        } catch (IOException e) {
                        } finally {
                            ef = null;
                        }
                    }
                }
            } catch (Exception e) {
                dtag = "";
            }
    String pageTitle="Cross Reference: " + path;

%><%@ include file="httpheader.jspf" %>
<body><div id="page">
<form action="<%=context%>/search">
    <div id="header"><%@ include file="pageheader.jspf" %>
        <div id="pagetitle"><b id="filename">Cross Reference: <%=basename%></b><br/><%=dtag%></div>
    </div>
    <div id="Masthead"><tt><a href="<%=context%>/xref/">xref</a>: <%=org.opensolaris.opengrok.web.Util.breadcrumbPath(context + "/xref", path)%></tt></div>    
    <div id="bar"><a href="<%=context%>" id="home">Home</a> | 
        <%
        
        if (noHistory || servlet.startsWith("/hi")) {
        %> <span class="c" id="history">History</span><%
        } else {
        %><a id="history" href="<%=context%>/history<%=path%>">History</a><%
        }
        if (noAnnotation) {
        %> | <span class="c" id="annotate">Annotate</span><%
        } else {
           String rev = request.getParameter("r");
            if (rev == null) {
                rev = "";
            } else if (rev.length() > 0) {
                rev = "&r=" + rev;
            }
        
            if (Boolean.parseBoolean(request.getParameter("a"))) {
        %> | <span id="toggle-annotate-by-javascript" style="display: none">
            <a href="#" onClick="javascript:toggle_annotations(); return false;" title="Show or hide line annotation(commit revisions,authors)." >Annotate</a>
        </span>
        <span id="toggle-annotate">
            <a href="<%=context%>/xref<%=path%><% 
               if (rev.length() > 0) { 
               %>?<%=rev%><% 
           } %>">Annotate</a></span>
        <script type="text/javascript">
            <!--
            var toggle_js = document.getElementById('toggle-annotate-by-javascript'); 
            var toggle_ss = document.getElementById('toggle-annotate');

            toggle_js.style.display = 'inline';
            toggle_ss.style.display = 'none';
            // -->
        </script> <%
        } else {
        %> | <a href="<%=context%>/xref<%=path%>?a=true<%=rev%>">Annotate</a><%
        }
    }    
            if (!isDir) {
        %> | <a href="javascript:lntoggle();" title="Show or hide line numbers (might be slower if file has more than 10 000 lines).">Line #</a><%
               String rev = request.getParameter("r");
               if (rev == null || rev.equals("")) {
        %> | <a id="download" href="<%=context%>/raw<%=path%>">Download</a><%
        } else {
        %> | <a id="download" href="<%=context%>/raw<%=path%>?r=<%=rev%>">Download</a><%
        }
     }

     Project proj = Project.getProject(resourceFile);
     if  (proj != null || !environment.hasProjects()) {
        %> | <input id="search" name="q" class="q"/>
        <input type="submit" value="Search" class="submit"/>
        <%
        if (proj != null) {
        %><input type="hidden" name="project" value="<%=proj.getDescription()%>"/><%
        }
        if(isDir) {
                if(path.length() > 0) {
        %><input type="checkbox" name="path" value="<%=path%>"/> only in <b><%=path%></b><%
          }
        } else {
          %><input type="checkbox" name="path" value="<%=parent%>"/> only in <b><%=parentBasename%></b><%
        }
        }
        %>
</div></form>
<%        
} // date check
    } // not a directory redirect
}
%>
