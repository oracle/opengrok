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
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2024, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.indexer.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.SuggestMode;
import org.apache.lucene.search.spell.SuggestWord;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuperIndexSearcher;
import org.opengrok.indexer.index.IndexedSymlink;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.search.SettingsHelper;
import org.opengrok.indexer.search.Summarizer;
import org.opengrok.indexer.search.context.Context;
import org.opengrok.indexer.search.context.HistoryContext;
import org.opengrok.indexer.util.ErrorMessageCollector;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.WrapperIOException;

/**
 * Working set for a search basically to factor out/separate search related
 * complexity from UI design.
 *
 * @author Jens Elkner
 */
@SuppressWarnings("java:S1135")
public class SearchHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchHelper.class);

    private static final Pattern TAB_SPACE = Pattern.compile("[\t ]+");

    public static final String REQUEST_ATTR = "SearchHelper";

    /**
     * Default query parse error message prefix.
     */
    public static final String PARSE_ERROR_MSG = "Unable to parse your query: ";

    /**
     * Max number of words to suggest for spellcheck.
     */
    public static final int SPELLCHECK_SUGGEST_WORD_COUNT = 5;

    /**
     * data root: used to find the search index file.
     */
    private final File dataRoot;
    /**
     * context path, i.e. the applications' context path (usually /source) to use
     * when generating a redirect URL
     */
    private final String contextPath;

    /**
     * piggyback: the source root directory.
     */
    private final File sourceRoot;

    /**
     * piggyback: the <i>Eftar</i> file-reader to use.
     */
    private final EftarFileReader desc;
    /**
     * the result cursor start index, i.e. where to start displaying results
     */
    private int start;
    /**
     * max. number of result items to show
     */
    private int maxItems;
    /**
     * The QueryBuilder used to create the query.
     */
    private final QueryBuilder builder;
    /**
     * The order used for ordering query results.
     */
    private SortOrder order;
    /**
     * Indicate whether this is search from a cross-reference. If {@code true}
     * {@link #executeQuery()} sets {@link #redirect} if certain conditions are
     * met.
     */
    private boolean crossRefSearch;
    /**
     * As with {@link #crossRefSearch}, but here indicating either a
     * cross-reference search or a "full blown search".
     */
    private boolean guiSearch;
    /**
     * if not {@code null}, the consumer should redirect the client to a
     * separate result page denoted by the value of this field. Automatically
     * set via {@link #prepareExec(SortedSet)} and {@link #executeQuery()}.
     */
    private String redirect;
    /**
     * A value indicating if redirection should be short-circuited when state or
     * query result would have indicated otherwise.
     */
    private boolean noRedirect;
    /**
     * if not {@code null}, the UI should show this error message and stop
     * processing the search. Automatically set via
     * {@link #prepareExec(SortedSet)} and {@link #executeQuery()}.
     */
    private String errorMsg;
    /**
     * the reader used to open the index. Automatically set via
     * {@link #prepareExec(SortedSet)}.
     */
    private IndexReader reader;
    /**
     * the searcher used to open/search the index. Automatically set via {@link #prepareExec(SortedSet)}.
     */
    private IndexSearcher searcher;
    /**
     * If performing multi-project search, the indexSearcher objects will be
     * tracked by the indexSearcherMap so that they can be properly released
     * once the results are read.
     */
    private final ArrayList<SuperIndexSearcher> superIndexSearchers = new ArrayList<>();
    /**
     * List of docs which result from the executing the query.
     */
    private ScoreDoc[] hits;
    /**
     * Total number of hits.
     */
    private long totalHits;
    /**
     * the query created by {@link #builder} via
     * {@link #prepareExec(SortedSet)}.
     */
    private Query query;
    /**
     * the Lucene sort instruction based on {@link #order} created via
     * {@link #prepareExec(SortedSet)}.
     */
    protected Sort sort;
    /**
     * The spellchecker object.
     */
    private DirectSpellChecker checker;
    /**
     * projects to use to set up indexer searchers. Usually done via {@link #prepareExec(SortedSet)}.
     */
    private SortedSet<String> projects;
    /**
     * opengrok summary context. Usually created via {@link #prepareSummary()}.
     */
    private Context sourceContext = null;
    /**
     * result summarizer usually created via {@link #prepareSummary()}.
     */
    private Summarizer summarizer = null;
    /**
     * history context usually created via {@link #prepareSummary()}.
     */
    private HistoryContext historyContext;

    private SettingsHelper settingsHelper;

    private SearchHelper(File dataRoot, File sourceRoot,
                         EftarFileReader eftarFileReader, QueryBuilder queryBuilder,
                         String contextPath) {
        this.start = 0;
        this.order = SortOrder.RELEVANCY;
        this.dataRoot = dataRoot;
        this.sourceRoot = sourceRoot;
        this.maxItems = 10;
        this.desc = eftarFileReader;
        this.builder = queryBuilder;
        this.contextPath = contextPath;

    }

    public File getDataRoot() {
        return dataRoot;
    }

    public File getSourceRoot() {
        return sourceRoot;
    }

    public EftarFileReader getDesc() {
        return desc;
    }

    public QueryBuilder getBuilder() {
        return builder;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public String getRedirect() {
        return redirect;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }

    public ScoreDoc[] getHits() {
        return hits;
    }

    public Query getQuery() {
        return query;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public SortedSet<String> getProjects() {
        return projects;
    }

    public Context getSourceContext() {
        return sourceContext;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public SortOrder getOrder() {
        return order;
    }

    public int getStart() {
        return start;
    }

    public Summarizer getSummarizer() {
        return summarizer;
    }

    public HistoryContext getHistoryContext() {
        return historyContext;
    }

    /**
     * User readable description for file types. Only those listed in
     * fileTypeDescription will be shown to the user.
     * Returns a set of file type descriptions to be used for a search form.
     *
     * @return Set of tuples with file type and description.
     */
    public static Set<Map.Entry<String, String>> getFileTypeDescriptions() {
        return AnalyzerGuru.getfileTypeDescriptions().entrySet();
    }

    /**
     * Create the searcher to use w.r.t. currently set parameters and the given
     * projects. Does not produce any {@link #redirect} link. It also does
     * nothing if {@link #redirect} or {@link #errorMsg} have a none-{@code null} value.
     * <p>
     * Parameters which should be populated/set at this time:
     * <ul>
     * <li>{@link #builder}</li> <li>{@link #dataRoot}</li>
     * <li>{@link #order} (falls back to relevance if unset)</li>
     * </ul>
     * Populates/sets:
     * <ul>
     * <li>{@link #query}</li> <li>{@link #searcher}</li> <li>{@link #sort}</li>
     * <li>{@link #projects}</li> <li>{@link #errorMsg} if an error occurs</li>
     * </ul>
     *
     * @param projects project names. If empty, a no-project setup is assumed (i.e. DATA_ROOT/index will be used
     *                 instead of possible multiple DATA_ROOT/$project/index). If the set contains projects
     *                 not known in the configuration or projects not yet indexed an error will be returned
     *                 in {@link #errorMsg}.
     * @return this instance
     */
    public SearchHelper prepareExec(SortedSet<String> projects) {
        if (redirect != null || errorMsg != null) {
            return this;
        }

        settingsHelper = null;
        // the Query created by the QueryBuilder
        try {
            query = builder.build();
            if (Objects.isNull(projects)) {
                errorMsg = "No project selected!";
                return this;
            }
            this.projects = projects;
            if (projects.isEmpty()) {
                // no project setup
                SuperIndexSearcher superIndexSearcher = RuntimeEnvironment.getInstance().getSuperIndexSearcher("");
                searcher = superIndexSearcher;
                superIndexSearchers.add(superIndexSearcher);
                reader = superIndexSearcher.getIndexReader();
            } else {
                // Check list of project names first to make sure all of them are valid and indexed.
                var invalidProjects = projects.stream().
                    filter(proj -> (Project.getByName(proj) == null)).
                    collect(new ErrorMessageCollector("Project list contains invalid projects: "));

                errorMsg = invalidProjects.or(() ->
                            projects.stream().
                            map(Project::getByName).
                            filter(Objects::nonNull).
                            filter(proj -> !proj.isIndexed()).
                            map(Project::getName).
                            collect(new ErrorMessageCollector(
                                    "Some of the projects to be searched are not indexed yet: "
                            ))
                        ).orElse(null);
                if (Objects.nonNull(errorMsg)) {
                    return this;
                }

                // We use MultiReader even for single project. This should not matter
                // given that MultiReader is just a cheap wrapper around set of IndexReader objects.
                reader = RuntimeEnvironment.getInstance().getMultiReader(projects, superIndexSearchers);
                if (reader != null) {
                    searcher = RuntimeEnvironment.getInstance().getIndexSearcherFactory().newSearcher(reader);
                } else {
                    errorMsg = projects.stream()
                            .collect(new ErrorMessageCollector("Failed to initialize search. Check the index for projects: ",
                                    "Failed to initialize search. Check the index"))
                            .orElse("");
                    return this;
                }
            }

            // TODO check if below is somehow reusing sessions so we don't
            // requery again and again, I guess 2min timeout sessions could be
            // useful, since you click on the next page within 2mins, if not,
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

            errorMsg = projects.stream()
                    .collect(new ErrorMessageCollector("Index database not found. Check the index for projects: ",
                            "Index database not found. Check the index"))
                    .orElse("");
            errorMsg += "; " + e.getMessage();
        } catch (IOException e) {
            errorMsg = e.getMessage();
        }
        return this;
    }

    /**
     * Calls {@link #prepareExec(java.util.SortedSet)} with a single-element
     * set for {@code project}.
     * @param project a defined instance
     * @return this instance
     */
    public SearchHelper prepareExec(Project project) {
        SortedSet<String> oneProject = new TreeSet<>();
        oneProject.add(project.getName());
        return prepareExec(oneProject);
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
     * <li>{@link #crossRefSearch} (default: false)</li> </ul> Populates/sets:
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
            totalHits = fdocs.totalHits.value;
            hits = fdocs.scoreDocs;

            /*
             * Determine if possibly a single-result redirect to xref is
             * eligible and applicable. If history query is active, then nope.
             */
            if (!noRedirect && hits != null && hits.length == 1 && builder.getHist() == null) {
                int docID = hits[0].doc;
                if (crossRefSearch && query instanceof TermQuery && builder.getDefs() != null) {
                    maybeRedirectToDefinition(docID, (TermQuery) query);
                } else if (guiSearch) {
                    if (builder.isPathSearch()) {
                        redirectToFile(docID);
                    } else {
                        maybeRedirectToMatchOffset(docID, builder.getContextFields());
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            errorMsg = e.getMessage();
        }
        return this;
    }

    private void maybeRedirectToDefinition(int docID, TermQuery termQuery)
            throws IOException, ClassNotFoundException {
        // Bug #3900: Check if this is a search for a single term, and that
        // term is a definition. If that's the case, and we only have one match,
        // we'll generate a direct link instead of a listing.
        //
        // Attempt to create a direct link to the definition if we search for
        // one single definition term AND we have exactly one match AND there
        // is only one definition of that symbol in the document that matches.
        Document doc = searcher.storedFields().document(docID);
        IndexableField tagsField = doc.getField(QueryBuilder.TAGS);
        if (tagsField != null) {
            byte[] rawTags = tagsField.binaryValue().bytes;
            Definitions tags = Definitions.deserialize(rawTags);
            String symbol = termQuery.getTerm().text();
            if (tags.occurrences(symbol) == 1) {
                String anchor = Util.uriEncode(symbol);
                redirect = contextPath + Prefix.XREF_P
                        + Util.uriEncodePath(doc.get(QueryBuilder.PATH))
                        + '?' + QueryParameters.FRAGMENT_IDENTIFIER_PARAM_EQ + anchor
                        + '#' + anchor;
            }
        }
    }

    private void maybeRedirectToMatchOffset(int docID, List<String> contextFields)
            throws IOException {
        /*
         * Only PLAIN files might redirect to a file offset, since an offset
         * must be subsequently converted to a line number and that is tractable
         * only from plain text.
         */
        var doc = searcher.storedFields().document(docID);
        var genre = doc.get(QueryBuilder.T);
        if (!AbstractAnalyzer.Genre.PLAIN.typeName().equals(genre)) {
            return;
        }

        var leaves = reader.leaves();
        int subIndex = ReaderUtil.subIndex(docID, leaves);
        var leaf = leaves.get(subIndex);

        var rewritten = query.rewrite(searcher);
        var weight = rewritten.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1);
        var matches = weight.matches(leaf, docID - leaf.docBase); // Adjust docID
        try {
            Optional.ofNullable(matches)
                    .filter(Predicate.not(MatchesUtils.MATCH_WITH_NO_TERMS::equals))
                    .map(objMatches -> calculateRedirectOffset(objMatches, contextFields))
                    .filter(offset -> offset >= 0)
                    .ifPresent(offset ->
                            redirect = contextPath + Prefix.XREF_P
                                    + Util.uriEncodePath(doc.get(QueryBuilder.PATH))
                                    + '?' + QueryParameters.MATCH_OFFSET_PARAM_EQ + offset
                    );

        } catch (WrapperIOException ex) {
            throw ex.getParentIOException();
        }

    }
    private int calculateRedirectOffset(Matches matches, List<String> contextFields) {
        int offset = -1;
        try {
            for (String field : contextFields) {
                var matchesIterator = matches.getMatches(field);
                while (matchesIterator.next()) {
                    if (matchesIterator.startOffset() >= 0) {
                        // Abort if there is more than a single match offset.
                        if (offset >= 0) {
                            return -1;
                        }
                        offset = matchesIterator.startOffset();
                    }
                }
            }
        } catch (IOException ex) {
            throw new WrapperIOException(ex);
        }
        return offset;
    }

    private void redirectToFile(int docID) throws IOException {
        Document doc = searcher.storedFields().document(docID);
        redirect = contextPath + Prefix.XREF_P + Util.uriEncodePath(doc.get(QueryBuilder.PATH));
    }

    private  String[] getSuggestion(Term term, IndexReader ir) {
        //TODO below seems to be case insensitive ... for refs/defs this is bad
        Function<String, SuggestWord[]> suggester = token -> {
            try {
                return checker.suggestSimilar(new Term(term.field(), token),
                        SPELLCHECK_SUGGEST_WORD_COUNT, ir, SuggestMode.SUGGEST_ALWAYS);
            } catch (IOException ex) {
                throw new WrapperIOException(ex);
            }
        };
        return Optional.ofNullable(term)
                .map(fullToken -> TAB_SPACE.split(fullToken.text(), 0))
                .stream().flatMap(Arrays::stream)
                .map(suggester)
                .flatMap(Arrays::stream)
                .map(suggestWord -> suggestWord.string)
                .toArray(String[] ::new);
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
        var projectNames = Optional.of(projects)
                            .filter(projectsList -> !projectsList.isEmpty())
                            .map(projectsList -> projectsList.toArray(String[]::new))
                            .orElseGet(() -> new String[]{"/"});

        List<Suggestion> res = new ArrayList<>();
        for (String projectName : projectNames) {
            Suggestion suggestion = new Suggestion(projectName);
            try {
                var searcherName = projects.isEmpty() ? "" : projectName;
                var superIndexSearcher = RuntimeEnvironment.getInstance().getSuperIndexSearcher(searcherName);
                superIndexSearchers.add(superIndexSearcher);
                var ir = superIndexSearcher.getIndexReader();

                Optional.ofNullable(builder.getFreetext())
                        .filter(Predicate.not(String::isEmpty))
                        .map(freeText -> new Term(QueryBuilder.FULL, freeText))
                        .map(term -> getSuggestion(term, ir))
                        .ifPresent(suggestion::setFreetext);
                Optional.ofNullable(builder.getRefs())
                        .filter(Predicate.not(String::isEmpty))
                        .map(refs -> new Term(QueryBuilder.REFS, refs))
                        .map(term -> getSuggestion(term, ir))
                        .ifPresent(suggestion::setDefs);
                Optional.ofNullable(builder.getDefs())
                        .filter(Predicate.not(String::isEmpty))
                        .map(defs -> new Term(QueryBuilder.DEFS, defs))
                        .map(term -> getSuggestion(term, ir))
                        .ifPresent(suggestion::setDefs);

                //TODO suggest also for path and history?
                if (suggestion.isUsable()) {
                    res.add(suggestion);
                }
            } catch (WrapperIOException ex) {
                LOGGER.log(Level.WARNING,
                        String.format("Got exception while getting spelling suggestions for project %s:", projectName),
                        ex.getParentIOException());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        String.format("Got exception while getting spelling suggestions for project %s:", projectName),
                        e);
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
            sourceContext = new Context(query, builder);
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
     * Free any resources associated with this helper.
     */
    public void destroy() {
        for (SuperIndexSearcher superIndexSearcher : superIndexSearchers) {
            try {
                superIndexSearcher.release();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "cannot release SuperIndexSearcher", ex);
            }
        }
    }

    /**
     * Searches for a document for a single file from the index.
     * @param file the file whose definitions to find
     * @return {@link ScoreDoc#doc} or -1 if it could not be found
     * @throws IOException if an error happens when accessing the index
     * @throws ParseException if an error happens when building the Lucene query
     */
    public int searchSingle(File file) throws IOException,
            ParseException {

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String path;
        try {
            path = env.getPathRelativeToSourceRoot(file);
        } catch (ForbiddenSymlinkException e) {
            LOGGER.log(Level.FINER, e.getMessage());
            return -1;
        }
        //sanitize windows path delimiters
        //in order not to conflict with Lucene escape character
        path = path.replace("\\", "/");

        QueryBuilder singleBuilder = new QueryBuilder();
        if (builder != null) {
            singleBuilder.reset(builder);
        }
        query = singleBuilder.setPath(path).build();

        TopDocs top = searcher.search(query, 1);
        if (top.totalHits.value == 0) {
            return -1;
        }

        int docID = top.scoreDocs[0].doc;
        Document doc = searcher.storedFields().document(docID);

        String foundPath = doc.get(QueryBuilder.PATH);
        // Only use the result if PATH matches exactly.
        if (!path.equals(foundPath)) {
            return -1;
        }

        return docID;
    }

    /**
     * Gets the persisted tabSize via {@link SettingsHelper} for the active
     * reader.
     * @param proj a defined instance or {@code null} if no project is active
     * @return tabSize
     * @throws IOException if an I/O error occurs querying the active reader
     */
    public int getTabSize(Project proj) throws IOException {
        ensureSettingsHelper();
        return settingsHelper.getTabSize(proj);
    }

    /**
     * Determines if there is a prime equivalent to {@code relativePath}
     * according to indexed symlinks and translate (or not) accordingly.
     * @param project the project name or empty string if projects are not used
     * @param relativePath an OpenGrok-style (i.e. starting with a file
     *                     separator) relative path
     * @return a prime relative path or just {@code relativePath} if no prime
     * is matched
     */
    public String getPrimeRelativePath(String project, String relativePath)
            throws IOException, ForbiddenSymlinkException {

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String nonEmptySourceRoot = Optional.ofNullable(env.getSourceRootPath())
                .orElseThrow(() -> new IllegalStateException("sourceRoot is not defined"));

        File absolute = new File(nonEmptySourceRoot + relativePath);

        ensureSettingsHelper();
        settingsHelper.getSettings(project);
        Map<String, IndexedSymlink> indexedSymlinks = settingsHelper.getSymlinks(project);
        String canonical = absolute.getCanonicalFile().getPath();
        for (IndexedSymlink entry : indexedSymlinks.values()) {
            if (canonical.equals(entry.getCanonical())) {
                if (absolute.getPath().equals(entry.getAbsolute())) {
                    return relativePath;
                }
                Path newAbsolute = Paths.get(entry.getAbsolute());
                return env.getPathRelativeToSourceRoot(newAbsolute.toFile());
            } else if (canonical.startsWith(entry.getCanonicalSeparated())) {
                Path newAbsolute = Paths.get(entry.getAbsolute(),
                        canonical.substring(entry.getCanonicalSeparated().length()));
                return env.getPathRelativeToSourceRoot(newAbsolute.toFile());
            }
        }

        return relativePath;
    }

    private void ensureSettingsHelper() {
        if (settingsHelper == null) {
            settingsHelper = new SettingsHelper(reader);
        }
    }

    /**
     * Builder Class for Search Helper.
     */
    public static final class Builder {
        private final SearchHelper searchHelper;

        /**
         * Search Helper Builder Class constructor.
         * <p>
         * Parameters which are defaulting while using this builder
         * <ul>
         * <li>{@link #start} default value zero</li>
         * <li>{@link #maxItems} default value 10</li>
         * <li>{@link #crossRefSearch} default value false</li>
         * <li>{@link #guiSearch} default value false</li>
         * <li>{@link #noRedirect} default value false</li>
         * <li>{@link #order} defaulted to RELEVANCY based sorting</li>
         * </ul>
         * @param dataRoot used to find the search index file
         * @param sourceRoot the source root directory.
         * @param eftarFileReader the <i>Eftar</i> file-reader to use.
         * @param queryBuilder The QueryBuilder used to create the query.
         * @param contextPath the applications' context path (usually /source) to use
         *                      when generating a redirect URL
         */
        public Builder(File dataRoot, File sourceRoot,
                     EftarFileReader eftarFileReader, QueryBuilder queryBuilder,
                     String contextPath) {
            searchHelper = new SearchHelper(dataRoot, sourceRoot, eftarFileReader, queryBuilder, contextPath);
        }

        /**
         * Set result cursor start index , i.e. where to start displaying results.
         * @param start result cursor start index
         * @return search helper builder instance
         */
        public Builder start(int start) {
            searchHelper.start = start;
            return this;
        }

        /**
         *  set max. number of result items to show.
         * @param maxItems maximum result items to show
         * @return search helper builder instance
         */
        public Builder maxItems(int maxItems) {
            searchHelper.maxItems = maxItems;
            return this;
        }

        /**
         * Sets order used for ordering query results.
         * @param order query results sort order
         * @return search helper builder instance
         */
        public Builder order(SortOrder order) {
            searchHelper.order = order;
            return this;
        }

        /**
         * Indicate whether this is search from a cross-reference. If {@code true}
         *  {@link #executeQuery()} sets {@link #redirect} if certain conditions are met.
         * @param crossRefSearch enable or disable crossRefSearch
         * @return search helper builder instance
         */
        public Builder crossRefSearch(boolean crossRefSearch) {
            searchHelper.crossRefSearch = crossRefSearch;
            return this;
        }

        /**
         *As with {@link #crossRefSearch}, but here indicating either a
         * cross-reference search or a "full blown search".
         * @param guiSearch enable or disable guiSearch
         * @return search helper builder instance
         */
        public Builder guiSearch(boolean guiSearch) {
            searchHelper.guiSearch = guiSearch;
            return this;
        }

        /**
         * A value indicating if redirection should be short-circuited when state or
         *  query result would have indicated otherwise.
         * @param noRedirect enable or disable redirection parameter
         * @return search helper builder instance
         */
        public Builder noRedirect(boolean noRedirect) {
            searchHelper.noRedirect = noRedirect;
            return this;
        }

        /**
         * Create and return the final search helper instance.
         * @return search helper configured to the specification of previous calls to this builder
         */
        public SearchHelper build() {
            return this.searchHelper;
        }

    }
}
