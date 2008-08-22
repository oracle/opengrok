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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
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
 */
public class DirectoryHistoryReader extends HistoryReader {

    public Map<Date, HashMap<String, HashMap<String, ArrayList<String>>>> hash = new LinkedHashMap<Date, HashMap<String, HashMap<String, ArrayList<String>>>>();
    Iterator<Date> diter;
    Date idate;
    Iterator<String> aiter;
    String iauthor;
    Iterator<String> citer;
    String icomment;

    @SuppressWarnings("PMD.ConfusingTernary")
    public DirectoryHistoryReader(String path) throws IOException {
        IndexReader ireader = null;
        IndexSearcher searcher = null;
        try {
            String src_root = RuntimeEnvironment.getInstance().getSourceRootPath();
            ireader = IndexDatabase.getIndexReader(path);
            if (ireader == null) {
                throw new IOException("Could not locate index database");
            }
            searcher = new IndexSearcher(ireader);
            Sort sort = new Sort("date", true);
            QueryParser qparser = new QueryParser("path", new CompatibleAnalyser());
            Query query = null;
            Hits hits = null;
            try {
                query = qparser.parse(path);
                hits = searcher.search(query, sort);
            } catch (org.apache.lucene.queryParser.ParseException e) {
            }
            if (hits != null) {
                for (int i = 0; i < 40 && i < hits.length(); i++) {
                    Document doc = hits.doc(i);
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
                    String comment = "none", cauthor = "nobody";
                    int ls = rpath.lastIndexOf('/');
                    if (ls != -1) {
                        String rparent = (ls != -1) ? rpath.substring(0, ls) : "";
                        String rbase = rpath.substring(ls + 1);
                        comment = rparent;
                        HistoryReader hr = null;
                        try {
                            File f = new File(src_root + rparent, rbase);
                            hr = HistoryGuru.getInstance().getHistoryReader(f);
                        } catch (IOException e) {
                        }
                        if (hr == null) {
                            put(cdate, "-", "", rpath);
                        } else {
                            try {
                                while (hr.next()) {
                                    if (hr.isActive()) {
                                        comment = hr.getComment();
                                        cauthor = hr.getAuthor();
                                        cdate = hr.getDate();
                                        put(cdate, cauthor, comment, rpath);
                                        break;
                                    }
                                }
                                hr.close();
                            } catch (IOException e) {
                                put(cdate, "-", "", rpath);
                            }
                        }
                    }
                }
            }
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (Exception ex) {
                }
            }
            if (ireader != null) {
                try {
                    ireader.close();
                } catch (Exception ex) {
                }
            }
        }
    }

    public final void put(Date date, String author, String comment, String path) {
        long time = date.getTime();
        date.setTime(time - (time % 3600000l));
        HashMap<String, HashMap<String, ArrayList<String>>> ac;
        HashMap<String, ArrayList<String>> cf;
        ArrayList<String> fls;
        ac =
                hash.get(date);
        if (ac == null) {
            ac = new HashMap<String, HashMap<String, ArrayList<String>>>();
            hash.put(date, ac);
        }

        cf = ac.get(author);
        if (cf == null) {
            cf = new HashMap<String, ArrayList<String>>();
            ac.put(author, cf);
        }

        fls = cf.get(comment);
        if (fls == null) {
            fls = new ArrayList<String>();
            cf.put(comment, fls);
        }

        fls.add(path);
    }

    @Override
    public void close() {
        // don't close input
    }

    @Override
    public boolean next() throws IOException {
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
        return true;
    }

    @Override
    public String getLine() {
        return null;
    }

    @Override
    public String getRevision() {
        return null;
    }

    @Override
    public Date getDate() {
        return (Date) idate.clone();
    }

    @Override
    public String getAuthor() {
        return iauthor;
    }

    @Override
    public String getComment() {
        return icomment;
    }

    @Override
    public List<String> getFiles() {
        return hash.get(idate).get(iauthor).get(icomment);
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
