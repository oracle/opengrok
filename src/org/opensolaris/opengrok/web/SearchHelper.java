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
 * Copyright (c) 2011 Jens Elkner.
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiSearcher;
import org.apache.lucene.search.ParallelMultiSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.FSDirectory;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.search.Summarizer;
import org.opensolaris.opengrok.search.context.Context;
import org.opensolaris.opengrok.search.context.HistoryContext;

/**
 * Working set for a search basically to factor out/separate search related
 * complexity from UI design.
 *
 * @author  Jens Elkner
 * @version $Revision$
 */
public class SearchHelper {

    /** opengrok's data root: used to find the search index file */
    public File dataRoot;
    /** context path, i.e. the applications context path (usually /source) to
     * use when generating a redirect URL */
    public String contextPath;
    /** piggyback: if {@code true}, files in opengrok's data directory are
     * gzipped compressed. */
    public boolean compressed;
    /** piggyback: the source root directory. */
    public File sourceRoot;
    /** piggyback: the eftar filereader to use. */
    public EftarFileReader desc;
    /** the result cursor start index, i.e. where to start displaying results */
    public int start;
    /** max. number of result items to show */
    public int maxItems;
    /** the QueryBuilder used to create the query */
    public QueryBuilder builder;
    /** the order to use to ordery query results */
    public SortOrder order;
    /** if {@code true} a {@link ParallelMultiSearcher} will be used instead of
     * a {@link MultiSearcher}. */
    public boolean parallel;
    /** Indicate, whether this is search from a cross reference. If {@code true}
     * {@link #executeQuery()} sets {@link #redirect} if certain conditions are
     * met. */
    public boolean isCrossRefSearch;
    /** if not {@code null}, the consumer should redirect the client to a
     * separate result page denoted by the value of this field. Automatically
     * set via {@link #prepareExec(TreeSet)} and {@link #executeQuery()}. */
    public String redirect;
    /** if not {@code null}, the UI should show this error message and stop
     * processing the search. Automatically set via {@link #prepareExec(TreeSet)}
     * and {@link #executeQuery()}.*/
    public String errorMsg;
    /** the searcher used to open/search the index. Automatically set via
     * {@link #prepareExec(TreeSet)}. */
    public Searcher searcher;
    /** list of docs which result from the executing the query */
    public ScoreDoc[] hits;
    /** total number of hits */
    public int totalHits;
    /** the query created by the used {@link QueryBuilder} via
     * {@link #prepareExec(TreeSet)}. */
    public Query query;
    /** the lucene sort instruction based on {@link #order} created via
     * {@link #prepareExec(TreeSet)}. */
    protected Sort sort;
    /** projects to use to setup indexer searchers. Usually setup via
     * {@link #prepareExec(TreeSet)}. */
    public SortedSet<String> projects;
    /** opengrok summary context. Usually created via {@link #prepareSummary()}. */
    public Context sourceContext = null;
    /** result summarizer usually created via {@link #prepareSummary()}. */
    public Summarizer summerizer = null;
    /** history context usually created via {@link #prepareSummary()}.*/
    public HistoryContext historyContext;
    /** Default query parse error message prefix */
    public static final String PARSE_ERROR_MSG = "Unable to parse your query: ";

