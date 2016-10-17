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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 *
 * Portions Copyright 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
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
import org.json.simple.JSONArray;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryException;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.web.Prefix;
import org.opensolaris.opengrok.web.SearchHelper;
import org.opensolaris.opengrok.web.Util;

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
    private static Map<String, ArrayList<Document>> createMap(IndexSearcher searcher, ScoreDoc[] hits, int startIdx, int stopIdx)
            throws CorruptIndexException, IOException {
        LinkedHashMap<String, ArrayList<Document>> dirHash =
                new LinkedHashMap<>();
        for (int i = startIdx; i < stopIdx; i++) {
            int docId = hits[i].doc;
            Document doc = searcher.doc(docId);
            String rpath = doc.get(QueryBuilder.PATH);
            String parent = rpath.substring(0, rpath.lastIndexOf('/'));
            ArrayList<Document> dirDocs = dirHash.get(parent);
            if (dirDocs == null) {
                dirDocs = new ArrayList<>();
                dirHash.put(parent, dirDocs);
            }
            dirDocs.add(doc);
        }
        return dirHash;
    }

    private static String getTags(File basedir, String path, boolean compressed) {
        char[] content = new char[1024 * 8];
        try (HTMLStripCharFilter r = new HTMLStripCharFilter(getXrefReader(basedir, path, compressed))) {
            int len = r.read(content);
            return new String(content, 0, len);
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "An error reading tags from " + basedir + path
                    + (compressed ? ".gz" : ""), e);
        }
        return "";
    }

    /** Return a reader for the specified xref file. */
    private static Reader getXrefReader(
                    File basedir, String path, boolean compressed)
            throws IOException {
        if (compressed) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(
                    new FileInputStream(new File(basedir, path + ".gz")))));
        } else {
            return new BufferedReader(new FileReader(new File(basedir, path)));
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
     * {@code null})</li> <li>{@link SearchHelper#compressed} (if sourceContext
     * is not {@code null})</li> <li>{@link SearchHelper#sourceRoot} (if
     * sourceContext or historyContext is not {@code null})</li> </ul>
     *
     * @param out write destination
     * @param sh search helper which has all required fields set
     * @param start index of the first hit to print
     * @param end index of the last hit to print
     * @throws HistoryException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static void prettyPrint(Writer out, SearchHelper sh, int start,
            int end)
            throws HistoryException, IOException, ClassNotFoundException {
        Project p;
        String ctxE = Util.URIEncodePath(sh.contextPath);
        String xrefPrefix = sh.contextPath + Prefix.XREF_P;
        String morePrefix = sh.contextPath + Prefix.MORE_P;
        String xrefPrefixE = ctxE + Prefix.XREF_P;
        File xrefDataDir = new File(sh.dataRoot, Prefix.XREF_P.toString());

        for (Map.Entry<String, ArrayList<Document>> entry :
                createMap(sh.searcher, sh.hits, start, end).entrySet()) {
            String parent = entry.getKey();
            out.write("<tr class=\"dir\"><td colspan=\"3\"><a href=\"");
            out.write(xrefPrefixE);
            out.write(Util.URIEncodePath(parent));
            out.write("/\">");
            out.write(parent); // htmlize ???
            out.write("/</a>");
            if (sh.desc != null) {
                out.write(" - <i>");
                out.write(sh.desc.get(parent)); // htmlize ???
                out.write("</i>");
            }
            JSONArray messages;
            if ((p = Project.getProject(parent)) != null
                    && (messages = Util.messagesToJson(RuntimeEnvironment.MESSAGES_MAIN_PAGE_TAG,
                            p.getDescription())).size() > 0) {
                out.write(" <a ");
                out.write("href=\"" + xrefPrefix + "/" + p.getDescription() + "\">");
                out.write("<span class=\"important-note important-note-rounded\" data-messages='" + messages + "'>!</span>");
                out.write("</a>");
            }
            out.write("</td></tr>");
            for (Document doc : entry.getValue()) {
                String rpath = doc.get(QueryBuilder.PATH);
                String rpathE = Util.URIEncodePath(rpath);
                DateFormat df;
                out.write("<tr>");
                Util.writeHAD(out, sh.contextPath, rpathE, false);
                out.write("<td class=\"f\"><a href=\"");
                out.write(xrefPrefixE);
                out.write(rpathE);
                out.write("\">");
                out.write(rpath.substring(rpath.lastIndexOf('/') + 1)); // htmlize ???
                out.write("</a>");
                if(sh.lastEditedDisplayMode){
                    try {
                        // insert last edited date if possible
                        df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                        String dd = df.format(DateTools.stringToDate(doc.get("date")));
                        out.write("<small class=\"edited\">(last modified ");
                        out.write(dd);
                        out.write(")</small>");    
                    } catch (ParseException ex) {
                        LOGGER.log(
                                Level.WARNING, "An error parsing date information", ex);
                    }                    
                }
                out.write("</td><td><tt class=\"con\">");
                if (sh.sourceContext != null) {
                    Genre genre = Genre.get(doc.get("t"));
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
                    if (Genre.XREFABLE == genre && sh.summarizer != null) {
                        String xtags = getTags(xrefDataDir, rpath, sh.compressed);
                        // FIXME use Highlighter from lucene contrib here,
                        // instead of summarizer, we'd also get rid of
                        // apache lucene in whole source ...
                        out.write(sh.summarizer.getSummary(xtags).toString());
                    } else if (Genre.HTML == genre && sh.summarizer != null) {
                        String htags = getTags(sh.sourceRoot, rpath, false);
                        out.write(sh.summarizer.getSummary(htags).toString());
                    } else {
                        FileReader r = genre == Genre.PLAIN
                                ? new FileReader(new File(sh.sourceRoot, rpath))
                                : null;
                        sh.sourceContext.getContext(r, out, xrefPrefix, morePrefix, 
                                rpath, tags, true, sh.builder.isDefSearch(), null, scopes);
                    }
                }

                if (sh.historyContext != null) {
                    sh.historyContext.getContext(new File(sh.sourceRoot, rpath),
                            rpath, out, sh.contextPath);
                }
                out.write("</tt></td></tr>\n");
            }
        }
    }
}
