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

--%><%@page import="
java.io.BufferedInputStream,
java.io.BufferedReader,
java.io.FileInputStream,
java.io.FileReader,
java.io.InputStream,
java.io.InputStreamReader,
java.io.Reader,
java.util.ArrayList,
java.util.Arrays,
java.util.List,
java.util.Set,
java.util.logging.Level,
java.util.zip.GZIPInputStream,

org.opensolaris.opengrok.OpenGrokLogger,
org.opensolaris.opengrok.analysis.AnalyzerGuru,
org.opensolaris.opengrok.analysis.Definitions,
org.opensolaris.opengrok.analysis.FileAnalyzer.Genre,
org.opensolaris.opengrok.analysis.FileAnalyzerFactory,
org.opensolaris.opengrok.history.Annotation,
org.opensolaris.opengrok.index.IndexDatabase,
org.opensolaris.opengrok.web.DirectoryListing"
%><%
{
    // need to set it here since requesting parameters
    if (request.getCharacterEncoding() == null) {
        request.setCharacterEncoding("UTF-8");
    }
    cfg = PageConfig.get(request);
    Annotation annotation = cfg.getAnnotation();
    if (annotation != null) {
        int r = annotation.getWidestRevision();
        int a = annotation.getWidestAuthor();
        cfg.addHeaderData("<style type=\"text/css\">"
            + ".blame .r { width: " + (r == 0 ? 6 : r) + "ex; } "
            + ".blame .a { width: " + (a == 0 ? 6 : a) + "ex; } "
            + "</style>");
    }
}
%><%@include

file="mast.jsp"

