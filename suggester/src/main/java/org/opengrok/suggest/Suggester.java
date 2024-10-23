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
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.suggest.query.SuggesterPrefixQuery;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opengrok.suggest.util.Progress;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides an interface for accessing suggester functionality.
 */
public final class Suggester implements Closeable {

    private static final String PROJECTS_DISABLED_KEY = "";

    private static final Logger LOGGER = Logger.getLogger(Suggester.class.getName());

    private final Map<String, SuggesterProjectData> projectDataMap = new ConcurrentHashMap<>();

    private final File suggesterDir;

    private int resultSize;

    private Duration awaitTerminationTime;

    private final boolean allowMostPopular;

    private final boolean projectsEnabled;

    private final Set<String> allowedFields;

    private final int timeThreshold;

    private final boolean isPrintProgress;

    private volatile boolean rebuilding;
    private volatile boolean terminating;
    private final Lock rebuildLock = new ReentrantLock();
    private final Condition rebuildDone = rebuildLock.newCondition();

    private final CountDownLatch initDone = new CountDownLatch(1);

    private final Timer suggesterRebuildTimer;  // per suggester data
    private final Timer suggesterInitTimer;     // per suggester data

    private final ExecutorService searchExecutorService;

    private final ExecutorService initRebuildExecutor;

    /**
     * @param suggesterDir directory under which the suggester data should be created
     * @param resultSize maximum number of items that should be returned
     * @param awaitTerminationTime how much time to wait for suggester to initialize
     * @param allowMostPopular specifies if the most popular completion is enabled
     * @param projectsEnabled specifies if the OpenGrok projects are enabled
     * @param allowedFields fields for which should the suggester be enabled,
     * if {@code null} then enabled for all fields
     * @param timeThreshold time in milliseconds after which the suggestions requests should time out
     * @param rebuildParallelismLevel parallelism level for rebuild
     * @param searchParallelismLevel parallelism level for search
     * @param registry meter registry
     * @param isPrintProgress whether to report progress for initialization and rebuild
     */
    @SuppressWarnings("java:S107")
    public Suggester(
            final File suggesterDir,
            final int resultSize,
            final Duration awaitTerminationTime,
            final boolean allowMostPopular,
            final boolean projectsEnabled,
            final Set<String> allowedFields,
            final int timeThreshold,
            final int rebuildParallelismLevel,
            final int searchParallelismLevel,
            MeterRegistry registry,
            boolean isPrintProgress) {
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
        this.allowedFields = new HashSet<>(allowedFields);
        this.timeThreshold = timeThreshold;
        this.isPrintProgress = isPrintProgress;

        // do NOT use fork join thread pool (work stealing thread pool) because it does not send interrupts upon cancellation
        this.searchExecutorService = Executors.newFixedThreadPool(searchParallelismLevel,
                runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    // This should match the naming in OpenGrokThreadFactory class.
                    thread.setName("OpenGrok-suggester-lookup-" + thread.threadId());
                    return thread;
                });

