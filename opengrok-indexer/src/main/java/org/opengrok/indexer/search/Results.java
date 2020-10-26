/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import static org.opengrok.indexer.web.messages.MessagesContainer.MESSAGES_MAIN_PAGE_TAG;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TandemPath;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.SearchHelper;
import org.opengrok.indexer.web.Util;
import org.opengrok.indexer.web.messages.MessagesUtils;

/**
 * @author Chandan slightly rewritten by Lubos Kosco
 */
public final class Results {

    private static final Logger LOGGER = LoggerFactory.getLogger(Results.class);

    private Results() {
        // Util class, should not be constructed
    }

    /**
     * Create a has map keyed by the directory of the document found.
     *
     * @param searcher searcher to use.
     * @param hits hits produced by the given searcher's search
     * @param startIdx the index of the first hit to check
     * @param stopIdx the index of the last hit to check
     * @return a (directory, hitDocument) hashmap
     * @throws CorruptIndexException
     * @throws IOException
     */
    private static Map<String, ArrayList<Integer>> createMap(
        IndexSearcher searcher, ScoreDoc[] hits, int startIdx, long stopIdx)
            throws CorruptIndexException, IOException {

        LinkedHashMap<String, ArrayList<Integer>> dirHash =
                new LinkedHashMap<>();
        for (int i = startIdx; i < stopIdx; i++) {
            int docId = hits[i].doc;
            Document doc = searcher.doc(docId);

            String rpath = doc.get(QueryBuilder.PATH);
            if (rpath == null) {
                continue;
            }

            String parent = rpath.substring(0, rpath.lastIndexOf('/'));
            ArrayList<Integer> dirDocs = dirHash.get(parent);
            if (dirDocs == null) {
                dirDocs = new ArrayList<>();
                dirHash.put(parent, dirDocs);
            }
            dirDocs.add(docId);
        }
        return dirHash;
    }

    private static String getTags(File basedir, String path, boolean compressed) {
        char[] content = new char[1024 * 8];
        try (HTMLStripCharFilter r = new HTMLStripCharFilter(getXrefReader(basedir, path, compressed))) {
            int len = r.read(content);
            return new String(content, 0, len);
        } catch (Exception e) {
            String fnm = compressed ? TandemPath.join(basedir + path, ".gz") :
                    basedir + path;
            LOGGER.log(Level.WARNING, "An error reading tags from " + fnm, e);
        }
        return "";
    }

    /** Return a reader for the specified xref file. */
    private static Reader getXrefReader(
                    File basedir, String path, boolean compressed)
            throws IOException {
        /*
         * For backward compatibility, read the OpenGrok-produced document
         * using the system default charset.
         */
        if (compressed) {
            return new BufferedReader(IOUtils.createBOMStrippedReader(
                    new GZIPInputStream(new FileInputStream(new File(basedir,
                            TandemPath.join(path, ".gz"))))));
        } else {
            return new BufferedReader(IOUtils.createBOMStrippedReader(
                    new FileInputStream(new File(basedir, path))));
        }
    }