    /**
     * Create the searcher to use wrt. to currently set parameters and the given
     * projects. Does not produce any {@link #redirect} link. It also does
     * nothing if {@link #redirect} or {@link #errorMsg} have a none-{@code null}
     * value.
     * <p>
     * Parameters which should be populated/set at this time:
     * <ul>
     * <li>{@link #builder}</li>
     * <li>{@link #dataRoot}</li>
     * <li>{@link #order} (falls back to relevance if unset)</li>
     * <li>{@link #parallel} (default: false)</li>
     * </ul>
     * Populates/sets:
     * <ul>
     * <li>{@link #query}</li>
     * <li>{@link #searcher}</li>
     * <li>{@link #sort}</li>
     * <li>{@link #projects}</li>
     * <li>{@link #errorMsg} if an error occurs</li>
     * </ul>
     *
     * @param projects  project to use query. If empty, a none-project opengrok
     *  setup is assumed (i.e. DATA_ROOT/index will be used instead of possible
     *  multiple DATA_ROOT/$project/index).
     * @return this instance
     */
    public SearchHelper prepareExec(TreeSet<String> projects) {
        if (redirect != null || errorMsg != null) {
            return this;
        }
        // the Query created by the QueryBuilder
        try {
            query = builder.build();
            if (projects == null) {
                errorMsg = "No project selected!";
                return this;
            }
            this.projects = projects;
            File indexDir = new File(dataRoot, "index");
            if (projects.isEmpty()) {
                //no project setup
                FSDirectory dir = FSDirectory.open(indexDir);
                searcher = new IndexSearcher(dir);
            } else if (projects.size() == 1) {
                // just 1 project selected
                FSDirectory dir =
                        FSDirectory.open(new File(indexDir, projects.first()));
                searcher = new IndexSearcher(dir);
            } else {
                //more projects
                IndexSearcher[] searchables = new IndexSearcher[projects.size()];
                int ii = 0;
                //TODO might need to rewrite to Project instead of
                // String , need changes in projects.jspf too
                for (String proj : projects) {
                    FSDirectory dir = FSDirectory.open(new File(indexDir, proj));
                    searchables[ii++] = new IndexSearcher(dir);
                }
                searcher = parallel
                        ? new ParallelMultiSearcher(searchables)
                        : new MultiSearcher(searchables);
            }
            // TODO check if below is somehow reusing sessions so we don't
            // requery again and again, I guess 2min timeout sessions could be
            // usefull, since you click on the next page within 2mins, if not,
            // then wait ;)
            switch (order) {
                case LASTMODIFIED:
                    sort = new Sort(new SortField("date", SortField.STRING, true));
                    break;
                case BY_PATH:
                    sort = new Sort(new SortField("fullpath", SortField.STRING));
                    break;
                default:
                    sort = Sort.RELEVANCE;
                    break;
            }
        } catch (ParseException e) {
            errorMsg = "Unable to parse your query: " + e.getMessage();
        } catch (FileNotFoundException e) {
//          errorMsg = "Index database(s) not found: " + e.getMessage();
            errorMsg = "Index database(s) not found.";
        } catch (Exception e) {
            errorMsg = e.getMessage();
        }
        return this;
    }

    /**
     * Start the search prepared by {@link #prepareExec(TreeSet)}.
     * It does nothing if {@link #redirect} or {@link #errorMsg} have a
     * none-{@code null} value.
     * <p>
     * Parameters which should be populated/set at this time:
     * <ul>
     * <li>all fields required for and populated by {@link #prepareExec(TreeSet)})</li>
     * <li>{@link #start} (default: 0)</li>
     * <li>{@link #maxItems} (default: 0)</li>
     * <li>{@link #isCrossRefSearch} (default: false)</li>
     * </ul>
     * Populates/sets:
     * <ul>
     * <li>{@link #hits} (see {@link TopFieldDocs#scoreDocs})</li>
     * <li>{@link #totalHits} (see {@link TopFieldDocs#totalHits})</li>
     * <li>{@link #contextPath}</li>
     * <li>{@link #errorMsg} if an error occurs</li>
     * <li>{@link #redirect} if certain conditions are met</li>
     * </ul>
     * @return this instance
     */
    public SearchHelper executeQuery() {
        if (redirect != null || errorMsg != null) {
            return this;
        }
        try {
            TopFieldDocs fdocs = searcher.search(query, null, start + maxItems, sort);
            totalHits = fdocs.totalHits;
            hits = fdocs.scoreDocs;
            // Bug #3900: Check if this is a search for a single term, and that
            // term is a definition. If that's the case, and we only have one match,
            // we'll generate a direct link instead of a listing.
            boolean isSingleDefinitionSearch =
                    (query instanceof TermQuery) && (builder.getDefs() != null);

            // Attempt to create a direct link to the definition if we search for
            // one single definition term AND we have exactly one match AND there
            // is only one definition of that symbol in the document that matches.
            boolean uniqueDefinition = false;
            if (isSingleDefinitionSearch && hits != null && hits.length == 1) {
                Document doc = searcher.doc(hits[0].doc);
                if (doc.getFieldable("tags") != null) {
                    byte[] rawTags = doc.getFieldable("tags").getBinaryValue();
                    Definitions tags = Definitions.deserialize(rawTags);
                    String symbol = ((TermQuery) query).getTerm().text();
                    if (tags.occurrences(symbol) == 1) {
                        uniqueDefinition = true;
                    }
                }
            }
            // @TODO fix me. I should try to figure out where the exact hit is
            // instead of returning a page with just _one_ entry in....
            if (uniqueDefinition && hits != null && hits.length > 0 && isCrossRefSearch) {
                redirect = contextPath + Prefix.XREF_P
                        + Util.URIEncodePath(searcher.doc(hits[0].doc).get("path"))
                        + '#' + Util.URIEncode(((TermQuery) query).getTerm().text());
            }
        } catch (BooleanQuery.TooManyClauses e) {
            errorMsg = "Too many results for wildcard!";
        } catch (Exception e) {
            errorMsg = e.getMessage();
        }
        return this;
    }
    private static final Pattern TABSPACE = Pattern.compile("[\t ]+");