        this.initRebuildExecutor = Executors.newFixedThreadPool(rebuildParallelismLevel,
                runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    // This should match the naming in OpenGrokThreadFactory class.
                    thread.setName("OpenGrok-suggester-rebuild-" + thread.threadId());
                    return thread;
                });

        suggesterRebuildTimer = Timer.builder("suggester.rebuild.latency").
                description("suggester rebuild latency").
                register(registry);
        suggesterInitTimer = Timer.builder("suggester.init.latency").
                description("suggester initialization latency").
                register(registry);
    }

    /**
     * Initializes suggester data for specified indexes. The data is initialized asynchronously.
     * @param luceneIndexes paths to Lucene indexes and name with which the index should be associated
     */
    public synchronized void init(final Collection<NamedIndexDir> luceneIndexes) {
        if (luceneIndexes == null || luceneIndexes.isEmpty()) {
            LOGGER.log(Level.INFO, "No index directories found, exiting...");
            return;
        }
        if (!projectsEnabled && luceneIndexes.size() > 1) {
            throw new IllegalArgumentException("Projects are not enabled and multiple Lucene indexes were passed");
        }

        Instant start = Instant.now();
        LOGGER.log(Level.INFO, "Initializing suggester for {0}", luceneIndexes);
        try (Progress progress = new Progress(LOGGER, "suggester initialization", luceneIndexes.size(),
                Level.INFO, isPrintProgress)) {

            List<Future<?>> futures = new ArrayList<>();
            for (NamedIndexDir indexDir : luceneIndexes) {
                if (terminating) {
                    LOGGER.log(Level.INFO, "Terminating suggester initialization");
                    return;
                }

                SuggesterProjectData projectData = this.projectDataMap.computeIfAbsent(getProjectDataKey(indexDir),
                            dir -> createProjectData(indexDir));
                if (projectData == null) {
                    LOGGER.log(Level.WARNING, "failed to create project data for {0}", indexDir);
                    continue;
                }

                submitInitIfIndexExists(initRebuildExecutor, indexDir, projectData, progress).ifPresent(futures::add);
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.log(Level.WARNING, "cannot retrieve suggester init result", e);
                }
            }

            initDone.countDown();
            Duration duration = Duration.between(start, Instant.now());
            LOGGER.log(Level.INFO, "{0} (took {1})", new Object[]{"Suggesters for " + luceneIndexes + " were initialized",
                    DurationFormatUtils.formatDurationWords(duration.toMillis(),
                            true, true)});
        }
    }

    private String getProjectDataKey(NamedIndexDir indexDir) {
        if (projectsEnabled) {
            return indexDir.name;
        } else {
            return PROJECTS_DISABLED_KEY;
        }
    }

    @Nullable
    private SuggesterProjectData createProjectData(NamedIndexDir indexDir) {
        SuggesterProjectData projectData = null;
        try {
            projectData = new SuggesterProjectData(FSDirectory.open(indexDir.path),
                    getSuggesterDir(indexDir.name), allowMostPopular, allowedFields);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("creating suggester data for %s failed", indexDir), e);
            return null;
        }
        return projectData;
    }

    /**
     * wait for initialization to finish.
     * @param timeout timeout value
     * @param unit timeout unit
     * @throws InterruptedException on canceled await()
     */
    @VisibleForTesting
    public void waitForInit(long timeout, TimeUnit unit) throws InterruptedException {
        if (!initDone.await(timeout, unit)) {
            LOGGER.log(Level.WARNING, "Initialization did not finish in {0} {1}", new Object[] {timeout, unit});
        }
    }

    private Optional<Future<?>> submitInitIfIndexExists(final ExecutorService executorService,
                                                        final NamedIndexDir indexDir, final SuggesterProjectData data,
                                                        Progress progress) {
        try {
            if (indexExists(indexDir.path)) {
                return Optional.of(executorService.submit(getInitRunnable(data, progress)));
            } else {
                LOGGER.log(Level.FINE, "Index in ''{0}'' directory does not exist, skipping...", data);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Could not check if index in '%s' exists", data), e);
        }
        return Optional.empty();
    }

    @Nullable
    private SuggesterProjectData getProjectData(final String name) {
        if (projectsEnabled) {
            return projectDataMap.get(name);
        } else {
            return projectDataMap.get(PROJECTS_DISABLED_KEY);
        }
    }

    private Runnable getInitRunnable(final SuggesterProjectData data, Progress progress) {
        return () -> {
            try {
                if (terminating) {
                    return;
                }

                Instant start = Instant.now();
                LOGGER.log(Level.FINE, "Initializing suggester data in ''{0}''", data);
                data.init();
                Duration duration = Duration.between(start, Instant.now());
                suggesterInitTimer.record(duration);
                LOGGER.log(Level.FINE, "Finished initialization of suggester data in ''{0}'', took {1}",
                        new Object[] {data, duration});
                progress.increment();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Could not initialize suggester data for '%s'", data), e);
            }
        };
    }

    private Path getSuggesterDir(final String indexDirName) {
        if (projectsEnabled) {
            return suggesterDir.toPath().resolve(indexDirName);
        } else {
            return this.suggesterDir.toPath();
        }
    }

    private boolean indexExists(final Path indexDir) throws IOException {
        try (Directory indexDirectory = FSDirectory.open(indexDir)) {
            return DirectoryReader.indexExists(indexDirectory);
        }
    }

    private void shutdownAndAwaitTermination(final ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(awaitTerminationTime.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.log(Level.WARNING, "executor was shutdown with timeout", executorService);
            }
            executorService.shutdownNow();
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Interrupted while building suggesters", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Rebuilds the data structures for specified indexes.
     * @param indexDirs paths to lucene indexes and name with which the index should be associated
     */
    public void rebuild(final Collection<NamedIndexDir> indexDirs) {
        if (indexDirs == null || indexDirs.isEmpty()) {
            LOGGER.log(Level.INFO, "Not rebuilding suggester data because no index directories were specified");
            return;
        }

        rebuildLock.lock();
        rebuilding = true;
        rebuildLock.unlock();

        if (terminating) {
            return;
        }

        Instant start = Instant.now();
        LOGGER.log(Level.INFO, "Rebuilding the following suggesters: {0}", indexDirs);

        try (Progress progress = new Progress(LOGGER, "suggester rebuild", indexDirs.size(),
                Level.INFO, isPrintProgress)) {
            List<Future<?>> futures = new ArrayList<>();
            for (NamedIndexDir indexDir : indexDirs) {
                SuggesterProjectData projectData = this.projectDataMap.computeIfAbsent(getProjectDataKey(indexDir),
                        dir -> createProjectData(indexDir));
                if (projectData != null) {
                    if (projectData.isInitialized()) {
                        futures.add(initRebuildExecutor.submit(getRebuildRunnable(projectData, progress)));
                    } else {
                        submitInitIfIndexExists(initRebuildExecutor, indexDir, projectData, progress).ifPresent(futures::add);
                    }
                }
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.log(Level.WARNING, "cannot retrieve suggester rebuild result", e);
                }
            }

            Duration duration = Duration.between(start, Instant.now());
            LOGGER.log(Level.INFO, "{0} (took {1})", new Object[]{"Suggesters for " + indexDirs + " were rebuilt",
                    DurationFormatUtils.formatDurationWords(duration.toMillis(),
                            true, true)});
        }

        rebuildLock.lock();
        try {
            rebuilding = false;
            rebuildDone.signalAll();
        } finally {
            rebuildLock.unlock();
        }
    }

    /**
     * wait for rebuild to finish.
     * @param timeout timeout value
     * @param unit timeout unit
     * @throws InterruptedException on canceled await()
     */
    public void waitForRebuild(long timeout, TimeUnit unit) throws InterruptedException {
        rebuildLock.lock();
        try {
            while (rebuilding) {
                if (!rebuildDone.await(timeout, unit)) {
                    LOGGER.log(Level.WARNING, "Rebuild did not finish in {0} {1}", new Object[] {timeout, unit});
                }
            }
        } finally {
            rebuildLock.unlock();
        }
    }

    private Runnable getRebuildRunnable(final SuggesterProjectData data, Progress progress) {
        return () -> {
            try {
                if (terminating) {
                    return;
                }

                Instant start = Instant.now();
                LOGGER.log(Level.FINE, "Rebuilding {0}", data);
                data.rebuild();
                Duration duration = Duration.between(start, Instant.now());
                suggesterRebuildTimer.record(duration);
                LOGGER.log(Level.FINE, "Rebuild of {0} finished, took {1}", new Object[] {data, duration});
                progress.increment();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Could not rebuild suggester", e);
            }
        };
    }

    /**
     * Removes the data associated with the provided names.
     * @param names names of the indexes to delete
     */
    public void remove(final Iterable<String> names) {
        if (names == null) {
            return;
        }

        LOGGER.log(Level.INFO, "Removing following suggesters: {0}", names);
        for (String suggesterName : names) {
            SuggesterProjectData collection = projectDataMap.get(suggesterName);
            if (collection == null) {
                LOGGER.log(Level.WARNING, "Unknown suggester {0}", suggesterName);
                continue;
            }
            projectDataMap.remove(suggesterName);
            collection.remove();
        }
    }

    /**
     * Retrieves suggestions based on the specified parameters.
     * @param indexReaders index readers with specified name (OpenGrok's project name)
     * @param suggesterQuery query for suggestions
     * @param query query on which the suggestions depend
     * @return suggestions
     */
    public Suggestions search(
            final List<NamedIndexReader> indexReaders,
            final SuggesterQuery suggesterQuery,
            final Query query
    ) {
        if (indexReaders == null || suggesterQuery == null) {
            return new Suggestions(Collections.emptyList(), true);
        }

        List<NamedIndexReader> readers = indexReaders;
        if (!projectsEnabled) {
            readers = Collections.singletonList(new NamedIndexReader(PROJECTS_DISABLED_KEY,
                    indexReaders.get(0).getReader()));
        }

        Suggestions suggestions;
        if (!SuggesterUtils.isComplexQuery(query, suggesterQuery)) { // use WFST for lone prefix
            suggestions = prefixLookup(readers, (SuggesterPrefixQuery) suggesterQuery);
        } else {
            suggestions = complexLookup(readers, suggesterQuery, query);
        }

        return new Suggestions(SuggesterUtils.combineResults(suggestions.items, resultSize),
                suggestions.partialResult);
    }

    private Suggestions prefixLookup(
            final List<NamedIndexReader> readers,
            final SuggesterPrefixQuery suggesterQuery
    ) {
        BooleanWrapper partialResult = new BooleanWrapper();

        List<LookupResultItem> results = readers.parallelStream().flatMap(namedIndexReader -> {
            SuggesterProjectData data = projectDataMap.get(namedIndexReader.name);
            if (data == null) {
                LOGGER.log(Level.FINE, "{0} not yet initialized", namedIndexReader.name);
                partialResult.value = true;
                return Stream.empty();
            }
            boolean gotLock = data.tryLock();
            if (!gotLock) { // do not wait for rebuild
                partialResult.value = true;
                return Stream.empty();
            }

            try {
                String prefix = suggesterQuery.getPrefix().text();

                return data.lookup(suggesterQuery.getField(), prefix, resultSize)
                        .stream()
                        .map(item -> new LookupResultItem(item.key.toString(), namedIndexReader.name, item.value));
            } finally {
                data.unlock();
            }
        }).collect(Collectors.toList());

        return new Suggestions(results, partialResult.value);
    }

    private Suggestions complexLookup(
            final List<NamedIndexReader> readers,
            final SuggesterQuery suggesterQuery,
            final Query query
    ) {
        List<LookupResultItem> results = new ArrayList<>(readers.size() * resultSize);
        List<SuggesterSearchTask> searchTasks = new ArrayList<>(readers.size());
        for (NamedIndexReader ir : readers) {
            searchTasks.add(new SuggesterSearchTask(ir, query, suggesterQuery, results));
        }

        List<Future<Void>> futures;
        try {
            futures = searchExecutorService.invokeAll(searchTasks, timeThreshold, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Interrupted while invoking suggester search", e);
            Thread.currentThread().interrupt();
            return new Suggestions(Collections.emptyList(), true);
        }

        boolean partialResult = futures.stream().anyMatch(Future::isCancelled);

        // wait for tasks to finish
        for (SuggesterSearchTask searchTask : searchTasks) {
            if (!searchTask.started) {
                continue;
            }

            if (!searchTask.finished) {
                synchronized (searchTask) {
                    while (!searchTask.finished) {
                        try {
                            searchTask.wait();
                        } catch (InterruptedException e) {
                            LOGGER.log(Level.WARNING, "Interrupted while waiting for task: {0}", searchTask);
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        return new Suggestions(results, partialResult);
    }

    /**
     * Handler for search events.
     * @param projects projects that the {@code query} was used to search in
     * @param query query that was used to perform the search
     */
    public void onSearch(final Iterable<String> projects, final Query query) {
        if (!allowMostPopular || projects == null) {
            return;
        }
        try {
            List<Term> terms = SuggesterUtils.intoTerms(query);

            if (!projectsEnabled) {
                incrementSearchCount(terms, PROJECTS_DISABLED_KEY);
            } else {
                projects.forEach(project -> incrementSearchCount(terms, project));
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE,
                    String.format("Could not update search count map%s",
                            projectsEnabled ? " for projects: " + projects : ""), e);
        }
    }

    private void incrementSearchCount(List<Term> terms, final String projectDataKey) {
        Optional.ofNullable(projectDataMap.get(projectDataKey))
                .ifPresent(data -> terms.forEach(data::incrementSearchCount));
    }

    /**
     * Sets the new maximum number of elements the suggester should suggest.
     * @param resultSize new number of suggestions to return
     */
    public void setResultSize(final int resultSize) {
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
    public void setAwaitTerminationTime(final Duration awaitTerminationTime) {
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
     * @return false if update failed, otherwise true
     */
    public boolean increaseSearchCount(final String project, final Term term, final int value, final boolean waitForLock) {
        if (!allowMostPopular) {
            return false;
        }

        SuggesterProjectData projectData = getProjectData(project);
        if (projectData == null) {
            LOGGER.log(Level.WARNING, "Cannot update search count because of missing suggester data{}",
                    projectsEnabled ? " for project " + project : "");
            return false;
        }

        return projectData.incrementSearchCount(term, value, waitForLock);
    }

    /**
     * Returns the searched terms sorted according to their popularity.
     * @param project project for which to return the data
     * @param field field for which to return the data
     * @param page which page of data to retrieve
     * @param pageSize number of results to return
     * @return list of terms with their popularity
     */
    public List<Entry<BytesRef, Integer>> getSearchCounts(
            final String project,
            final String field,
            final int page,
            final int pageSize
    ) {
        SuggesterProjectData data = projectDataMap.get(project);
        if (data == null) {
            LOGGER.log(Level.FINE, "Cannot retrieve search counts because suggester data for project {0} was not found",
                    project);
            return Collections.emptyList();
        }

        return data.getSearchCountsSorted(field, page, pageSize);
    }

    /**
     * Do not allow more suggester rebuilds.
     */
    public void terminate() {
        terminating = true;
    }

    /**
     * Closes opened resources.
     */
    @Override
    public void close() {
        searchExecutorService.shutdownNow();
        shutdownAndAwaitTermination(initRebuildExecutor);
        projectDataMap.values().forEach(f -> {
            try {
                f.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("Could not close suggester data %s", f), e);
            }
        });
    }

    private class SuggesterSearchTask implements Callable<Void> {

        private final NamedIndexReader namedIndexReader;
        private final Query query;
        private final SuggesterQuery suggesterQuery;
        private final List<LookupResultItem> results;

        private volatile boolean finished = false;
        private volatile boolean started = false;

        SuggesterSearchTask(
                final NamedIndexReader namedIndexReader,
                final Query query,
                final SuggesterQuery suggesterQuery,
                final List<LookupResultItem> results
        ) {
            this.namedIndexReader = namedIndexReader;
            this.query = query;
            this.suggesterQuery = suggesterQuery;
            this.results = results;
        }

        @Override
        public Void call() {
            try {
                started = true;

                SuggesterProjectData data = projectDataMap.get(namedIndexReader.name);
                if (data == null) {
                    LOGGER.log(Level.FINE, "{0} not yet initialized", namedIndexReader.name);
                    return null;
                }
                boolean gotLock = data.tryLock();
                if (!gotLock) { // do not wait for rebuild
                    return null;
                }

                try {
                    SuggesterSearcher searcher = new SuggesterSearcher(namedIndexReader.reader, resultSize);

                    List<LookupResultItem> resultItems = searcher.suggest(query, namedIndexReader.name, suggesterQuery,
                            data.getSearchCounts(suggesterQuery.getField()));

                    synchronized (results) {
                        results.addAll(resultItems);
                    }
                } finally {
                    data.unlock();
                }
            } finally {
                synchronized (this) {
                    finished = true;
                    this.notifyAll();
                }
            }
            return null;
        }
    }

    /**
     * Result suggestions data.
     */
    public static class Suggestions {

        private final List<LookupResultItem> items;
        private final boolean partialResult;

        public Suggestions(final List<LookupResultItem> items, final boolean partialResult) {
            this.items = items;
            this.partialResult = partialResult;
        }

        public List<LookupResultItem> getItems() {
            return items;
        }

        public boolean isPartialResult() {
            return partialResult;
        }
    }

    /**
     * Model classes for holding project name and path to its index directory.
     */
    public static class NamedIndexDir {

        /**
         * Name of the project.
         */
        private final String name;

        /**
         * Path to index directory for project with name {@link #name}.
         */
        private final Path path;

        public NamedIndexDir(final String name, final Path path) {
            if (name == null) {
                throw new IllegalArgumentException("Name cannot be null");
            }
            if (path == null) {
                throw new IllegalArgumentException("Path cannot be null");
            }

            this.name = name;
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public Path getPath() {
            return path;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (!(obj instanceof NamedIndexDir)) {
                return false;
            }

            NamedIndexDir that = (NamedIndexDir) obj;
            return (this.name.equals(that.name) && this.path.equals(that.path));
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name, this.path);
        }
    }

    /**
     * Model class to hold the project name and its {@link IndexReader}.
     */
    public static class NamedIndexReader {

        /**
         * Name of the project.
         */
        private final String name;

        /**
         * IndexReader of the project with {@link #name}.
         */
        private final IndexReader reader;

        public NamedIndexReader(final String name, final IndexReader reader) {
            if (name == null) {
                throw new IllegalArgumentException("Name cannot be null");
            }
            if (reader == null) {
                throw new IllegalArgumentException("Reader cannot be null");
            }

            this.name = name;
            this.reader = reader;
        }

        public String getName() {
            return name;
        }

        public IndexReader getReader() {
            return reader;
        }

        @Override
        public String toString() {
            return name;
        }

    }

    private static class BooleanWrapper {

        private volatile boolean value;

    }
}