%><script type="text/javascript">/* <![CDATA[ */
document.pageReady.push(function() { pageReadyList();});
/* ]]> */</script>
<%
/* ---------------------- list.jsp start --------------------- */
{
    cfg = PageConfig.get(request);
    String rev = cfg.getRequestedRevision();

    File resourceFile = cfg.getResourceFile();
    String path = cfg.getPath();
    String basename = resourceFile.getName();
    String rawPath = request.getContextPath() + Prefix.RAW_P + path;
    Reader r = null;
    if (cfg.isDir()) {
        // valid resource is requested
        // mast.jsp assures, that resourceFile is valid and not /
        // see cfg.resourceNotAvailable()
        Project activeProject = Project.getProject(resourceFile);
        String cookieValue = cfg.getRequestedProjectsAsString();
        if (activeProject != null) {
            Set<String>  projects = cfg.getRequestedProjects();
            if (!projects.contains(activeProject.getDescription())) {
                projects.add(activeProject.getDescription());
                // update cookie
                cookieValue = cookieValue.length() == 0
                    ? activeProject.getDescription()
                    : activeProject.getDescription() + '/' + cookieValue;
                Cookie cookie = new Cookie("OpenGrokProject", cookieValue);
                // TODO hmmm, projects.jspf doesn't set a path
                cookie.setPath(request.getContextPath() + '/');
                response.addCookie(cookie);
            }
        }
        // requesting a directory listing
        DirectoryListing dl = new DirectoryListing(cfg.getEftarReader());
        List<String> files = cfg.getResourceFileList();
        if (!files.isEmpty()) {
            List<String> readMes = dl.listTo(resourceFile, out, path, files);
            File[] catfiles = cfg.findDataFiles(readMes);
            for (int i=0; i < catfiles.length; i++) {
                if (catfiles[i] == null) {
                    continue;
                }
%><h3><%= readMes.get(i) %></h3>
<div id="src">
    <pre><%
                Util.dump(out, catfiles[i], catfiles[i].getName().endsWith(".gz"));
    %></pre>
</div><%
            }
        }
    } else if (rev.length() != 0) {
        // requesting a previous revision
        FileAnalyzerFactory a = AnalyzerGuru.find(basename);
        Genre g = AnalyzerGuru.getGenre(a);
        String error = null;
        if (g == Genre.PLAIN|| g == Genre.HTML || g == null) {
            InputStream in = null;
            try {
                in = HistoryGuru.getInstance()
                    .getRevision(resourceFile.getParent(), basename, rev.substring(2));
            } catch (Exception e) {
                // fall through to error message
                error = e.getMessage();
            }
            if (in != null) {
                try {
                    if (g == null) {
                        a = AnalyzerGuru.find(in);
                        g = AnalyzerGuru.getGenre(a);
                    }
                    if (g == Genre.DATA || g == Genre.XREFABLE
                        || g == null)
                    {
%>
<div id="src">
    Binary file [Click <a href="<%= rawPath %>?<%= rev
        %>">here</a> to download]
</div><%
                    } else {
%>
<div id="src">
    <span class="pagetitle"><%= basename %> revision <%=
        rev.substring(2) %></span>
    <pre><%
                        if (g == Genre.PLAIN) {
                            // We don't have any way to get definitions
                            // for old revisions currently.
                            Definitions defs = null;
                            Annotation annotation = cfg.getAnnotation();
                            //not needed yet
                            //annotation.writeTooltipMap(out);
                            r = new InputStreamReader(in);
                            AnalyzerGuru.writeXref(a, r, out, defs,
                                annotation, Project.getProject(resourceFile));
                        } else if (g == Genre.IMAGE) {
    %></pre>
    <img src="<%= rawPath %>?<%= rev %>"/>
    <pre><%
                        } else if (g == Genre.HTML) {
                            r = new InputStreamReader(in);
                            Util.dump(out, r);
                        } else {
        %> Click <a href="<%= rawPath %>?<%= rev %>">download <%= basename %></a><%
                        }
                    }
                } catch (IOException e) {
                    error = e.getMessage();
                } finally {
                    if (r != null) {
                        try { r.close(); in = null;}
                        catch (Exception e) { /* ignore */ }
                    }
                    if (in != null) {
                        try { in.close(); }
                        catch (Exception e) { /* ignore */ }
                    }
                }
    %></pre>
</div><%
            } else {
%>
<h3 class="error">Error reading file</h3><%
                if (error != null) {
%>
<p class="error"><%= error %></p><%
                }
            }
        } else if (g == Genre.IMAGE) {
%>
<div id="src">
    <img src="<%= rawPath %>?<%= rev %>"/>
</div><%
        } else {
%>
<div id="src">
    Binary file [Click <a href="<%= rawPath %>?<%= rev
        %>">here</a> to download]
</div><%
        }
    } else {
        // requesting cross referenced file
        File xrefFile = null;
        if (!cfg.annotate()) {
            xrefFile = cfg.findDataFile();
        }
        if (xrefFile != null) {
%>
<div id="src">
    <pre><%
            Util.dump(out, xrefFile, xrefFile.getName().endsWith(".gz"));
    %></pre>
</div><%
        } else {
            // annotate
            BufferedInputStream bin =
                new BufferedInputStream(new FileInputStream(resourceFile));
            try {
                FileAnalyzerFactory a = AnalyzerGuru.find(basename);
                Genre g = AnalyzerGuru.getGenre(a);
                if (g == null) {
                    a = AnalyzerGuru.find(bin);
                    g = AnalyzerGuru.getGenre(a);
                }
                if (g == Genre.IMAGE) {
%>
<div id="src">
    <img src="<%= rawPath %>"/>
</div><%
                } else if ( g == Genre.HTML) {
                    r = new InputStreamReader(bin);
                    Util.dump(out, r);
                } else if (g == Genre.PLAIN) {
%>
<div id="src">
    <pre><%
                    // We're generating xref for the latest revision, so we can
                    // find the definitions in the index.
                    Definitions defs = IndexDatabase.getDefinitions(resourceFile);
                    Annotation annotation = cfg.getAnnotation();
                    r = new InputStreamReader(bin);
                    AnalyzerGuru.writeXref(a, r, out, defs, annotation,
                        Project.getProject(resourceFile));
    %></pre>
</div><%
                } else {
%>
Click <a href="<%= rawPath %>">download <%= basename %></a><%
                }
            } finally {
                if (r != null) {
                    try { r.close(); bin = null; }
                    catch (Exception e) { /* ignore */ }
                }
                if (bin != null) {
                    try { bin.close(); }
                    catch (Exception e) { /* ignore */ }
                }
            }
        }
    }
}
/* ---------------------- list.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>