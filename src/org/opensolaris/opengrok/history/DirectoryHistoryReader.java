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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.util.Version;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IndexDatabase;

/**
 * Generate SCM history for directory by using the Index database. (Please note
 * that SCM systems that supports changesets consisting of multiple files should
 * implement their own HistoryReader!)
 *
 * @author Chandan
 * @author Lubos Kosco update for lucene 3.0.0
 */
public class DirectoryHistoryReader {

    private final Map<Date, Map<String, Map<String, SortedSet<String>>>> hash =
        new LinkedHashMap<Date, Map<String, Map<String, SortedSet<String>>>>();
    Iterator<Date> diter;
    Date idate;
    Iterator<String> aiter;
    String iauthor;
    Iterator<String> citer;
    String icomment;
    HistoryEntry currentEntry;
    History history;

    @SuppressWarnings("PMD.ConfusingTernary")
    public DirectoryHistoryReader(String path) throws IOException {
        //TODO can we introduce paging here ???  this class is used just for rss.jsp !
        int hitsPerPage=RuntimeEnvironment.getInstance().getHitsPerPage();
        int cachePages=RuntimeEnvironment.getInstance().getCachePages();
        IndexReader ireader = null;
        IndexSearcher searcher = null;
        try {
            String src_root = RuntimeEnvironment.getInstance().getSourceRootPath();
            ireader = IndexDatabase.getIndexReader(path);
            if (ireader == null) {
                throw new IOException("Could not locate index database");
            }
            searcher = new IndexSearcher(ireader);
            SortField sfield=new SortField("date",SortField.STRING, true);
            Sort sort = new Sort(sfield);
            QueryParser qparser = new QueryParser(Version.LUCENE_CURRENT,"path", new CompatibleAnalyser());
            Query query = null;            
            ScoreDoc[] hits = null;
            try {
                query = qparser.parse(path);
                TopFieldDocs fdocs=searcher.search(query, null,hitsPerPage*cachePages, sort);
                fdocs=searcher.search(query, null,fdocs.totalHits, sort);
                hits = fdocs.scoreDocs;
            } catch (org.apache.lucene.queryParser.ParseException e) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while parsing search query", e);
            }
            if (hits != null) {
                for (int i = 0; i < 40 && i < hits.length; i++) {
                    int docId = hits[i].doc;
                    Document doc = searcher.doc(docId);
                    String rpath = doc.get("path");
                    if (!rpath.startsWith(path)) {
                        continue;
                    }
                    Date cdate;
                    try {
                        cdate = DateTools.stringToDate(doc.get("date"));
                    } catch (java.text.ParseException ex) {
                        OpenGrokLogger.getLogger().log(Level.WARNING, "Could not get date for " + path, ex);
                        cdate = new Date();
                    }
                    int ls = rpath.lastIndexOf('/');
                    if (ls != -1) {
                        String rparent = (ls != -1) ? rpath.substring(0, ls) : "";
                        String rbase = rpath.substring(ls + 1);
                        History hist = null;
                        try {
                            File f = new File(src_root + rparent, rbase);
                            hist = HistoryGuru.getInstance().getHistory(f);
                        } catch (HistoryException e) {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while getting history reader", e);
                        }
                        if (hist == null) {
                            put(cdate, "-", "", rpath);
                        } else {
                            readFromHistory(hist, rpath);
                        }
                    }
                }
            }

            ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
            while (next()) {
                entries.add(currentEntry);
            }

            history = new History(entries);
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (Exception ex) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing searcher", ex);
                }
            }
            if (ireader != null) {
                try {
                    ireader.close();
                } catch (Exception ex) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing reader", ex);
                }
            }
        }
    }

    public History getHistory() {
        return history;
    }

    private void put(Date date, String author, String comment, String path) {
        long time = date.getTime();
        date.setTime(time - (time % 3600000l));

        Map<String, Map<String, SortedSet<String>>> ac = hash.get(date);
        if (ac == null) {
            ac = new HashMap<String, Map<String, SortedSet<String>>>();
            hash.put(date, ac);
        }

        Map<String, SortedSet<String>> cf = ac.get(author);
        if (cf == null) {
            cf = new HashMap<String, SortedSet<String>>();
            ac.put(author, cf);
        }

        SortedSet<String> fls = cf.get(comment);
        if (fls == null) {
            fls = new TreeSet<String>();
            cf.put(comment, fls);
        }

        fls.add(path);
    }

    private boolean next() throws IOException {
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

        currentEntry = new HistoryEntry(null, idate, iauthor, icomment, true);

        return true;
    }

    private void readFromHistory(History hist, String rpath) {
        for (HistoryEntry entry : hist.getHistoryEntries()) {
            if (entry.isActive()) {
                String comment = entry.getMessage();
                String cauthor = entry.getAuthor();
                Date cdate = entry.getDate();
                put(cdate, cauthor, comment, rpath);
                break;
            }
        }
    }
}
