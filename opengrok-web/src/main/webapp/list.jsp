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

Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2017-2020, Chris Fraire <cfraire@me.com>.

--%>
<%@page errorPage="error.jsp" import="
java.io.BufferedInputStream,
java.io.File,
java.io.FileInputStream,
java.io.InputStreamReader,
java.io.Reader,
java.net.URLEncoder,
java.nio.charset.StandardCharsets,
java.util.List,
java.util.Locale,
java.util.Set,
org.opengrok.indexer.analysis.AnalyzerGuru,
org.opengrok.indexer.analysis.Definitions,
org.opengrok.indexer.analysis.AbstractAnalyzer,
org.opengrok.indexer.analysis.AnalyzerFactory,
org.opengrok.indexer.analysis.NullableNumLinesLOC,
org.opengrok.indexer.history.Annotation,
org.opengrok.indexer.index.IndexDatabase,
org.opengrok.indexer.search.DirectoryEntry,
org.opengrok.indexer.util.FileExtraZipper,
org.opengrok.indexer.util.IOUtils,
org.opengrok.web.DirectoryListing"
%>
<%@ page import="static org.opengrok.web.PageConfig.DUMMY_REVISION" %>
<%@ page import="static org.opengrok.indexer.history.LatestRevisionUtil.getLatestRevision" %>
<%@ page import="jakarta.servlet.http.Cookie" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="org.opengrok.indexer.util.Statistics" %>
<%@ page import="org.opengrok.indexer.logger.LoggerFactory" %>
<%@ page import="java.util.logging.Logger" %>
<%
{
    // need to set it here since requesting parameters
    if (request.getCharacterEncoding() == null) {
        request.setCharacterEncoding("UTF-8");
    }

    PageConfig cfg = PageConfig.get(request);
    cfg.checkSourceRootExistence();

    String rev = cfg.getRequestedRevision();
    if (!cfg.isDir() && rev.length() == 0) {
        /*
         * Get the latest revision and redirect so that the revision number
         * appears in the URL.
         */
        String latestRevision = getLatestRevision(cfg.getResourceFile());
        if (latestRevision != null) {
            cfg.evaluateMatchOffset();
            String location = cfg.getRevisionLocation(latestRevision);
            response.sendRedirect(location);
            return;
        }
        if (!cfg.getEnv().isGenerateHtml()) {
            cfg.evaluateMatchOffset();
            /*
             * Economy mode is on and failed to get the last revision
             * (presumably running with history turned off).  Use dummy
             * revision string so that xref can be generated from the resource
             * file directly.
             */
            String location = cfg.getRevisionLocation(DUMMY_REVISION);
            response.sendRedirect(location);
            return;
        }

        if (cfg.evaluateMatchOffset()) {
            /*
             * If after calling, a match offset has been translated to a
             * fragment identifier (specifying a line#), then redirect to self.
             * This block will not be activated again the second time.
             */
            String location = cfg.getRevisionLocation(""); // empty
            response.sendRedirect(location);
            return;
        }
    }

    Annotation annotation = cfg.getAnnotation();
    if (annotation != null) {
        int r = annotation.getWidestRevision();
        int a = annotation.getWidestAuthor();
        cfg.addHeaderData("<style type=\"text/css\">"
            + ".blame .r { width: " + (r == 0 ? 6 : Math.ceil(r * 0.7)) + "em; } "
            + ".blame .a { width: " + (a == 0 ? 6 : Math.ceil(a * 0.7)) + "em; } "
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
    final Logger LOGGER = LoggerFactory.getLogger(getClass());

    PageConfig cfg = PageConfig.get(request);
    String rev = cfg.getRequestedRevision();
    Project project = cfg.getProject();

    String navigateWindowEnabled = project != null ? Boolean.toString(
            project.isNavigateWindowEnabled()) : "false";
    File resourceFile = cfg.getResourceFile();
    String path = cfg.getPath();
    String basename = resourceFile.getName();
    String rawPath = request.getContextPath() + Prefix.DOWNLOAD_P + path;
    Reader r = null;
    if (cfg.isDir()) {
        Statistics statistics = new Statistics();

        // valid resource is requested
        // mast.jsp assures, that resourceFile is valid and not /
        // see cfg.resourceNotAvailable()
        String cookieValue = cfg.getRequestedProjectsAsString();
        String projectName = null;
        if (project != null) {
            projectName = project.getName();
            Set<String>  projects = cfg.getRequestedProjects();
            if (!projects.contains(projectName)) {
                projects.add(projectName);
                // update cookie
                cookieValue = cookieValue.length() == 0 ? projectName :
                        projectName + ',' + cookieValue;
                Cookie cookie = new Cookie(PageConfig.OPEN_GROK_PROJECT, URLEncoder.encode(cookieValue, StandardCharsets.UTF_8));
                // TODO hmmm, projects.jspf doesn't set a path
                cookie.setPath(request.getContextPath() + '/');
                response.addCookie(cookie);
            }
        }
        // requesting a directory listing
        DirectoryListing dl = new DirectoryListing(cfg.getEftarReader());
        List<String> files = cfg.getResourceFileList();
        if (!files.isEmpty()) {
            List<DirectoryEntry> entries = dl.createDirectoryEntries(resourceFile, path, files);

            List<NullableNumLinesLOC> extras = cfg.getExtras(project, request);
            FileExtraZipper zipper = new FileExtraZipper();
            zipper.zip(entries, extras);

            dl.extraListTo(Util.uriEncodePath(request.getContextPath()),
                    resourceFile, out, path, entries);

            List<String> readMes = null;
            if (entries != null) {
                readMes = entries.stream().
                        filter(e -> e.getFile().getName().toLowerCase(Locale.ROOT).startsWith("readme") ||
                                e.getFile().getName().toLowerCase(Locale.ROOT).endsWith("readme")).
                        map(e -> e.getFile().getName()).
                        collect(Collectors.toList());
            }

            File[] catfiles = cfg.findDataFiles(readMes);
            for (int i = 0; i < catfiles.length; i++) {
                if (catfiles[i] == null) {
                    continue;
                }
%>
<%
    String lcName = readMes.get(i).toLowerCase(Locale.ROOT);
    if (lcName.endsWith(".md") || lcName.endsWith(".markdown")) {
    %><div id="src<%=i%>" data-markdown>
        <div class="markdown-heading">
            <h3><%= readMes.get(i) %></h3>
        </div>
        <div class="markdown-content"
             data-markdown-download="<%= request.getContextPath() + Prefix.DOWNLOAD_P + Util.uriEncodePath(cfg.getPath() + readMes.get(i)) %>">
        </div>
        <pre data-markdown-original><%
            Util.dump(out, catfiles[i], catfiles[i].getName().endsWith(".gz"));
        %></pre>
    </div>
<% } else { %>
    <h3><%= readMes.get(i) %></h3>
    <div id="src<%=i%>">
        <pre><%
            Util.dump(out, catfiles[i], catfiles[i].getName().endsWith(".gz"));
        %></pre>
    </div>
<%
    }

            }
        }

        statistics.report(LOGGER, Level.FINE, "directory listing done", "dir.list.latency");
    } else if (rev.length() != 0) {
        // requesting a revision
        File xrefFile;
        if (cfg.isLatestRevision(rev) && (xrefFile = cfg.findDataFile()) != null) {
            if (cfg.annotate()) {
                // annotate
                BufferedInputStream bin = new BufferedInputStream(new FileInputStream(resourceFile));
                try {
                    AnalyzerFactory a = AnalyzerGuru.find(basename);
                    AbstractAnalyzer.Genre g = AnalyzerGuru.getGenre(a);
                    if (g == null) {
                        a = AnalyzerGuru.find(bin);
                        g = AnalyzerGuru.getGenre(a);
                    }
                    if (g == AbstractAnalyzer.Genre.IMAGE) {
%>
<div id="src">
    <img src="<%= rawPath %>" alt="Image from Source Repository"/>
</div><%
                    } else if ( g == AbstractAnalyzer.Genre.HTML) {
                        /*
                         * For backward compatibility, read the OpenGrok-produced
                         * document using the system default charset.
                         */
                        r = new InputStreamReader(bin);
                        // dumpXref() is also useful here for translating links.
                        Util.dumpXref(out, r, request.getContextPath(), resourceFile);
                    } else if (g == AbstractAnalyzer.Genre.PLAIN) {
%>
<div id="src" data-navigate-window-enabled="<%= navigateWindowEnabled %>">
    <pre><%
                        // We're generating xref for the latest revision, so we can
                        // find the definitions in the index.
                        Definitions defs = IndexDatabase.getDefinitions(resourceFile);
                        Annotation annotation = cfg.getAnnotation();
                        // Data under source root is read with UTF-8 as a default.
                        r = IOUtils.createBOMStrippedReader(bin,
                            StandardCharsets.UTF_8.name());
                        AnalyzerGuru.writeDumpedXref(request.getContextPath(), a,
                                r, out, defs, annotation, project, resourceFile);
    %></pre>
</div><%
                    } else {
%>
Click <a href="<%= rawPath %>">download <%= basename %></a><%
                    }
                } finally {
                    if (r != null) {
                        IOUtils.close(r);
                        bin = null;
                    }
                    if (bin != null) {
                        IOUtils.close(bin);
                        bin = null;
                    }
                }

            } else {
%>
<div id="src" data-navigate-window-enabled="<%= navigateWindowEnabled %>">
    <pre><%
                    boolean compressed = xrefFile.getName().endsWith(".gz");
                    Util.dumpXref(out, xrefFile, compressed,
                            request.getContextPath());
    %></pre>
</div>
<%
            }
        } else {
%>
<%@

include file="/xref.jspf"

%>
<%
        }
    } else {
        // Requesting cross-referenced file with no known revision.
        File xrefFile = cfg.findDataFile();
        if (xrefFile != null) {
%>
<div id="src" data-navigate-window-enabled="<%= navigateWindowEnabled %>">
    <pre><%
            boolean compressed = xrefFile.getName().endsWith(".gz");
            Util.dumpXref(out, xrefFile, compressed, request.getContextPath());
    %></pre>
</div>
<%
        } else {
            // Failed to get xref, generate on the fly.
%>
<%@

include file="/xref.jspf"

%>
<%
        }
    }
}
/* ---------------------- list.jsp end --------------------- */
%><%@

include file="/foot.jspf"

%>
