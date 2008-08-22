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
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.TagFilter;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.search.Summary.Fragment;
import org.opensolaris.opengrok.search.context.Context;
import org.opensolaris.opengrok.search.context.HistoryContext;
import org.opensolaris.opengrok.web.Util;

/**
 * This is an encapsulation of the details on how to seach in the index
 * database.
 *
 * @author Trond Norbye
 */
public class SearchEngine {
    /**
     * Holds value of property definition.
     */
    private String definition;
    
    /**
     * Holds value of property file.
     */
    private String file;
    
    /**
     * Holds value of property freetext.
     */
    private String freetext;
    
    /**
     * Holds value of property history.
     */
    private String history;
    
    /**
     * Holds value of property symbol.
     */
    private String symbol;

    /**
     * Holds value of property indexDatabase.
     */
    private Query query;
    private final CompatibleAnalyser analyzer;
    private final QueryParser qparser;
    private Context sourceContext;
    private HistoryContext historyContext;
    private Summarizer summer;
    private final List<org.apache.lucene.search.Hit> hits;
    private final char[] content = new char[1024*8];
    private String source;
    private String data;
    
    /**
     * Creates a new instance of SearchEngine
     */
    public SearchEngine() {
        analyzer = new CompatibleAnalyser();
        qparser = new QueryParser("full", analyzer);
        qparser.setDefaultOperator(QueryParser.AND_OPERATOR);
        qparser.setAllowLeadingWildcard(RuntimeEnvironment.getInstance().isAllowLeadingWildcard());
        hits = new ArrayList<org.apache.lucene.search.Hit>();
    }

    public boolean isValidQuery() {
        boolean ret = false;
        String qry = Util.buildQueryString(freetext, definition, symbol, file, history);
        if (qry.length() > 0) {
            try {
                qparser.parse(qry);
                ret = true;
            } catch (Exception e) {                
            }
        }
        
        return ret;
    }
    
    private void searchSingleDatabase(File root) throws IOException {
        IndexReader ireader = IndexReader.open(root);
        Searcher searcher = new IndexSearcher(ireader);
        Hits res = searcher.search(query);
        if (res.length() > 0) {
            Iterator iter = res.iterator();
            while (iter.hasNext()) {
                org.apache.lucene.search.Hit h = (org.apache.lucene.search.Hit) iter.next();
                hits.add(h);
            }
        }

    }
    
    public String getQuery() {
        return query.toString();
    }
    
