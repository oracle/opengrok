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
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuperIndexSearcher;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;

/**
 * Generate SCM history for directory by using the Index database. (Please note
 * that SCM systems that supports changesets consisting of multiple files should
 * implement their own HistoryReader!)
 *
 * The sole purpose of this class is to produce history for generating RSS feed
 * for directory changes.
 *
 * @author Chandan
 * @author Lubos Kosco update for lucene 4.x
 */
public class DirectoryHistoryReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryHistoryReader.class);

    // This is a giant hash constructed in this class.
    // It maps date -> author -> (comment, revision, displayRevision) -> [ list of files ]
    private final Map<Date, Map<String, Map<List<String>, SortedSet<String>>>> hash
            = new LinkedHashMap<>(); // set in put()
    Iterator<Date> diter;
    Date idate;
    Iterator<String> aiter;
    String iauthor;
    Iterator<List<String>> citer;
    List<String> icomment;
    HistoryEntry currentEntry; // set in next()
    History history; // set in the constructor

    private static final int MAX_RESULTS = 40;

    /**
     * The main task of this method is to produce list of history entries for
     * the specified directory and store them in @code history. This is done by
     * searching the index to get recently changed files under in the directory
     * tree under @code path and storing their histories in giant @code hash.
     *
     * @param path directory to generate history for
     * @throws IOException when index cannot be accessed
     */
    public DirectoryHistoryReader(String path) throws IOException {
        SuperIndexSearcher searcher = null;
        try {
            // Prepare for index search.
            String srcRoot = RuntimeEnvironment.getInstance().getSourceRootPath();
            // The search results will be sorted by date.
            searcher = RuntimeEnvironment.getInstance().getSuperIndexSearcher(new File(srcRoot, path));
            SortField sfield = new SortField(QueryBuilder.DATE, SortField.Type.STRING, true);
            Sort sort = new Sort(sfield);
            QueryParser qparser = new QueryParser(QueryBuilder.PATH, new CompatibleAnalyser());
            Query query;
            ScoreDoc[] hits = null;
            try {
                // Get files under given directory by searching the index.
                query = qparser.parse(path);
                TopFieldDocs fdocs = searcher.search(query, MAX_RESULTS, sort);
                hits = fdocs.scoreDocs;
            } catch (ParseException e) {
                LOGGER.log(Level.WARNING,
                        "An error occurred while parsing search query", e);
            }
            if (hits != null) {
                // Get maximum MAX_RESULTS (why ? XXX) files which were changed recently.
                StoredFields storedFields = searcher.storedFields();
                for (int i = 0; i < MAX_RESULTS && i < hits.length; i++) {
                    int docId = hits[i].doc;
                    Document doc = storedFields.document(docId);
                    String rpath = doc.get(QueryBuilder.PATH);
                    if (!rpath.startsWith(path)) {
                        continue;
                    }
                    Date cdate;
                    try {
                        cdate = DateTools.stringToDate(doc.get(QueryBuilder.DATE));
                    } catch (java.text.ParseException ex) {
                        LOGGER.log(Level.WARNING, String.format("Could not get date for '%s'", path), ex);
                        cdate = new Date();
                    }
                    int ls = rpath.lastIndexOf('/');
                    if (ls != -1) {
                        String rparent = rpath.substring(0, ls);
                        String rbase = rpath.substring(ls + 1);
                        History hist = null;
                        File f = new File(srcRoot + rparent, rbase);
                        try {
                            hist = HistoryGuru.getInstance().getHistory(f);
                        } catch (HistoryException e) {
                            LOGGER.log(Level.WARNING,
                                    String.format("An error occurred while getting history reader for '%s'", f), e);
                        }
                        if (hist == null) {
                            put(cdate, "", null, "-", "", rpath);
                        } else {
                            // Put all history entries for this file into the giant hash.
                            readFromHistory(hist, rpath);
                        }
                    }
                }
            }

            // Now go through the giant hash and produce history entries from it.
            ArrayList<HistoryEntry> entries = new ArrayList<>();
            while (next()) {
                entries.add(currentEntry);
            }

            // This is why we are here. Store all the constructed history entries
            // into history object.
            history = new History(entries);
        } finally {
            if (searcher != null) {
                try {
                    searcher.release();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING,
                            String.format("An error occurred while closing index reader for '%s'", path), ex);
                }
            }
        }
    }

    public History getHistory() {
        return history;
    }

    // Fill the giant hash with some data from one history entry.
    private void put(Date date, String revision, String displayRevision, String author, String comment, String path) {
        long time = date.getTime();
        date.setTime(time - time % 3600000L);

        Map<String, Map<List<String>, SortedSet<String>>> ac = hash.computeIfAbsent(date, k -> new HashMap<>());

        Map<List<String>, SortedSet<String>> cf = ac.computeIfAbsent(author, k -> new HashMap<>());

        // We are not going to modify the list so this is safe to do.
        List<String> cr = new ArrayList<>();
        cr.add(comment);
        cr.add(revision);
        cr.add(displayRevision);
        SortedSet<String> fls = cf.computeIfAbsent(cr, k -> new TreeSet<>());

        fls.add(path);
    }

    /**
     * Do one traversal step of the giant hash and produce history entry object
     * and store it into @code currentEntry.
     *
     * @return true if history entry was successfully generated otherwise false
     */
    private boolean next() {
        if (diter == null) {
            diter = hash.keySet().iterator();
        }

        if (citer == null || !citer.hasNext()) {
            if (aiter == null || !aiter.hasNext()) {
                if (diter.hasNext()) {
                    aiter = hash.get(idate = diter.next()).keySet().iterator();
                } else {
                    return false;
                }

            }
            citer = hash.get(idate).get(iauthor = aiter.next()).keySet().iterator();
        }

        icomment = citer.next();

        currentEntry = new HistoryEntry(icomment.get(1), icomment.get(2), idate, iauthor, icomment.get(0), true,
          hash.get(idate).get(iauthor).get(icomment));

        return true;
    }

    /**
     * Go through all history entries in @code hist for file @code path and
     * store them in the giant hash.
     *
     * @param hist history to store
     * @param rpath path of the file corresponding to the history
     */
    private void readFromHistory(History hist, String rpath) {
        for (HistoryEntry entry : hist.getHistoryEntries()) {
            if (entry.isActive()) {
                String comment = entry.getMessage();
                String cauthor = entry.getAuthor();
                Date cdate = entry.getDate();
                String revision = entry.getRevision();
                String displayRevision = entry.getDisplayRevision();
                put(cdate, revision, displayRevision, cauthor, comment, rpath);
                break;
            }
        }
    }
}
