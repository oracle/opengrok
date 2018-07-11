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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.opengrok.suggest.query.SuggesterPrefixQuery;
import org.opengrok.suggest.query.SuggesterQuery;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides an interface for accessing suggester functionality.
 */
public final class Suggester implements Closeable {

    private static final String PROJECTS_DISABLED_KEY = "";

    private static final Logger logger = Logger.getLogger(Suggester.class.getName());

    private final Map<String, FieldWFSTCollection> projectData = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private final File suggesterDir;

    private int resultSize;

    private Duration awaitTerminationTime;

    private boolean allowMostPopular;

    private boolean projectsEnabled;

    /**
     * @param suggesterDir directory under which the suggester data should be created
     * @param resultSize maximum number of suggestions that should be returned
     * @param awaitTerminationTime how much time to wait for suggester to initialize
     * @param allowMostPopular specifies if the most popular completion is enabled
     * @param projectsEnabled specifies if the OpenGrok projects are enabled
     */
    public Suggester(
            final File suggesterDir,
            final int resultSize,
            final Duration awaitTerminationTime,
            final boolean allowMostPopular,
            final boolean projectsEnabled
    ) {
        if (suggesterDir == null) {
            throw new IllegalArgumentException("Suggester needs to have directory specified");
        }
        if (suggesterDir.exists() && !suggesterDir.isDirectory()) {
            throw new IllegalArgumentException(suggesterDir + " is not a directory");
        }

        this.suggesterDir = suggesterDir;

        setResultSize(resultSize);
        setAwaitTerminationTime(awaitTerminationTime);

        this.allowMostPopular = allowMostPopular;
        this.projectsEnabled = projectsEnabled;
    }

    /**
     * Initializes suggester data for specified indexes. The data is initialized asynchronously.
     * @param luceneIndexes paths where Lucene indexes are stored
     */
    public void init(final Collection<Path> luceneIndexes) {
        if (luceneIndexes == null || luceneIndexes.isEmpty()) {
            logger.log(Level.INFO, "No index directories found, exiting...");
            return;
        }
        if (!projectsEnabled && luceneIndexes.size() > 1) {
            throw new IllegalArgumentException("Projects are not enabled and multiple lucene indexes were passed");
        }

        synchronized (lock) {
            logger.log(Level.INFO, "Initializing suggester");

            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            for (Path indexDir : luceneIndexes) {
                submitInitIfIndexExists(executorService, indexDir);
            }

            shutdownAndAwaitTermination(executorService, "Suggester successfully initialized");
        }
    }