    /**
     * Execute a search. Before calling this function, you must set the
     * appropriate seach critera with the set-functions.
     *
     * @return The number of hits
     */
    public int search() {
        source = RuntimeEnvironment.getInstance().getSourceRootPath();
        data = RuntimeEnvironment.getInstance().getDataRootPath();
        hits.clear();
                
        String qry = Util.buildQueryString(freetext, definition, symbol, file, history);
        if (qry.length() > 0) {
            try {
                query = qparser.parse(qry);
                RuntimeEnvironment env = RuntimeEnvironment.getInstance();
                File root = new File(env.getDataRootFile(), "index");            

                if (env.hasProjects()) {
                    // search all projects
                    for (Project project : env.getProjects()) {
                        searchSingleDatabase(new File(root, project.getPath()));
                    }                
                } else {
                    // search the index database
                    searchSingleDatabase(root);
                }
            } catch (Exception e) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Exception searching", e);
            }
        }
        if (!hits.isEmpty()) {
            sourceContext = null;
            summer = null;
            try {
                sourceContext = new Context(query);
                if(sourceContext.isEmpty()) {
                    sourceContext = null;
                }
                summer = new Summarizer(query, analyzer);
            } catch (Exception e) {
            }
            
            historyContext = null;
            try {
                historyContext = new HistoryContext(query);
                if(historyContext.isEmpty()) {
                    historyContext = null;
                }
            } catch (Exception e) {
            }
        }
        return hits.size();
    }
    
    public void more(int start, int end, List<Hit> ret) {
        int len = end;
        if (end > hits.size()) {
            len = hits.size();
        }
        for (int ii = start; ii < len; ++ii) {
            boolean alt = (ii % 2 == 0);
            boolean hasContext = false;
            try {
                Document doc = hits.get(ii).getDocument();
                String filename = doc.get("path");
                String genre = doc.get("t");
                Definitions tags = null;
                Fieldable tagsField = doc.getFieldable("tags");
                if (tagsField != null) {
                    tags = Definitions.deserialize(tagsField.binaryValue());
                }
                int nhits = hits.size();
                
                if(sourceContext != null) {
                    try {
                        if ("p".equals(genre) && (source != null)) {
                            hasContext = sourceContext.getContext(new InputStreamReader(new FileInputStream(source +
                                    filename)), null, null, null, filename,
                                    tags, nhits > 100, ret);
                        } else if("x".equals(genre) && data != null && summer != null){
                            int l = 0;
                            Reader r = new TagFilter(new BufferedReader(new FileReader(data + "/xref" + filename)));
                            try {
                                l = r.read(content);
                            } finally {
                                r.close();
                            }
                            Summary sum = summer.getSummary(new String(content, 0, l));
                            Fragment fragments[] = sum.getFragments();
                            for (int jj = 0; jj < fragments.length; ++jj) {
                                String match = fragments[jj].toString();
                                if (match.length() > 0) {
                                    if (!fragments[jj].isEllipsis()) {
                                        Hit hit = new Hit(filename, fragments[jj].toString(), "", true, alt);
                                        ret.add(hit);
                                    }
                                    hasContext = true;
                                }
                            }
                        } else {
                            OpenGrokLogger.getLogger().warning("Unknown genre: " + genre);
                            hasContext |= sourceContext.getContext(null, null, null, null, filename, tags, false, ret);
                        }
                    } catch (FileNotFoundException exp) {
                        hasContext |= sourceContext.getContext(null, null, null, null, filename, tags, false, ret);
                    }
                }
                if (historyContext != null) {
                    hasContext |= historyContext.getContext(source + filename, filename, ret);
                }
                if(!hasContext) {
                    ret.add(new Hit(filename, "...", "", false, alt));
                }
            } catch (IOException e) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Exception searching", e);
            } catch (ClassNotFoundException e) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Exception searching", e);
            }
        }
        
    }
    
    /**
     * Getter for property definition.
     *
     * @return Value of property definition.
     */
    public String getDefinition() {
        return this.definition;
    }
    
    /**
     * Setter for property definition.
     *
     * @param definition New value of property definition.
     */
    public void setDefinition(String definition) {
        this.definition = definition;
    }
    
    /**
     * Getter for property file.
     *
     * @return Value of property file.
     */
    public String getFile() {
        return this.file;
    }
    
    /**
     * Setter for property file.
     *
     * @param file New value of property file.
     */
    public void setFile(String file) {
        this.file = file;
    }
    
    /**
     * Getter for property freetext.
     *
     * @return Value of property freetext.
     */
    public String getFreetext() {
        return this.freetext;
    }
    
    /**
     * Setter for property freetext.
     *
     * @param freetext New value of property freetext.
     */
    public void setFreetext(String freetext) {
        this.freetext = freetext;
    }
    
    /**
     * Getter for property history.
     *
     * @return Value of property history.
     */
    public String getHistory() {
        return this.history;
    }
    
    /**
     * Setter for property history.
     *
     * @param history New value of property history.
     */
    public void setHistory(String history) {
        this.history = history;
    }
    
    /**
     * Getter for property symbol.
     *
     * @return Value of property symbol.
     */
    public String getSymbol() {
        return this.symbol;
    }
    
    /**
     * Setter for property symbol.
     *
     * @param symbol New value of property symbol.
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    boolean onlyFilnameSearch() {
        return sourceContext == null && historyContext == null;
    }
}
