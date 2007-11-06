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

ident	"%Z%%M% %I%     %E% SMI"

--%><%@ page import = "javax.servlet.*,
java.lang.*,
javax.servlet.http.*,
java.util.*,
java.io.*,
org.opensolaris.opengrok.index.*,
org.opensolaris.opengrok.configuration.*
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
String uriEncodedName = null;
{
   File theFile = new File(path);
   String parentName = theFile.getParent();
   if (parentName != null) {
      uriEncodedName = parentName + "/" + Util.URIEncode(theFile.getName());
   } else {
      uriEncodedName = "/" + Util.URIEncode(theFile.getName());
   }
}

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
            noHistory = !(isDir || HistoryGuru.getInstance().hasHistory(rawSource + "/" + parent));
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

if ((!isDir && noHistory) || servlet.startsWith("/hi")) {
	%> <span class="c" id="history">History</span> |<%
} else {
	%><a id="history" href="<%=context%>/history<%=path%>">History</a> |<%
}
if (noAnnotation || annotate) {
%> <span class="c" id="annotate">Annotate</span> |<%
} else {
    String rev = request.getParameter("r");
    if (rev == null) {
        rev = "";
    } else if (rev.length() > 0) {
        rev = "&r=" + rev;
    }
%> <a id="annotate" href="<%=context%>/xref<%=path%>?a=true<%=rev%>">Annotate</a> |<%
}
        if (!isDir) {
           String rev = request.getParameter("r");
           if (rev == null || rev.equals("")) {
%> <a id="download" href="<%=context%>/raw<%=uriEncodedName%>">Download</a> | <%
           } else {
%> <a id="download" href="<%=context%>/raw<%=uriEncodedName%>?r=<%=rev%>">Download</a> | <%
           }
        }

%> <input id="search" name="q" class="q"/>
<input type="submit" value="Search" class="submit"/><%

if(isDir) {
                if(path.length() > 0) {
	%><input type="checkbox" name="path" value="<%=path%>"/> only in <b><%=path%></b><%
  }
} else {
	%><input type="checkbox" name="path" value="<%=parent%>"/> only in <b><%=parentBasename%></b><%
}

%></div></form><%

} // date check
    } // not a directory redirect
}
%>
