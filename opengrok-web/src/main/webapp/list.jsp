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

Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
Portions Copyright 2011 Jens Elkner.
Portions Copyright (c) 2017-2020, Chris Fraire <cfraire@me.com>.

--%>
<%@page errorPage="error.jsp" import="
java.io.BufferedInputStream,
java.io.File,
java.io.FileInputStream,
java.io.InputStream,
java.io.InputStreamReader,
java.io.IOException,
java.io.Reader,
java.net.URLEncoder,
java.nio.charset.StandardCharsets,
java.util.List,
java.util.Locale,
java.util.logging.Level,
java.util.logging.Logger,
java.util.Set,
java.util.TreeSet,
org.opengrok.indexer.analysis.AnalyzerGuru,
org.opengrok.indexer.analysis.Ctags,
org.opengrok.indexer.analysis.Definitions,
org.opengrok.indexer.analysis.AbstractAnalyzer,
org.opengrok.indexer.analysis.AbstractAnalyzer.Genre,
org.opengrok.indexer.analysis.AnalyzerFactory,
org.opengrok.indexer.analysis.NullableNumLinesLOC,
org.opengrok.indexer.history.Annotation,
org.opengrok.indexer.history.HistoryGuru,
org.opengrok.indexer.index.IndexDatabase,
org.opengrok.indexer.logger.LoggerFactory,
org.opengrok.indexer.search.DirectoryEntry,
org.opengrok.indexer.search.DirectoryExtraReader,
org.opengrok.indexer.util.FileExtraZipper,
org.opengrok.indexer.util.ForbiddenSymlinkException,
org.opengrok.indexer.util.ObjectPool,
org.opengrok.indexer.util.IOUtils,
org.opengrok.indexer.web.QueryParameters,
org.opengrok.web.DirectoryListing,
org.opengrok.indexer.web.SearchHelper"
%>
<%
final String DUMMY_REVISION = "unknown";

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
        String latestRevision = cfg.getLatestRevision();
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
            List<NullableNumLinesLOC> extras = null;
            SearchHelper searchHelper = cfg.prepareInternalSearch();
            /*
             * N.b. searchHelper.destroy() is called via
             * WebappListener.requestDestroyed() on presence of the following
             * REQUEST_ATTR.
             */
            request.setAttribute(SearchHelper.REQUEST_ATTR, searchHelper);
            if (project != null) {
                searchHelper.prepareExec(project);
            } else {
                //noinspection Convert2Diamond
                searchHelper.prepareExec(new TreeSet<String>());
            }

            if (searchHelper.searcher != null) {
                DirectoryExtraReader extraReader = new DirectoryExtraReader();
                String primePath = path;
                try {
                    primePath = searchHelper.getPrimeRelativePath(projectName, path);
                } catch (IOException | ForbiddenSymlinkException ex) {
                    LOGGER.log(Level.WARNING, String.format(
                            "Error getting prime relative for %s", path), ex);
                }
                extras = extraReader.search(searchHelper.searcher, primePath);
            }

            FileExtraZipper zipper = new FileExtraZipper();
            List<DirectoryEntry> entries = zipper.zip(resourceFile, files,
                extras);

            List<String> readMes = dl.extraListTo(
                    Util.URIEncodePath(request.getContextPath()),
                    resourceFile, out, path, entries);
            File[] catfiles = cfg.findDataFiles(readMes);
            for (int i=0; i < catfiles.length; i++) {
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
             data-markdown-download="<%= request.getContextPath() + Prefix.DOWNLOAD_P + Util.URIEncodePath(cfg.getPath() + readMes.get(i)) %>">
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
                        /**
                         * For backward compatibility, read the OpenGrok-produced
                         * document using the system default charset.
                         */
                        r = new InputStreamReader(bin);
                        // dumpXref() is also useful here for translating links.
                        Util.dumpXref(out, r, request.getContextPath());
                    } else if (g == AbstractAnalyzer.Genre.PLAIN) {
%>
<div id="src" data-navigate-window-enabled="<%= navigateWindowEnabled %>">
    <pre><%
                        // We're generating xref for the latest revision, so we can
                        // find the definitions in the index.
                        Definitions defs = IndexDatabase.getDefinitions(resourceFile);
                        Annotation annotation = cfg.getAnnotation();
                        // SRCROOT is read with UTF-8 as a default.
                        r = IOUtils.createBOMStrippedReader(bin,
                            StandardCharsets.UTF_8.name());
                        AnalyzerGuru.writeDumpedXref(request.getContextPath(), a,
                                r, out, defs, annotation, project);
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
</div><%
            }
        } else {
            // requesting a previous revision or needed to generate xref on the fly
            // (either economy mode is enabled or the cfg.findDataFile() call above failed).
            AnalyzerFactory a = AnalyzerGuru.find(basename);
            Genre g = AnalyzerGuru.getGenre(a);
            String error = null;
            if (g == Genre.PLAIN || g == Genre.HTML || g == null) {
                InputStream in = null;
                File tempf = null;
                try {
                    if (rev.equals(DUMMY_REVISION)) {
                        in = new BufferedInputStream(new FileInputStream(resourceFile));
                    } else {
                        tempf = File.createTempFile("ogtags", basename);
                        if (HistoryGuru.getInstance().getRevision(tempf,
                                resourceFile.getParent(), basename, rev)) {
                            in = new BufferedInputStream(new FileInputStream(tempf));
                        } else {
                            tempf.delete();
                            tempf = null;
                        }
                    }
                } catch (Exception e) {
                    // fall through to error message
                    error = e.getMessage();
                    if (tempf != null) {
                        tempf.delete();
                        tempf = null;
                    }
                }
                if (in != null) {
                    try {
                        if (g == null) {
                            a = AnalyzerGuru.find(in, basename);
                            g = AnalyzerGuru.getGenre(a);
                        }
                        if (g == AbstractAnalyzer.Genre.DATA || g == AbstractAnalyzer.Genre.XREFABLE || g == null) {
    %>
    <div id="src">
    Download binary file, <a href="<%= rawPath %>?<%= QueryParameters.REVISION_PARAM_EQ %>
<%= Util.URIEncode(rev) %>"><%= basename %></a>
    </div><%
                        } else {
    %>
    <div id="src">
        <pre><%
                            if (g == AbstractAnalyzer.Genre.PLAIN) {
                                Definitions defs = null;
                                ObjectPool<Ctags> ctagsPool = cfg.getEnv().
                                        getIndexerParallelizer().getCtagsPool();
                                int tries = 2;
                                while (cfg.getEnv().isWebappCtags()) {
                                    Ctags ctags = ctagsPool.get();
                                    try {
                                        ctags.setTabSize(project != null ?
                                                project.getTabSize() : 0);
                                        defs = ctags.doCtags(tempf.getPath());
                                        break;
                                    } catch (InterruptedException ex) {
                                        if (--tries > 0) {
                                            LOGGER.log(Level.WARNING,
                                                    "doCtags() interrupted--{0}",
                                                    ex.getMessage());
                                            continue;
                                        }
                                        LOGGER.log(Level.WARNING, "doCtags()", ex);
                                        break;
                                    } catch (Exception ex) {
                                        LOGGER.log(Level.WARNING, "doCtags()", ex);
                                        break;
                                    } finally {
                                        ctags.reset();
                                        ctagsPool.release(ctags);
                                    }
                                }
                                Annotation annotation = cfg.getAnnotation();
                                //not needed yet
                                //annotation.writeTooltipMap(out);
                                // SRCROOT is read with UTF-8 as a default.
                                r = IOUtils.createBOMStrippedReader(in,
                                    StandardCharsets.UTF_8.name());
                                AnalyzerGuru.writeDumpedXref(
                                        request.getContextPath(),
                                        a, r, out,
                                        defs, annotation, project);
                            } else if (g == AbstractAnalyzer.Genre.IMAGE) {
        %></pre>
        <img src="<%= rawPath %>?<%= QueryParameters.REVISION_PARAM_EQ %><%= Util.URIEncode(rev) %>"/>
        <pre><%
                            } else if (g == AbstractAnalyzer.Genre.HTML) {
                                /**
                                 * For backward compatibility, read the
                                 * OpenGrok-produced document using the system
                                 * default charset.
                                 */
                                r = new InputStreamReader(in);
                                /**
                                 * dumpXref() is also useful here for
                                 * translating links.
                                 */
                                Util.dumpXref(out, r, request.getContextPath());
                            } else {
        %>Download binary file, <a href="<%= rawPath %>?<%= QueryParameters.REVISION_PARAM_EQ %>
<%= Util.URIEncode(rev) %>"><%= basename %></a><%
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Failed xref on-the-fly", e);
                    } finally {
                        if (r != null) {
                            IOUtils.close(r);
                            in = null;
                        }
                        if (in != null) {
                            IOUtils.close(in);
                            in = null;
                        }
                        if (tempf != null) {
                            tempf.delete();
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
            } else if (g == AbstractAnalyzer.Genre.IMAGE) {
    %>
    <div id="src">
        <img src="<%= rawPath %>?<%= QueryParameters.REVISION_PARAM_EQ %><%= Util.URIEncode(rev) %>"
	    alt="Image from Source Repository"/>
    </div><%
            } else {
    %>
    <div id="src">
    Download binary file, <a href="<%= rawPath %>?<%= QueryParameters.REVISION_PARAM_EQ %>
<%= Util.URIEncode(rev) %>"><%= basename %></a>
    </div><%
            }
        }
    } else {
        // requesting cross referenced file

        File xrefFile = cfg.findDataFile();
        if (xrefFile != null) {
%>
<div id="src" data-navigate-window-enabled="<%= navigateWindowEnabled %>">
    <pre><%
            boolean compressed = xrefFile.getName().endsWith(".gz");
            Util.dumpXref(out, xrefFile, compressed, request.getContextPath());
    %></pre>
</div><%
        } else {
%>
<p class="error">Failed to get xref file</p><%
        }
    }
}
/* ---------------------- list.jsp end --------------------- */
%><%@

include file="foot.jspf"

%>