    private static void getSuggestion(String term, SpellChecker checker,
            List<String> result) throws IOException {
        if (term == null) {
            return;
        }
        String[] toks = TABSPACE.split(term, 0);
        for (int j = 0; j < toks.length; j++) {
            if (toks[j].length() <= 3) {
                continue;
            }
            result.addAll(Arrays.asList(checker.suggestSimilar(toks[j].toLowerCase(), 5)));
        }
    }

    /**
     * If a search did not return a hit, one may use this method to obtain
     * suggestions for a new search.
     *
     * <p>
     * Parameters which should be populated/set at this time:
     * <ul>
     * <li>{@link #projects}</li>
     * <li>{@link #dataRoot}</li>
     * <li>{@link #builder}</li>
     * </ul>
     * @return a possible empty list of sugeestions.
     */
    public List<Suggestion> getSuggestions() {
        if (projects == null) {
            return new ArrayList<Suggestion>(0);
        }
        File[] spellIndex = null;
        if (projects.isEmpty()) {
            spellIndex = new File[]{new File(dataRoot, "spellIndex")};
        } else if (projects.size() == 1) {
            spellIndex = new File[]{
                new File(dataRoot, "spellIndex/" + projects.first())
            };
        } else {
            spellIndex = new File[projects.size()];
            int ii = 0;
            File indexDir = new File(dataRoot, "spellIndex");
            for (String proj : projects) {
                spellIndex[ii++] = new File(indexDir, proj);
            }
        }
        List<Suggestion> res = new ArrayList<Suggestion>();
        List<String> dummy = new ArrayList<String>();
        for (int idx = 0; idx < spellIndex.length; idx++) {
            if (!spellIndex[idx].exists()) {
                continue;
            }
            FSDirectory spellDirectory = null;
            SpellChecker checker = null;
            Suggestion s = new Suggestion(spellIndex[idx].getName());
            try {
                spellDirectory = FSDirectory.open(spellIndex[idx]);
                checker = new SpellChecker(spellDirectory);
                getSuggestion(builder.getFreetext(), checker, dummy);
                s.freetext = dummy.toArray(new String[dummy.size()]);
                dummy.clear();
                getSuggestion(builder.getRefs(), checker, dummy);
                s.refs = dummy.toArray(new String[dummy.size()]);
                dummy.clear();
                // TODO it seems the only true spellchecker is for
                // below field, see IndexDatabase
                // createspellingsuggestions ...
                getSuggestion(builder.getDefs(), checker, dummy);
                s.defs = dummy.toArray(new String[dummy.size()]);
                dummy.clear();
                if (s.freetext.length > 0 || s.defs.length > 0 || s.refs.length > 0) {
                    res.add(s);
                }
            } catch (IOException e) {
                /* ignore */
            } finally {
                if (spellDirectory != null) {
                    spellDirectory.close();
                }
                if (checker != null) {
                    try {
                        checker.close();
                    } catch (Exception x) { /* ignore */ }
                }
            }
        }
        return res;
    }

    /**
     * Prepare the fields to support printing a fullblown summary. Does nothing
     * if {@link #redirect} or {@link #errorMsg} have a none-{@code null} value.
     *
     * <p>
     * Parameters which should be populated/set at this time:
     * <ul>
     * <li>{@link #query}</li>
     * <li>{@link #builder}</li>
     * </ul>
     * Populates/sets:
     * Otherwise the following fields are set (includes {@code null}):
     * <ul>
     * <li>{@link #sourceContext}</li>
     * <li>{@link #summerizer}</li>
     * <li>{@link #historyContext}</li>
     * </ul>
     *
     * @return this instance.
     */
    public SearchHelper prepareSummary() {
        if (redirect != null || errorMsg != null) {
            return this;
        }
        try {
            sourceContext = new Context(query, builder.getQueries());
            summerizer = new Summarizer(query, new CompatibleAnalyser());
        } catch (Exception e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Summerizer: {0}", e.getMessage());
        }
        try {
            historyContext = new HistoryContext(query);
        } catch (Exception e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "HistoryContext: {0}", e.getMessage());
        }
        return this;
    }

    /**
     * Free any resources associated with this helper (that includes closing
     * the used {@link #searcher}).
     */
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (IOException e) {
                /* ignore */
            }
        }
    }
}
