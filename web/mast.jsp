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

Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.

--%><%@ page import = "javax.servlet.*,
             java.lang.*,
             javax.servlet.http.*,
             java.util.*,
             java.io.*,
             org.opensolaris.opengrok.index.*,
             org.opensolaris.opengrok.configuration.*,
             org.opensolaris.opengrok.web.EftarFileReader,
             org.opensolaris.opengrok.web.Util,
             org.opensolaris.opengrok.web.Constants,
             org.opensolaris.opengrok.history.HistoryGuru"
             %><%@ page session="false" %><%@ page errorPage="error.jsp"%><%

// Use UTF-8 if no encoding is specified in the request
if (request.getCharacterEncoding() == null) {
    request.setCharacterEncoding("UTF-8");
}

String context = request.getContextPath();
String servlet = request.getServletPath();
String reqURI = request.getRequestURI();
String path = request.getPathInfo();
if (path == null) path = "";
RuntimeEnvironment environment = RuntimeEnvironment.getInstance();
environment.setUrlPrefix(context + Constants.searchR + "?");
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
    String newPath = rawSource + "/on/" + path; //TODO do we still use "on" ???
    File newFile = new File(newPath);
    if(newFile.canRead()) {
        if(newFile.isDirectory() && servlet.startsWith(Constants.xrefP) && !path.endsWith("/")) {
            response.sendRedirect(context + servlet + "/on" + uriEncodedName + "/");
        } else {
            response.sendRedirect(context + servlet + "/on" + uriEncodedName);
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
    uriEncodedName = Util.URIEncodePath(path);
    isDir = resourceFile.isDirectory();
    if (isDir && !servlet.startsWith(Constants.xrefP) && !servlet.startsWith(Constants.histP)) {	//if it is an existing directory perhaps people wanted directory xref
        if(!reqURI.endsWith("/")) {
            response.sendRedirect(context + Constants.xrefP + uriEncodedName + "/");
        } else {
            response.sendRedirect(context + Constants.xrefP + uriEncodedName);
        }
    } if (isDir && !reqURI.endsWith("/")) {
        response.sendRedirect(context + servlet + uriEncodedName +"/");
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
                if(servlet.startsWith(Constants.xrefS)) {
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
    String rev = request.getParameter("r");
            if (rev == null) {
                rev = "";
            } else if (rev.length() > 0) {
                rev = "&r=" + rev;
            }
    String h = request.getParameter("h");
%><%@ include file="httpheader.jspf" %><%//below style is for the fancy always on top search bar%>
<body style="overflow:hidden;">
<script type="text/javascript" src="<%=context%>/jquery-1.4.4.min.js"></script>
<script type="text/javascript">/* <![CDATA[ */
function get_annotations() {
    link="<%=context+Constants.xrefP+uriEncodedName%>?a=true<%=rev%>";
    hash="&h="+window.location.hash.substring(1,window.location.hash.length);
    window.location=link+hash;
}
    $().ready(function() {
     h="<%=h%>";
     if (!window.location.hash) {
         if (h!=null && h!="null")  { window.location.hash=h; }
             else { $('#content').focus(); }
      }
} );
/* ]]> */</script>
<% if (annotate) { %>
<script type="text/javascript" src="<%=context%>/jquery.tooltip-1.3.pack.js"></script>
<script type="text/javascript">/* <![CDATA[ */
function toggle_annotations() {
  $("span").each(function() {      
      if (this.className == 'blame') {
         this.className = 'blame-hidden';
      } else if (this.className == 'blame-hidden') {
         this.className = 'blame';
      }
     }
    );   
}
$().ready(function() {
    $('a[name=r]').tooltip({
        left: 5,
	showURL: false
       });    
} );
/* ]]> */</script>
<% } %>
<div id="page">
<div id="whole_header" >
<form action="<%=context+Constants.searchP%>">
    <div id="header"><%@ include file="pageheader.jspf" %>
        <div id="pagetitle"><b id="filename">Cross Reference: <%=basename%></b><% if (dtag!=null & dtag!="") { %><br/><%=dtag%><% } %></div>
    </div>
    <div id="Masthead"><tt><a href="<%=context+Constants.xrefP%>/">xref</a>: <%=org.opensolaris.opengrok.web.Util.breadcrumbPath(context + Constants.xrefP, path)%></tt></div>
    <div id="bar"><a href="<%=context%>/" id="home">Home</a> |
        <%
        
        if (noHistory || servlet.startsWith(Constants.histS)) {
        %> <span class="c" id="history">History</span><%
        } else {
        %><a id="history" href="<%=context+Constants.histL+uriEncodedName%>">History</a><%
        }
        if (noAnnotation) {
        %> | <span class="c" id="annotate">Annotate</span><%
        } else {       
            if (Boolean.parseBoolean(request.getParameter("a"))) {
        %> | <span id="toggle-annotate-by-javascript" style="display: none">
            <a href="#" onclick="javascript:toggle_annotations(); return false;" title="Show or hide line annotation(commit revisions,authors)." >Annotate</a>
        </span>
        <span id="toggle-annotate">
            <a href="<%=context+Constants.xrefP+uriEncodedName%><%
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
        %> | <a href="#" onclick="javascript:get_annotations(); return false;">Annotate</a><%
        }
    }    
            if (!isDir) {
                if ( servlet.startsWith(Constants.xrefS) ) {
               %> | <a href="#" onclick="javascript:lntoggle();return false;" title="Show or hide line numbers (might be slower if file has more than 10 000 lines).">Line #</a> | <a href="#" onclick="javascript:lsttoggle();return false;" title="Show or hide symbol list.">Navigate</a><%
                }
               String lrev = request.getParameter("r");
               if (lrev == null || lrev.equals("")) {
        %> | <a id="download" href="<%=context+Constants.rawP+uriEncodedName%>">Download</a><%
        } else {
        %> | <a id="download" href="<%=context+Constants.rawP+uriEncodedName%>?r=<%=lrev%>">Download</a><%
        }
     }

     Project proj = Project.getProject(resourceFile);
     //if (proj != null || !environment.hasProjects())
     {
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
</div></form></div>
        <div id="content"><%
} // date check
    } // not a directory redirect
}
%>
