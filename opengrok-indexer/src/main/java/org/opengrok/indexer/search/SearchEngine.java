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
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollectorManager;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuperIndexSearcher;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.Summary.Fragment;
import org.opengrok.indexer.search.context.Context;
import org.opengrok.indexer.search.context.HistoryContext;
import org.opengrok.indexer.util.Statistics;
import org.opengrok.indexer.util.TandemPath;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.SortOrder;

/**
 * This is an encapsulation of the details on how to search in the index database(s).
 * This is used for searching via the REST API.
 * <p>
 * Authorization is <b>not</b> enforced here, this has to be done by the caller by filtering out the projects
 * passed to {@link #search(List)}.
 *
 * @author Trond Norbye 2005
 * @author Lubos Kosco - upgrade to lucene 3.x, 4.x, 5.x
 */
public class SearchEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngine.class);

    /**
     * Message text used when logging exceptions thrown when searching.
     */
    private static final String SEARCH_EXCEPTION_MSG = "Exception searching";
    /**
     * Version of Lucene index common for the whole application.
     */
    public static final Version LUCENE_VERSION = Version.LATEST;
    public static final String LUCENE_VERSION_HELP = LUCENE_VERSION.major + "_" + LUCENE_VERSION.minor + "_" + LUCENE_VERSION.bugfix;
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
     * Holds value of property type.
     */
    private String type;
    /**
     * Holds value of property sort.
     */
    private SortOrder sortOrder;
    /**
     * Holds value of property maxHitsPerFile.
     * 0 means unlimited (default).
     */
    private int maxHitsPerFile;

    private Query query;
    private QueryBuilder queryBuilder;
    private final CompatibleAnalyser analyzer = new CompatibleAnalyser();
    private Context sourceContext;
    private HistoryContext historyContext;
    private Summarizer summarizer;

    // internal structures to hold the results from Lucene
    private final List<Document> docs;
    private final int maxDocs;
    int totalHits = 0;
    private ScoreDoc[] hits;

    private String source;
    private String data;

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private IndexSearcher searcher;
    private final ArrayList<SuperIndexSearcher> searcherList = new ArrayList<>();

    /**
     * Creates a new instance of SearchEngine which collects at most {@code maxDocs} documents,
     * so that heap usage stays bounded on queries matching many documents.
     * {@link #getTotalHits()} still reports the full match count for pagination.
     *
     * @param maxDocs positive maximum number of documents to collect
     * @throws IllegalArgumentException if {@code maxDocs} is not positive
     */
    public SearchEngine(int maxDocs) {
        if (maxDocs <= 0) {
            throw new IllegalArgumentException("maxDocs must be positive: " + maxDocs);
        }
        docs = new ArrayList<>();
        this.maxDocs = maxDocs;
    }

    /**
     * Creates a new instance of SearchEngine collecting at most the configured
     * hits per page times the number of cache pages.
     */
    public SearchEngine() {
        this(env.getHitsPerPage() * env.getCachePages());
    }

    /**
     * Create a QueryBuilder using the fields that have been set on this instance.
     *
     * @return a query builder
     */
    private QueryBuilder createQueryBuilder() {
        return new QueryBuilder()
                .setFreetext(freetext)
                .setDefs(definition)
                .setRefs(symbol)
                .setPath(file)
                .setHist(history)
                .setType(type);
    }

    /**
     * @return whether the query built from the set-parameters is valid
     */
    public boolean isValidQuery() {
        boolean ret;
        try {
            query = createQueryBuilder().build();
            ret = (query != null);
        } catch (ParseException e) {
            ret = false;
        }

        return ret;
    }

    /**
     * Search one index. This is used if no projects are set up.
     * @throws IOException when index could not be read
     */
    private void searchSingleDatabase() throws IOException {
        SuperIndexSearcher superIndexSearcher = env.getSuperIndexSearcher("");
        searcherList.add(superIndexSearcher);
        searcher = superIndexSearcher;
        searchIndex(superIndexSearcher);
    }

    /**
     * Perform search on multiple indexes.
     * @param projectList list of projects to search
     * @throws IOException when some index could not be read
     */
    private void searchMultiDatabase(List<Project> projectList) throws IOException {
        SortedSet<String> projectNames = projectList.stream().map(Project::getName).
                collect(Collectors.toCollection(TreeSet::new));

        // We use MultiReader even for single project. This should not matter given that MultiReader is just
        // a cheap wrapper around set of IndexReader objects.
        searcher = env.getIndexSearcherFactory().newSearcher(env.getMultiReader(projectNames, searcherList));
        searchIndex(searcher);
    }

    private void searchIndex(IndexSearcher searcher) throws IOException {
        Statistics stat = new Statistics();
        // The collector managers eagerly allocate a priority queue of the requested size, hence
        // the index-size cap, mirroring IndexSearcher#search(Query, int).
        final int numHits = Math.min(maxDocs, Math.max(1, searcher.getIndexReader().maxDoc()));
        TopDocs topDocs;
        Sort luceneSort = getSort();
        if (luceneSort == null) {
            topDocs = searcher.search(query, new TopScoreDocCollectorManager(numHits, Integer.MAX_VALUE));
        } else {
            topDocs = searcher.search(query, new TopFieldCollectorManager(luceneSort, numHits, Integer.MAX_VALUE));
        }
        hits = topDocs.scoreDocs;
        totalHits = (int) topDocs.totalHits.value;

        stat.report(LOGGER, Level.FINEST, "search via SearchEngine done",
                "search.latency", new String[]{"category", "engine",
                        "outcome", totalHits > 0 ? "success" : "empty"});

        StoredFields storedFields = searcher.storedFields();
        for (ScoreDoc hit : hits) {
            int docId = hit.doc;
            Document d = storedFields.document(docId);
            docs.add(d);
        }
    }

    private @Nullable Sort getSort() {
        Sort luceneSort = null;
        if (getSortOrder() == SortOrder.LASTMODIFIED) {
            luceneSort = new Sort(new SortField(QueryBuilder.DATE, SortField.Type.STRING, true));
        } else if (getSortOrder() == SortOrder.BY_PATH) {
            luceneSort = new Sort(new SortField(QueryBuilder.FULLPATH, SortField.Type.STRING));
        }
        return luceneSort;
    }

    /**
     * Gets the string representation of search query used in {@link #search()} if it was called.
     * @return defined instance or {@code null}
     */
    @VisibleForTesting
    @Nullable
    public String getQuery() {
        return query != null ? query.toString() : null;
    }

    /**
     * Gets the instance from {@code search(...)} if it was called.
     * @return defined instance or {@code null}
     */
    public Query getQueryObject() {
        return query;
    }

    /**
     * Gets the builder from {@code search(...)} if it was called.
     * <p>
     * (Modifying the builder will have no effect on this {@link SearchEngine}.)
     * @return defined instance or {@code null}
     */
    @VisibleForTesting
    @Nullable
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Gets the searcher from {@code search(...)} if it was called.
     * @return defined instance or {@code null}
     */
    @VisibleForTesting
    @Nullable
    public IndexSearcher getSearcher() {
        return searcher;
    }

    /**
     * Execute a search.
     * <p>
     * Before calling this function, you must set the appropriate search criteria with the set-functions.
     * Note that this search collects at most {@code maxDocs} documents (see {@link #SearchEngine(int)}).
     * <p>
     * Must be eventually followed by call to {@link #destroy()} so that
     * the {@code IndexSearcher} objects are properly freed.
     *
     * @return number of collected hits, safe to pass to {@link #results(int, int, List)};
     * see {@link #getTotalHits()} for the total match count
     */
    @VisibleForTesting
    public int search() {
        return search(env.hasProjects() ? env.getProjectList() : new ArrayList<>());
    }

    /**
     * Execute a search on projects or root file.
     * <p>
     * Before calling this function, you must set the appropriate search criteria with the set-functions.
     * Note that this search collects at most {@code maxDocs} documents (see {@link #SearchEngine(int)}).
     * <p>
     * If the {@code projects} parameter is an empty list, it tries to search in {@code searchSingleDatabase}
     * with root set to the {@code root} parameter.
     * <p>
     * Must be eventually followed by call to {@link #destroy()} so that
     * the {@code IndexSearcher} objects are properly freed.
     *
     * @param projects projects to search
     * @return number of collected hits, safe to pass to {@link #results(int, int, List)};
     * see {@link #getTotalHits()} for the total match count
     */
    public int search(List<Project> projects) {
        source = env.getSourceRootPath();
        data = env.getDataRootPath();
        docs.clear();

        QueryBuilder newBuilder = createQueryBuilder();
        try {
            query = newBuilder.build();
            if (query != null) {
                if (projects.isEmpty()) {
                    searchSingleDatabase();
                } else {
                    searchMultiDatabase(projects);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, SEARCH_EXCEPTION_MSG, e);
        }

        if (!docs.isEmpty()) {
            sourceContext = null;
            summarizer = null;
            try {
                sourceContext = new Context(query, newBuilder);
                if (sourceContext.isEmpty()) {
                    sourceContext = null;
                }
                summarizer = new Summarizer(query, analyzer);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "An error occurred while creating summary", e);
            }

            historyContext = null;
            try {
                historyContext = new HistoryContext(query);
                if (historyContext.isEmpty()) {
                    historyContext = null;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "An error occurred while getting history context", e);
            }
        }
        int count = hits == null ? 0 : hits.length;
        queryBuilder = newBuilder;
        return count;
    }

    /**
     * Gets the queried score docs from {@code search(...)} if it was called.
     * @return a defined instance if the query succeeded, or {@code null}
     */
    @VisibleForTesting
    public ScoreDoc[] scoreDocs() {
        return hits;
    }

    /**
     * Gets the document of the specified {@code docId} from {@code search(...)} if it was called.
     *
     * @param docId document ID
     * @return a defined instance if a query succeeded
     * @throws java.io.IOException if an error occurs obtaining the Lucene document by ID
     */
    @VisibleForTesting
    public Document doc(int docId) throws IOException {
        if (searcher == null) {
            throw new IllegalStateException("search(...) did not succeed");
        }
        return searcher.storedFields().document(docId);
    }

    /**
     * Get results by going through the documents from the search hits and grabbing the context therein.
     * This involves reading various document fields as well as file contents from the respective files
     * under source root.
     * If no search was started before, no results are returned.
     * Only documents collected by {@code search(...)} are available; {@code endDocIndex} is clamped
     * to the collected count, i.e. the value {@code search(...)} returned.
     *
     * @param startDocIndex start index of the hit list
     * @param endDocIndex end index of the hit list
     * @param ret output argument that contains list of results from start to end or null/empty if no search was started
     */
    public void results(int startDocIndex, int endDocIndex, @NotNull List<Hit> ret) {
        ret.clear();

        // return if no search() was done
        if (hits == null || (endDocIndex < startDocIndex)) {
            return;
        }

        extractResults(startDocIndex, Math.min(endDocIndex, docs.size()), ret);
    }

    private void extractResults(int startDocIndex, int endDocIndex, @NotNull List<Hit> ret) {
        //TODO generation of ret(results) could be cached and consumers of engine would just print them in whatever
        // form they need, this way we could get rid of docs
        // the only problem is that count of docs is usually smaller than number of results
        for (int ii = startDocIndex; ii < endDocIndex; ++ii) {
            boolean alt = (ii % 2 == 0);
            boolean hasContext = false;
            try {
                Document doc = docs.get(ii);
                String filename = doc.get(QueryBuilder.PATH);

                AbstractAnalyzer.Genre genre = AbstractAnalyzer.Genre.get(doc.get(QueryBuilder.T));
                Definitions tags = null;
                IndexableField tagsField = doc.getField(QueryBuilder.TAGS);
                if (tagsField != null) {
                    tags = Definitions.deserialize(tagsField.binaryValue().bytes);
                }
                Scopes scopes = null;
                IndexableField scopesField = doc.getField(QueryBuilder.SCOPES);
                if (scopesField != null) {
                    scopes = Scopes.deserialize(scopesField.binaryValue().bytes);
                }

                if (sourceContext != null) {
                    sourceContext.toggleAlt();
                    try {
                        if (AbstractAnalyzer.Genre.PLAIN == genre && (source != null)) {
                            // Source root is read with UTF-8 as a default.
                            hasContext = sourceContext.getContext(
                                new InputStreamReader(new FileInputStream(
                                source + filename), StandardCharsets.UTF_8),
                                null, null, null, filename, tags, false,
                                getDefinition() != null, ret, scopes, maxHitsPerFile);
                        } else if (AbstractAnalyzer.Genre.XREFABLE == genre && data != null && summarizer != null) {
                            int l;
                            /*
                              For backward compatibility, read the OpenGrok-produced document using the system
                              default charset.
                             */
                            final char[] content = new char[1024 * 8];
                            try (Reader r = env.isCompressXref()
                                    ? new HTMLStripCharFilter(new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(
                                            TandemPath.join(data + Prefix.XREF_P + filename, ".gz"))))))
                                    : new HTMLStripCharFilter(new BufferedReader(new FileReader(data + Prefix.XREF_P + filename)))) {
                                l = r.read(content);
                            }
                            //TODO FIX below fragmenter according to either summarizer or context
                            // (to get line numbers, might be hard, since xref writers will need to be fixed too,
                            // they generate just one line of html code now :( )
                            Summary sum = summarizer.getSummary(new String(content, 0, l));
                            Fragment[] fragments = sum.getFragments();
                            for (Fragment fragment : fragments) {
                                String match = fragment.toString();
                                if (!match.isEmpty()) {
                                    if (!fragment.isEllipsis()) {
                                        Hit hit = new Hit(filename, fragment.toString(), "", true, alt);
                                        ret.add(hit);
                                    }
                                    hasContext = true;
                                }
                            }
                        } else {
                            LOGGER.log(Level.WARNING, "Unknown genre: {0} for {1}", new Object[]{genre, filename});
                            hasContext |= sourceContext.getContext(null, null, null, null, filename, tags, false, false, ret, scopes);
                        }
                    } catch (FileNotFoundException exp) {
                        LOGGER.log(Level.WARNING, "Couldn''t read summary from {0} ({1})", new Object[]{filename, exp.getMessage()});
                        hasContext |= sourceContext.getContext(null, null, null, null, filename, tags, false, false, ret, scopes);
                    }
                }
                if (historyContext != null) {
                    hasContext |= historyContext.getContext(source + filename, filename, ret);
                }
                if (!hasContext) {
                    ret.add(new Hit(filename, "...", "", false, alt));
                }
            } catch (IOException | ClassNotFoundException | HistoryException e) {
                LOGGER.log(Level.WARNING, SEARCH_EXCEPTION_MSG, e);
            }
        }
    }

    /**
     * Free resources associated with this instance.
     */
    public void destroy() {
        for (SuperIndexSearcher superIndexSearcher : searcherList) {
            try {
                superIndexSearcher.release();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "cannot release indexSearcher", ex);
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
     * Getter for property maxHitsPerFile.
     *
     * @return Value of property maxHitsPerFile. 0 means unlimited.
     */
    public int getMaxHitsPerFile() {
        return this.maxHitsPerFile;
    }

    /**
     * Sets the maximum number of matching lines returned per file in {@link #results}.
     * 0 (the default) returns all matching lines, consistent with the HTML search page.
     * Callers that need to bound response size can pass a positive value to cap per-file hits.
     *
     * @param maxHitsPerFile maximum hits per file, or 0 for unlimited.
     */
    public void setMaxHitsPerFile(int maxHitsPerFile) {
        this.maxHitsPerFile = maxHitsPerFile;
    }

    /**
     * @return maximum number of documents collected by {@code search(...)}
     */
    @VisibleForTesting
    public int getMaxDocs() {
        return maxDocs;
    }

    /**
     * Gets the total number of documents matching the query from {@code search(...)} if it was called,
     * regardless of the {@code maxDocs} cap (see {@link #SearchEngine(int)}).
     *
     * @return total matching document count
     */
    public int getTotalHits() {
        return totalHits;
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
     * Getter for property type.
     *
     * @return Value of property type.
     */
    public String getType() {
        return this.type;
    }

    /**
     * Setter for property type.
     *
     * @param fileType New value of property type.
     */
    public void setType(String fileType) {
        this.type = fileType;
    }

    /**
     * Getter for property sort.
     *
     * @return Value of property sortOrder.
     */
    public SortOrder getSortOrder() {
        return this.sortOrder;
    }

    /**
     * Setter for property sort.
     *
     * @param sortOrder New value of property sortOrder.
     */
    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }
}
