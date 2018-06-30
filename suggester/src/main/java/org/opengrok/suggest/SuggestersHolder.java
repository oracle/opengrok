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

import net.openhft.chronicle.map.ChronicleMap;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.opengrok.suggest.query.SuggesterPrefixQuery;
import org.opengrok.suggest.query.SuggesterQuery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public final class SuggestersHolder {

    private static final Logger logger = Logger.getLogger(SuggestersHolder.class.getName());

    private static final int AWAIT_TERMINATION_TIME_SECONDS = 180;

    private static final int DEFAULT_RESULT_SIZE = 10;

    private final Map<String, FieldWFSTCollection> map = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private final File suggesterDir;

    public SuggestersHolder(final File suggesterDir) {
        if (suggesterDir == null) {
            throw new IllegalArgumentException("Suggester needs to have directory specified");
        }
        if (suggesterDir.exists() && !suggesterDir.isDirectory()) {
            throw new IllegalArgumentException("Provided directory is not a directory");
        }

        this.suggesterDir = suggesterDir;
    }

    public void init(final Collection<Path> luceneIndexes) {
        if (luceneIndexes == null || luceneIndexes.isEmpty()) {
            logger.log(Level.INFO, "No index directories found, exiting...");
            return;
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

    private Runnable getInitRunnable(final Path indexDir) {
        return () -> {
            try {
                logger.log(Level.FINE, "Initializing {0}", indexDir);

                FieldWFSTCollection wfst = new FieldWFSTCollection(FSDirectory.open(indexDir), Paths.get(
                        SuggestersHolder.this.suggesterDir.getAbsolutePath(), indexDir.getFileName().toString()));
                wfst.init();
                map.put(indexDir.getFileName().toString(), wfst);

                logger.log(Level.FINE, "Finished initialization for {0}", indexDir);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Could not initialize suggester data for " + indexDir, e);
            }
        };
    }

    private boolean indexExists(final Path indexDir) throws IOException {
        try (Directory indexDirectory = FSDirectory.open(indexDir)) {
            return DirectoryReader.indexExists(indexDirectory);
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

    private void shutdownAndAwaitTermination(final ExecutorService executorService, final String logMessageOnSuccess) {
        executorService.shutdown();
        try {
            executorService.awaitTermination(AWAIT_TERMINATION_TIME_SECONDS, TimeUnit.SECONDS);
            logger.log(Level.INFO, logMessageOnSuccess);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Interrupted while building suggesters", e);
        }
    }

    public void rebuild(final List<Path> indexDirs) {
        if (indexDirs == null || indexDirs.isEmpty()) {
            logger.log(Level.INFO, "Not rebuilding suggester data because no index directories were specified");
            return;
        }

        synchronized (lock) {
            logger.log(Level.INFO, "Rebuilding following suggesters: {0}", indexDirs);

            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            for (Path indexDir : indexDirs) {
                FieldWFSTCollection fieldsWFST = map.get(indexDir.getFileName().toString());
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

    public void remove(final Iterable<String> suggesterNames) {
        if (suggesterNames == null) {
            return;
        }

        synchronized (lock) {
            logger.log(Level.INFO, "Removing following suggesters: {0}", suggesterNames);

            for (String suggesterName : suggesterNames) {
                FieldWFSTCollection collection = map.get(suggesterName);
                if (collection == null) {
                    logger.log(Level.WARNING, "Unknown suggester {0}", suggesterName);
                    continue;
                }
                collection.remove();
                map.remove(suggesterName);
            }
        }
    }

    public List<LookupResultItem> search(
            final List<NamedIndexReader> suggesters,
            final SuggesterQuery suggesterQuery,
            final Query query
    ) {
        if (suggesters == null || suggesterQuery == null) {
            return Collections.emptyList();
        }

        boolean isOnlySuggestQuery = query == null;

        List<LookupResultItem> results = suggesters.parallelStream().flatMap(namedIndexReader -> {

            if (isOnlySuggestQuery && suggesterQuery instanceof SuggesterPrefixQuery) {
                String prefix = ((SuggesterPrefixQuery) suggesterQuery).getPrefix().text();
                return map.get(namedIndexReader.name)
                        .lookup(suggesterQuery.getField(), prefix, DEFAULT_RESULT_SIZE)
                        .stream()
                        .map(item -> new LookupResultItem(item.key.toString(), namedIndexReader.name, item.value));
            } else {
                SuggesterSearcher searcher = new SuggesterSearcher(namedIndexReader.reader, DEFAULT_RESULT_SIZE);

                List<LookupResultItem> resultItems = searcher.search(query, namedIndexReader.name, suggesterQuery, map.get(namedIndexReader.name).map2.get(suggesterQuery.getField()));

                return resultItems.stream();
            }
        }).collect(Collectors.toList());


        return SuggesterUtils.combineResults(results, DEFAULT_RESULT_SIZE);
    }

    public void onSearch(Iterable<String> projects, Query query) {
        List<Term> terms = SuggesterUtils.intoTerms(query);

        for (String project : projects) {

            for (Term t : terms) {
                ChronicleMap<String, Integer> m = map.get(project).map2.get(t.field());

                m.merge(t.text(), 1, (a, b) -> a + b);
            }
        }
    }

    public static class NamedIndexReader {

        private final String name;

        private final IndexReader reader;

        public NamedIndexReader(String name, IndexReader reader) {
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
