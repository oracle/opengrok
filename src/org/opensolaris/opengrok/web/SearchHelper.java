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
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions copyright (c) 2011 Jens Elkner. 
 */
package org.opensolaris.opengrok.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.spell.SuggestWord;
import org.apache.lucene.store.FSDirectory;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.CompatibleAnalyser;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.SuperIndexSearcher;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.search.Summarizer;
import org.opensolaris.opengrok.search.context.Context;
import org.opensolaris.opengrok.search.context.HistoryContext;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Working set for a search basically to factor out/separate search related
 * complexity from UI design.
 *
 * @author Jens Elkner
 * @version $Revision$
 */
public class SearchHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchHelper.class);

    /**
     * max number of words to suggest for spellcheck
     */
    public int SPELLCHECK_SUGGEST_WORD_COUNT = 5;
    /**
     * opengrok's data root: used to find the search index file
     */
    public File dataRoot;
    /**
     * context path, i.e. the applications context path (usually /source) to use
     * when generating a redirect URL
     */
    public String contextPath;
    /**
     * piggyback: if {@code true}, files in opengrok's data directory are
     * gzipped compressed.
     */
    public boolean compressed;
    /**
     * piggyback: the source root directory.
     */
    public File sourceRoot;
    /**
     * piggyback: the eftar filereader to use.
     */
    public EftarFileReader desc;
    /**
     * the result cursor start index, i.e. where to start displaying results
     */
    public int start;
    /**
     * max. number of result items to show
     */
    public int maxItems;
    /**
     * the QueryBuilder used to create the query
     */
    public QueryBuilder builder;
    /**
     * the order used for ordering query results
     */
    public SortOrder order;
    /**
     * if {@code true} multi-threaded search will be used.
     */
    public boolean parallel;
    /**
     * Indicate, whether this is search from a cross reference. If {@code true}
     * {@link #executeQuery()} sets {@link #redirect} if certain conditions are
     * met.
     */
    public boolean isCrossRefSearch;
    /**
     * if not {@code null}, the consumer should redirect the client to a
     * separate result page denoted by the value of this field. Automatically
     * set via {@link #prepareExec(SortedSet)} and {@link #executeQuery()}.
     */
    public String redirect;
    /**
     * if not {@code null}, the UI should show this error message and stop
     * processing the search. Automatically set via
     * {@link #prepareExec(SortedSet)} and {@link #executeQuery()}.
     */
    public String errorMsg;
    /**
     * the searcher used to open/search the index. Automatically set via
     * {@link #prepareExec(SortedSet)}.
     */
    public IndexSearcher searcher;
    /**
     * If performing multi-project search, the indexSearcher objects will be
     * tracked by the indexSearcherMap so that they can be properly released
     * once the results are read.
     */
    private final ArrayList<SuperIndexSearcher> searcherList = new ArrayList<>();
    /**
     * close IndexReader associated with searches on destroy()
     */
    private Boolean closeOnDestroy;
    /**
     * list of docs which result from the executing the query
     */
    public ScoreDoc[] hits;
    /**
     * total number of hits
     */
    public int totalHits;
    /**
     * the query created by the used {@link QueryBuilder} via
     * {@link #prepareExec(SortedSet)}.
     */
    public Query query;
    /**
     * the Lucene sort instruction based on {@link #order} created via
     * {@link #prepareExec(SortedSet)}.
     */
    protected Sort sort;
    /**
     * the spellchecker object
     */
    protected DirectSpellChecker checker;
    /**
     * projects to use to setup indexer searchers. Usually setup via
     * {@link #prepareExec(SortedSet)}.
     */
    public SortedSet<String> projects;
    /**
     * opengrok summary context. Usually created via {@link #prepareSummary()}.
     */
    public Context sourceContext = null;
    /**
     * result summarizer usually created via {@link #prepareSummary()}.
     */
    public Summarizer summarizer = null;
    /**
     * history context usually created via {@link #prepareSummary()}.
     */
    public HistoryContext historyContext;
    
    /**
     * display last edited date of a file in search results
     */
    public boolean lastEditedDisplayMode = true;
    /**
     * Default query parse error message prefix
     */
    public static final String PARSE_ERROR_MSG = "Unable to parse your query: ";

    /**
     * User readable description for file types. Only those listed in
     * fileTypeDescription will be shown to the user.
     *
     * Returns a set of file type descriptions to be used for a search form.
     *
     * @return Set of tuples with file type and description.
     */
    public static Set<Map.Entry<String, String>> getFileTypeDescriptions() {
        return AnalyzerGuru.getfileTypeDescriptions().entrySet();
    }

    File indexDir;

    /**
     * Create the searcher to use wrt. to currently set parameters and the given
     * projects. Does not produce any {@link #redirect} link. It also does
     * nothing if {@link #redirect} or {@link #errorMsg} have a
     * none-{@code null} value.
     * <p>
     * Parameters which should be populated/set at this time: <ul>
     * <li>{@link #builder}</li> <li>{@link #dataRoot}</li>
     * <li>{@link #order} (falls back to relevance if unset)</li>
     * <li>{@link #parallel} (default: false)</li> </ul> Populates/sets: <ul>
     * <li>{@link #query}</li> <li>{@link #searcher}</li> <li>{@link #sort}</li>
     * <li>{@link #projects}</li> <li>{@link #errorMsg} if an error occurs</li>
     * </ul>
     *
     * @param projects project to use query. If empty, a no-project setup
     * is assumed (i.e. DATA_ROOT/index will be used instead of possible
     * multiple DATA_ROOT/$project/index).
     * @return this instance
     */
    public SearchHelper prepareExec(SortedSet<String> projects) {
        if (redirect != null || errorMsg != null) {
            return this;
        }
        // the Query created by the QueryBuilder
        try {
            indexDir = new File(dataRoot, IndexDatabase.INDEX_DIR);
            query = builder.build();
            if (projects == null) {
                errorMsg = "No project selected!";
                return this;
            }
            this.projects = projects;
            if (projects.isEmpty()) {
                // no project setup
                FSDirectory dir = FSDirectory.open(indexDir.toPath());
                searcher = new IndexSearcher(DirectoryReader.open(dir));
                closeOnDestroy = true;
            } else {
                // We use MultiReader even for single project. This should
                // not matter given that MultiReader is just a cheap wrapper
                // around set of IndexReader objects.
                closeOnDestroy = false;
                searcher = new IndexSearcher(RuntimeEnvironment.getInstance().
                    getMultiReader(projects, searcherList));
            }

            // TODO check if below is somehow reusing sessions so we don't
            // requery again and again, I guess 2min timeout sessions could be
            // usefull, since you click on the next page within 2mins, if not,
            // then wait ;)
            // Most probably they are not reused. SearcherLifetimeManager might help here.
            switch (order) {
                case LASTMODIFIED:
                    sort = new Sort(new SortField(QueryBuilder.DATE, SortField.Type.STRING, true));
                    break;
                case BY_PATH:
                    sort = new Sort(new SortField(QueryBuilder.FULLPATH, SortField.Type.STRING));
                    break;
                default:
                    sort = Sort.RELEVANCE;
                    break;
            }
            checker = new DirectSpellChecker();
        } catch (ParseException e) {
            errorMsg = PARSE_ERROR_MSG + e.getMessage();
        } catch (FileNotFoundException e) {
//          errorMsg = "Index database(s) not found: " + e.getMessage();
            errorMsg = "Index database(s) not found.";
        } catch (IOException e) {
            errorMsg = e.getMessage();
        }
        return this;
    }

    /**
     * Start the search prepared by {@link #prepareExec(SortedSet)}. It does
     * nothing if {@link #redirect} or {@link #errorMsg} have a
     * none-{@code null} value.
     * <p>
     * Parameters which should be populated/set at this time: <ul> <li>all
     * fields required for and populated by
     * {@link #prepareExec(SortedSet)})</li> <li>{@link #start} (default:
     * 0)</li> <li>{@link #maxItems} (default: 0)</li>
     * <li>{@link #isCrossRefSearch} (default: false)</li> </ul> Populates/sets:
     * <ul> <li>{@link #hits} (see {@link TopFieldDocs#scoreDocs})</li>
     * <li>{@link #totalHits} (see {@link TopFieldDocs#totalHits})</li>
     * <li>{@link #contextPath}</li> <li>{@link #errorMsg} if an error
     * occurs</li> <li>{@link #redirect} if certain conditions are met</li>
     * </ul>
     *
     * @return this instance
     */
    public SearchHelper executeQuery() {
        if (redirect != null || errorMsg != null) {
            return this;
        }
        try {
            TopFieldDocs fdocs = searcher.search(query, start + maxItems, sort);
            totalHits = fdocs.totalHits;
            hits = fdocs.scoreDocs;
            // Bug #3900: Check if this is a search for a single term, and that
            // term is a definition. If that's the case, and we only have one match,
            // we'll generate a direct link instead of a listing.
            boolean isSingleDefinitionSearch
                    = (query instanceof TermQuery) && (builder.getDefs() != null);

            // Attempt to create a direct link to the definition if we search for
            // one single definition term AND we have exactly one match AND there
            // is only one definition of that symbol in the document that matches.
            boolean uniqueDefinition = false;
            if (isSingleDefinitionSearch && hits != null && hits.length == 1) {
                Document doc = searcher.doc(hits[0].doc);
                if (doc.getField(QueryBuilder.TAGS) != null) {
                    byte[] rawTags = doc.getField(QueryBuilder.TAGS).binaryValue().bytes;
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
                        + Util.URIEncodePath(searcher.doc(hits[0].doc).get(QueryBuilder.PATH))
                        + '#' + Util.URIEncode(((TermQuery) query).getTerm().text());
            }
        } catch (BooleanQuery.TooManyClauses e) {
            errorMsg = "Too many results for wildcard!";
        } catch (IOException | ClassNotFoundException e) {
            errorMsg = e.getMessage();
        }
        return this;
    }
    private static final Pattern TABSPACE = Pattern.compile("[\t ]+");

    private void getSuggestion(Term term, IndexReader ir,
            List<String> result) throws IOException {
        if (term == null) {
            return;
        }
        String[] toks = TABSPACE.split(term.text(), 0);
        for (String tok : toks) {
            //TODO below seems to be case insensitive ... for refs/defs this is bad
            SuggestWord[] words = checker.suggestSimilar(new Term(term.field(), tok),
                SPELLCHECK_SUGGEST_WORD_COUNT, ir, SuggestMode.SUGGEST_ALWAYS);
            for (SuggestWord w : words) {
                result.add(w.string);
            }
        }
    }

    /**
     * If a search did not return a hit, one may use this method to obtain
     * suggestions for a new search.
     *
     * <p>
     * Parameters which should be populated/set at this time: <ul>
     * <li>{@link #projects}</li> <li>{@link #dataRoot}</li>
     * <li>{@link #builder}</li> </ul>
     *
     * @return a possible empty list of suggestions.
     */
    public List<Suggestion> getSuggestions() {
        if (projects == null) {
            return new ArrayList<>(0);
        }
        String name[];
        if (projects.isEmpty()) {
            name = new String[]{"/"};
        } else if (projects.size() == 1) {
            name = new String[]{projects.first()};
        } else {
            name = new String[projects.size()];
            int ii = 0;
            for (String proj : projects) {
                name[ii++] = proj;
            }
        }
        List<Suggestion> res = new ArrayList<>();
        List<String> dummy = new ArrayList<>();
        FSDirectory dir;
        IndexReader ir = null;
        Term t;
        for (String proj : name) {
            Suggestion s = new Suggestion(proj);
            try {
                if (!closeOnDestroy) {
                    SuperIndexSearcher searcher = RuntimeEnvironment.getInstance().getIndexSearcher(proj);
                    searcherList.add(searcher);
                    ir = searcher.getIndexReader();
                } else {
                    dir = FSDirectory.open(new File(indexDir, proj).toPath());
                    ir = DirectoryReader.open(dir);
                }
                if (builder.getFreetext() != null
                        && !builder.getFreetext().isEmpty()) {
                    t = new Term(QueryBuilder.FULL, builder.getFreetext());
                    getSuggestion(t, ir, dummy);
                    s.freetext = dummy.toArray(new String[dummy.size()]);
                    dummy.clear();
                }
                if (builder.getRefs() != null && !builder.getRefs().isEmpty()) {
                    t = new Term(QueryBuilder.REFS, builder.getRefs());
                    getSuggestion(t, ir, dummy);
                    s.refs = dummy.toArray(new String[dummy.size()]);
                    dummy.clear();
                }
                if (builder.getDefs() != null && !builder.getDefs().isEmpty()) {
                    t = new Term(QueryBuilder.DEFS, builder.getDefs());
                    getSuggestion(t, ir, dummy);
                    s.defs = dummy.toArray(new String[dummy.size()]);
                    dummy.clear();
                }
                //TODO suggest also for path and history?
                if ((s.freetext != null && s.freetext.length > 0)
                        || (s.defs != null && s.defs.length > 0)
                        || (s.refs != null && s.refs.length > 0)) {
                    res.add(s);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Got exception while getting "
                        + "spelling suggestions: ", e);
            } finally {
                if (ir != null && closeOnDestroy) {
                    try {
                        ir.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING, "Got exception while "
                                + "getting spelling suggestions: ", ex);
                    }
                }
            }
        }
        return res;
    }

    /**
     * Prepare the fields to support printing a full blown summary. Does nothing
     * if {@link #redirect} or {@link #errorMsg} have a none-{@code null} value.
     *
     * <p>
     * Parameters which should be populated/set at this time: <ul>
     * <li>{@link #query}</li> <li>{@link #builder}</li> </ul> Populates/sets:
     * Otherwise the following fields are set (includes {@code null}): <ul>
     * <li>{@link #sourceContext}</li> <li>{@link #summarizer}</li>
     * <li>{@link #historyContext}</li> </ul>
     *
     * @return this instance.
     */
    public SearchHelper prepareSummary() {
        if (redirect != null || errorMsg != null) {
            return this;
        }
        try {
            sourceContext = new Context(query, builder.getQueries());
            summarizer = new Summarizer(query, new CompatibleAnalyser());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Summarizer: {0}", e.getMessage());
        }
        try {
            historyContext = new HistoryContext(query);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "HistoryContext: {0}", e.getMessage());
        }
        return this;
    }

    /**
     * Free any resources associated with this helper (that includes closing the
     * used {@link #searcher} in case of no-project setup).
     */
    public void destroy() {
        if (searcher != null && closeOnDestroy) {
            IOUtils.close(searcher.getIndexReader());
        }

        for (SuperIndexSearcher is : searcherList) {
            try {
                is.getSearcherManager().release(is);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "cannot release indexSearcher", ex);
            }
        }
    }
}