    private void submitInitIfIndexExists(final ExecutorService executorService, final Path indexDir) {
        try {
            if (indexExists(indexDir)) {
                executorService.submit(getInitRunnable(indexDir));
            } else {
                logger.log(Level.FINE, "Index in {0} directory does not exist, skipping...", indexDir);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not check if index exists", e);
        }
    }

    private Runnable getInitRunnable(final Path indexDir) {
        return () -> {
            try {
                logger.log(Level.FINE, "Initializing {0}", indexDir);

                FieldWFSTCollection wfst = new FieldWFSTCollection(FSDirectory.open(indexDir), getSuggesterDir(indexDir),
                        allowMostPopular);
                wfst.init();
                if (projectsEnabled) {
                    projectData.put(indexDir.getFileName().toString(), wfst);
                } else {
                    projectData.put(PROJECTS_DISABLED_KEY, wfst);
                }

                logger.log(Level.FINE, "Finished initialization of {0}", indexDir);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not initialize suggester data for " + indexDir, e);
            }
        };
    }

    private Path getSuggesterDir(final Path indexDir) {
        if (projectsEnabled) {
            return suggesterDir.toPath().resolve(indexDir.getFileName());
        } else {
            return this.suggesterDir.toPath();
        }
    }

    private boolean indexExists(final Path indexDir) throws IOException {
        try (Directory indexDirectory = FSDirectory.open(indexDir)) {
            return DirectoryReader.indexExists(indexDirectory);
        }
    }

    private void shutdownAndAwaitTermination(final ExecutorService executorService, final String logMessageOnSuccess) {
        executorService.shutdown();
        try {
            executorService.awaitTermination(awaitTerminationTime.toMillis(), TimeUnit.MILLISECONDS);
            logger.log(Level.INFO, logMessageOnSuccess);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Interrupted while building suggesters", e);
        }
    }

    /**
     * Rebuilds the data structures for specified indexes.
     * @param indexDirs paths where Lucene indexes are stored
     */
    public void rebuild(final List<Path> indexDirs) {
        if (indexDirs == null || indexDirs.isEmpty()) {
            logger.log(Level.INFO, "Not rebuilding suggester data because no index directories were specified");
            return;
        }

        synchronized (lock) {
            logger.log(Level.INFO, "Rebuilding following suggesters: {0}", indexDirs);

            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            for (Path indexDir : indexDirs) {
                FieldWFSTCollection fieldsWFST = projectData.get(indexDir.getFileName().toString());
                if (fieldsWFST != null) {
                    executorService.submit(getRebuildRunnable(fieldsWFST));
                } else {
                    submitInitIfIndexExists(executorService, indexDir);
                }
            }

            shutdownAndAwaitTermination(executorService, "Suggesters for " + indexDirs + " were successfully rebuilt");
        }
    }

    private Runnable getRebuildRunnable(final FieldWFSTCollection fieldsWFST) {
        return () -> {
            try {
                logger.log(Level.FINE, "Rebuilding {0}", fieldsWFST);
                fieldsWFST.rebuild();
                logger.log(Level.FINE, "Rebuild of {0} finished", fieldsWFST);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not rebuild suggester", e);
            }
        };
    }

    /**
     * Removes the data associated with the names {@code projectNames}.
     * @param projectNames names of the indexes to delete (name is determined by the name of the Lucene index
     * directory)
     */
    public void remove(final Iterable<String> projectNames) {
        if (projectNames == null) {
            return;
        }

        synchronized (lock) {
            logger.log(Level.INFO, "Removing following suggesters: {0}", projectNames);

            for (String suggesterName : projectNames) {
                FieldWFSTCollection collection = projectData.get(suggesterName);
                if (collection == null) {
                    logger.log(Level.WARNING, "Unknown suggester {0}", suggesterName);
                    continue;
                }
                collection.remove();
                projectData.remove(suggesterName);
            }
        }
    }

    /**
     * Retrieves suggestions based on the specified parameters.
     * @param indexReaders index readers with specified name (OpenGrok's project name)
     * @param suggesterQuery query for suggestions
     * @param query query on which the suggestions depend
     * @return suggestions
     */
    public List<LookupResultItem> search(
            final List<NamedIndexReader> indexReaders,
            final SuggesterQuery suggesterQuery,
            final Query query
    ) {
        if (indexReaders == null || suggesterQuery == null) {
            return Collections.emptyList();
        }

        List<NamedIndexReader> readers = indexReaders;
        if (!projectsEnabled) {
            readers = Collections.singletonList(new NamedIndexReader(PROJECTS_DISABLED_KEY,
                    indexReaders.get(0).getReader()));
        }

        List<LookupResultItem> results = readers.parallelStream().flatMap(namedIndexReader -> {

            FieldWFSTCollection data = projectData.get(namedIndexReader.name);
            if (data == null) {
                logger.log(Level.FINE, "{0} not yet initialized", namedIndexReader.name);
                return Stream.empty();
            }
            boolean gotLock = data.tryLock();
            if (!gotLock) { // do not wait for rebuild
                return Stream.empty();
            }

            try {
                if (!SuggesterUtils.isComplexQuery(query, suggesterQuery)) { // use WFST for lone prefix
                    String prefix = ((SuggesterPrefixQuery) suggesterQuery).getPrefix().text();

                    return data.lookup(suggesterQuery.getField(), prefix, resultSize)
                            .stream()
                            .map(item -> new LookupResultItem(item.key.toString(), namedIndexReader.name, item.value));
                } else {
                    SuggesterSearcher searcher = new SuggesterSearcher(namedIndexReader.reader, resultSize);

                    List<LookupResultItem> resultItems = searcher.suggest(query, namedIndexReader.name, suggesterQuery,
                            data.getSearchCounts(suggesterQuery.getField()));

                    return resultItems.stream();
                }
            } finally {
                data.unlock();
            }
        }).collect(Collectors.toList());

        return SuggesterUtils.combineResults(results, resultSize);
    }

    /**
     * Handler for search events.
     * @param projects projects that the {@code query} was used to search in
     * @param query query that was used to perform the search
     */
    public void onSearch(final Iterable<String> projects, final Query query) {
        if (!allowMostPopular) {
            return;
        }
        try {
            List<Term> terms = SuggesterUtils.intoTerms(query);

            if (!projectsEnabled) {
                for (Term t : terms) {
                    projectData.get(PROJECTS_DISABLED_KEY).incrementSearchCount(t);
                }
            } else {
                for (String project : projects) {
                    for (Term t : terms) {
                        projectData.get(project).incrementSearchCount(t);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not update search count map", e);
        }
    }

    /**
     * Sets the new maximum number of elements the suggester should suggest.
     * @param resultSize new number of suggestions to return
     */
    public final void setResultSize(final int resultSize) {
        if (resultSize < 0) {
            throw new IllegalArgumentException("Result size cannot be negative");
        }
        this.resultSize = resultSize;
    }

    /**
     * Sets the new duration for which to await the initialization of the suggester data. Does not affect already
     * running initialization.
     * @param awaitTerminationTime maximum duration for which to wait for initialization
     */
    public final void setAwaitTerminationTime(final Duration awaitTerminationTime) {
        if (awaitTerminationTime.isNegative() || awaitTerminationTime.isZero()) {
            throw new IllegalArgumentException(
                    "Time to await termination of building the suggester data cannot be 0 or negative");
        }
        this.awaitTerminationTime = awaitTerminationTime;
    }

    /**
     * Increases search counts for specific term.
     * @param project project where the term resides
     * @param term term for which to increase search count
     * @param value positive value by which to increase the search count
     */
    public void increaseSearchCount(final String project, final Term term, final int value) {
        if (!allowMostPopular) {
            return;
        }
        FieldWFSTCollection data;
        if (!projectsEnabled) {
            data = projectData.get(PROJECTS_DISABLED_KEY);
        } else {
            data = projectData.get(project);
        }
        data.incrementSearchCount(term, value);
    }

    /**
     * Closes opened resources.
     */
    @Override
    public void close() {
        projectData.values().forEach(f -> {
            try {
                f.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not close suggester data " + f, e);
            }
        });
    }

    /**
     * Model class to hold the project name and its {@link IndexReader}.
     */
    public static class NamedIndexReader {

        private final String name;

        private final IndexReader reader;

        public NamedIndexReader(final String name, final IndexReader reader) {
            this.name = name;
            this.reader = reader;
        }

        public String getName() {
            return name;
        }

        public IndexReader getReader() {
            return reader;
        }
    }

}