    /**
     * Prints out results in html form. The following search helper fields are
     * required to be properly initialized: <ul>
     * <li>{@link SearchHelper#dataRoot}</li>
     * <li>{@link SearchHelper#contextPath}</li>
     * <li>{@link SearchHelper#searcher}</li> <li>{@link SearchHelper#hits}</li>
     * <li>{@link SearchHelper#historyContext} (ignored if {@code null})</li>
     * <li>{@link SearchHelper#sourceContext} (ignored if {@code null})</li>
     * <li>{@link SearchHelper#summarizer} (if sourceContext is not
     * {@code null})</li> <li>{@link SearchHelper#sourceRoot} (if
     * sourceContext or historyContext is not {@code null})</li> </ul>
     *
     * @param out write destination
     * @param sh search helper which has all required fields set
     * @param start index of the first hit to print
     * @param end index of the last hit to print
     * @throws HistoryException history exception
     * @throws IOException I/O exception
     * @throws ClassNotFoundException class not found
     */
    public static void prettyPrint(Writer out, SearchHelper sh, int start,
            long end)
            throws HistoryException, IOException, ClassNotFoundException {
        Project p;
        String ctxE = Util.URIEncodePath(sh.contextPath);
        String xrefPrefix = sh.contextPath + Prefix.XREF_P;
        String morePrefix = sh.contextPath + Prefix.MORE_P;
        String xrefPrefixE = ctxE + Prefix.XREF_P;
        File xrefDataDir = new File(sh.dataRoot, Prefix.XREF_P.toString());

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        boolean evenRow = true;
        out.write("<tbody class=\"search-result\">");
        for (Map.Entry<String, ArrayList<Integer>> entry :
                createMap(sh.searcher, sh.hits, start, end).entrySet()) {
            String parent = entry.getKey();
            out.write("<tr class=\"dir\"><td colspan=\"3\"><a href=\"");
            out.write(xrefPrefixE);
            out.write(Util.URIEncodePath(parent));
            out.write("/\">");
            out.write(htmlize(parent));
            out.write("/</a>");
            if (sh.desc != null) {
                out.write(" - <i>");
                out.write(sh.desc.get(parent));
                out.write("</i>");
            }

            p = Project.getProject(parent);
            String messages = MessagesUtils.messagesToJson(p, MESSAGES_MAIN_PAGE_TAG);
            if (p != null && !messages.isEmpty()) {
                out.write(" <a href=\"" + xrefPrefix + "/" + p.getName() + "\">");
                out.write("<span class=\"note-" + MessagesUtils.getMessageLevel(p.getName(), MESSAGES_MAIN_PAGE_TAG) +
                        " important-note important-note-rounded\" data-messages='" + messages + "'>!</span>");
                out.write("</a>");
            }

            int tabSize = sh.getTabSize(p);
            PrintPlainFinalArgs fargs = new PrintPlainFinalArgs(out, sh, env,
                xrefPrefix, tabSize, morePrefix);

            out.write("</td></tr>");
            for (int docId : entry.getValue()) {
                Document doc = sh.searcher.doc(docId);
                String rpath = doc.get(QueryBuilder.PATH);
                String rpathE = Util.URIEncodePath(rpath);
                if (evenRow) {
                    out.write("<tr class=\"search-result-even-row\">");
                } else {
                    out.write("<tr>");
                }
                evenRow = !evenRow;
                Util.writeHAD(out, sh.contextPath, rpathE, false);
                out.write("<td class=\"f\"><a href=\"");
                out.write(xrefPrefixE);
                out.write(rpathE);
                out.write("\"");
                if (env.isLastEditedDisplayMode()) {
                    printLastEditedDate(out, doc);
                }
                out.write(">");
                out.write(htmlize(rpath.substring(rpath.lastIndexOf('/') + 1)));
                out.write("</a>");
                out.write("</td><td><code class=\"con\">");
                if (sh.sourceContext != null) {
                    AbstractAnalyzer.Genre genre = AbstractAnalyzer.Genre.get(
                            doc.get(QueryBuilder.T));
                    if (AbstractAnalyzer.Genre.XREFABLE == genre && sh.summarizer != null) {
                        String xtags = getTags(xrefDataDir, rpath, env.isCompressXref());
                        // FIXME use Highlighter from lucene contrib here,
                        // instead of summarizer, we'd also get rid of
                        // apache lucene in whole source ...
                        out.write(sh.summarizer.getSummary(xtags).toString());
                    } else if (AbstractAnalyzer.Genre.HTML == genre && sh.summarizer != null) {
                        String htags = getTags(sh.sourceRoot, rpath, false);
                        out.write(sh.summarizer.getSummary(htags).toString());
                    } else if (genre == AbstractAnalyzer.Genre.PLAIN) {
                        printPlain(fargs, doc, docId, rpath);
                    }
                }

                if (sh.historyContext != null) {
                    sh.historyContext.getContext(new File(sh.sourceRoot, rpath),
                            rpath, out, sh.contextPath);
                }
                out.write("</code></td></tr>\n");
            }
        }
        out.write("</tbody>");
    }

    private static void printLastEditedDate(final Writer out, final Document doc) throws IOException {
        try {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            String dd = df.format(DateTools.stringToDate(doc.get("date")));
            out.write(" class=\"result-annotate\" title=\"");
            out.write("Last modified: ");
            out.write(dd);
            out.write("\"");
        } catch (ParseException ex) {
            LOGGER.log(Level.WARNING, "An error parsing date information", ex);
        }
    }

    private static void printPlain(PrintPlainFinalArgs fargs, Document doc,
        int docId, String rpath) throws ClassNotFoundException, IOException {

        fargs.shelp.sourceContext.toggleAlt();

        boolean didPresentNew = fargs.shelp.sourceContext.getContext2(fargs.env,
            fargs.shelp.searcher, docId, fargs.out, fargs.xrefPrefix,
            fargs.morePrefix, true, fargs.tabSize);

        if (!didPresentNew) {
            /*
             * Fall back to the old view, which re-analyzes text using
             * PlainLinetokenizer. E.g., when source code is updated (thus
             * affecting timestamps) but re-indexing is not yet complete.
             */
            Definitions tags = null;
            IndexableField tagsField = doc.getField(QueryBuilder.TAGS);
            if (tagsField != null) {
                tags = Definitions.deserialize(tagsField.binaryValue().bytes);
            }
            Scopes scopes;
            IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
            if (scopesField != null) {
                scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
            } else {
                scopes = new Scopes();
            }
            boolean isDefSearch = fargs.shelp.builder.isDefSearch();
            // SRCROOT is read with UTF-8 as a default.
            File sourceFile = new File(fargs.shelp.sourceRoot, rpath);
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 Reader r = IOUtils.createBOMStrippedReader(fis, StandardCharsets.UTF_8.name())) {
                fargs.shelp.sourceContext.getContext(r, fargs.out,
                    fargs.xrefPrefix, fargs.morePrefix, rpath, tags, true,
                    isDefSearch, null, scopes);
            } catch (IOException ex) {
                String errMsg = String.format("No context for %s", sourceFile);
                if (LOGGER.isLoggable(Level.FINE)) {
                    // WARNING but with FINE detail
                    LOGGER.log(Level.WARNING, errMsg, ex);
                } else {
                    LOGGER.log(Level.WARNING, errMsg);
                }
            }
        }
    }

    private static String htmlize(String raw) {
        return Util.htmlize(raw);
    }

    private static class PrintPlainFinalArgs {
        final Writer out;
        final SearchHelper shelp;
        final RuntimeEnvironment env;
        final String xrefPrefix;
        final String morePrefix;
        final int tabSize;

        PrintPlainFinalArgs(Writer out, SearchHelper shelp,
                RuntimeEnvironment env, String xrefPrefix, int tabSize,
                String morePrefix) {
            this.out = out;
            this.shelp = shelp;
            this.env = env;
            this.xrefPrefix = xrefPrefix;
            this.morePrefix = morePrefix;
            this.tabSize = tabSize;
        }
    }
}
