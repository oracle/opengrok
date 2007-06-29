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
 * s enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2005 Trond Norbye.  All rights reserved.
 * Use is subject to license terms.
 */

/*
 * ident	"@(#)SearchEngine.java 1.1     06/02/22 SMI"
 */

package org.opensolaris.opengrok.search.scope;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.analysis.TagFilter;
import org.opensolaris.opengrok.search.*;

import org.opensolaris.opengrok.search.Summary.Fragment;
import org.opensolaris.opengrok.search.context.Context;
import org.opensolaris.opengrok.search.context.HistoryContext;

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
    private IndexDatabase indexDatabase;
    private Query query;
    private CompatibleAnalyser analyzer;
    private QueryParser qparser;
    private Context sourceContext;
    private HistoryContext historyContext;
    private Summarizer summer;
    private Hits hits;
    private int nhits;
    private char[] content = new char[1024*8];
    //private LineMatcher[] m;
    /**
     * Creates a new instance of SearchEngine
     */
    public SearchEngine() {
        analyzer = new CompatibleAnalyser();
        qparser = new QueryParser("full", analyzer);
        qparser.setDefaultOperator(QueryParser.AND_OPERATOR);
    }
    
    /**
     * Execute a search. Before calling this function, you must set the
     * appropriate seach critera with the set-functions.
     *
     * @return A list containg all the search results.
     */
    public int search() {
        hits = null;
        StringBuilder sb = new StringBuilder();
        
        if ((freetext != null) && (freetext.length() > 0)) {
            sb.append(freetext);
        }
        
        if ((definition != null) && (definition.length() > 0)) {
            sb.append(" defs:(");
            sb.append(definition);
            sb.append(")");
        }
        
        if ((symbol != null) && (symbol.length() > 0)) {
            sb.append(" refs:(");
            sb.append(symbol);
            sb.append(")");
        }
        
        if ((file != null) && (file.length() > 0)) {
            sb.append(" path:(");
            sb.append(file);
            sb.append(")");
        }
        
        if ((history != null) && (history.length() > 0)) {
            sb.append(" hist:(");
            sb.append(history);
            sb.append(")");
        }
        
        if (sb.length() > 0) {
            try {
                query = qparser.parse(sb.toString());
                
                IndexReader ireader = IndexReader.open(indexDatabase.getDatabase() +
                        "/index");
                Searcher searcher = new IndexSearcher(ireader);
                hits = searcher.search(query);
                nhits = hits.length();
                if (hits.length() == 0) {
                    hits = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (hits != null) {
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
            
            //m = QueryMatchers.getMatchers(query);
            return hits.length();
        }
        return 0;
    }
    
    public void more(int start, int end, List<Hit> ret) {
        for (int ii = start; ii < end; ++ii) {
            boolean alt = (ii % 2 == 0);
            boolean hasContext = false;
            try {
                Document doc = hits.doc(ii);
                String filename = doc.get("path");
                String genre = doc.get("t");
                String tags = doc.get("tags");
                
                if(sourceContext != null) {
                    try {
                        if ("p".equals(genre) && (indexDatabase.getSource() != null)) {
                            hasContext = sourceContext.getContext(new InputStreamReader(new FileInputStream(indexDatabase.getSource() +
                                    filename)), null, null, null, filename,
                                    tags, nhits > 100, ret);
                        } else if("x".equals(genre) && indexDatabase.getDatabase() != null && summer != null){
                            Reader r = new TagFilter(new BufferedReader(new FileReader(indexDatabase.getDatabase() + "/xref" + filename)));
                            int len = r.read(content);
                            Summary sum = summer.getSummary(new String(content, 0, len));
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
//                    } else if("h".equals(genre) && indexDatabase.getSource() != null && summer != null){
//                        Reader r = new TagFilter(new BufferedReader(new FileReader(srcRoot + rpath)));
//                        int len = r.read(content);
//                        out.write(summer.getSummary(new String(content, 0, len)).toString());
                        } else {
                            System.out.println(genre);
                            hasContext |= sourceContext.getContext(null, null, null, null, filename, tags, false, ret);
                        }
                    } catch (FileNotFoundException exp) {
                        hasContext |= sourceContext.getContext(null, null, null, null, filename, tags, false, ret);
                    }
                }
                if (historyContext != null) {
                    hasContext |= historyContext.getContext(indexDatabase.getSource() + filename, filename, ret);
                }
                if(!hasContext) {
                    ret.add(new Hit(filename, "...", "", false, alt));
                }
            } catch (IOException e) {
                e.printStackTrace();
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
    
    /**
     * Setter for property indexDatabase.
     *
     * @param indexDatabase New value of property indexDatabase.
     */
    public void setIndexDatabase(IndexDatabase indexDatabase) {
        this.indexDatabase = indexDatabase;
    }

    boolean onlyFilnameSearch() {
        return sourceContext == null && historyContext == null;
    }
}
