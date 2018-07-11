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

import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.fst.WFSTCompletionLookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.opengrok.suggest.popular.PopularityCounter;
import org.opengrok.suggest.popular.PopularityMap;
import org.opengrok.suggest.popular.impl.chronicle.ChronicleMapAdapter;
import org.opengrok.suggest.popular.impl.chronicle.ChronicleMapConfiguration;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds all the necessary data for one index directory. In the context of OpenGrok it is one project.
 */
class FieldWFSTCollection implements Closeable {

    private static final Logger logger = Logger.getLogger(FieldWFSTCollection.class.getName());

    private static final int MAX_TERM_SIZE = Short.MAX_VALUE - 3;

    private static final String TEMP_DIR_PREFIX = "opengrok";

    private static final String WFST_FILE_SUFFIX = ".wfst";

    private static final String SEARCH_COUNT_MAP_NAME = "search_count.db";

    private static final String VERSION_FILE_NAME = "version.txt";

    private static final int DEFAULT_WEIGHT = 0;

    private static final double AVERAGE_LENGTH_DEFAULT = 22;

    private Directory indexDir;

    private Path suggesterDir;

    private final Map<String, WFSTCompletionLookup> lookups = new HashMap<>();

    private final Map<String, PopularityMap> searchCountMaps = new HashMap<>();

    private final Map<String, Double> averageLengths = new HashMap<>();

    private boolean allowMostPopular;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    FieldWFSTCollection(final Directory indexDir, final Path suggesterDir, final boolean allowMostPopular) {
        this.indexDir = indexDir;
        this.suggesterDir = suggesterDir;
        this.allowMostPopular = allowMostPopular;
    }

    /**
     * Initializes the data structure. Rebuild is launched only if necessary.
     * @throws IOException if initialization was not successful
     */
    public void init() throws IOException {
        lock.writeLock().lock();
        try {
            long commitVersion = getCommitVersion();

            if (hasStoredData() && commitVersion == getDataVersion()) {
                loadStoredWFSTs();
            } else {
                createSuggesterDir();
                build();
            }

            if (allowMostPopular) {
                initSearchCountMap();
            }

            storeDataVersion(commitVersion);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private long getCommitVersion() throws IOException {
        List<IndexCommit> commits = DirectoryReader.listCommits(indexDir);
        if (commits.size() > 1) {
            throw new IllegalStateException("IndexDeletionPolicy changed, normally only one commit should be stored");
        }
        IndexCommit commit = commits.get(0);

        return commit.getGeneration();
    }

    private boolean hasStoredData() {
        if (!suggesterDir.toFile().exists()) {
            return false;
        }

        File[] children = suggesterDir.toFile().listFiles();
        return children != null && children.length > 0;
    }

    private void loadStoredWFSTs() throws IOException {
        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            for (String field : MultiFields.getIndexedFields(indexReader)) {

                File WFSTfile = getWFSTFile(field);
                if (WFSTfile.exists()) {
                    WFSTCompletionLookup WFST = loadStoredWFST(WFSTfile);
                    lookups.put(field, WFST);
                } else {
                    logger.log(Level.INFO, "Missing FieldWFSTCollection file for {0} field in {1}, creating a new one",
                            new Object[] {field, suggesterDir});

                    WFSTCompletionLookup lookup = build(indexReader, field);
                    store(lookup, field);

                    lookups.put(field, lookup);
                }
            }
        }
    }

    private WFSTCompletionLookup loadStoredWFST(final File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            WFSTCompletionLookup lookup = createWFST();
            lookup.load(fis);
            return lookup;
        }
    }

    private WFSTCompletionLookup createWFST() throws IOException {
        return new WFSTCompletionLookup(FSDirectory.open(Files.createTempDirectory(TEMP_DIR_PREFIX)), TEMP_DIR_PREFIX);
    }

    private File getWFSTFile(final String field) {
        return getFile(field + WFST_FILE_SUFFIX);
    }

    private File getFile(final String fileName) {
        return suggesterDir.resolve(fileName).toFile();
    }

    /**
     * Forces the rebuild of the data structure.
     * @throws IOException if some error occurred
     */
    public void rebuild() throws IOException {
        lock.writeLock().lock();
        try {
            build();

            if (allowMostPopular) {
                initSearchCountMap();
            }

            storeDataVersion(getCommitVersion());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void build() throws IOException {
        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            for (String field : MultiFields.getIndexedFields(indexReader)) {
                WFSTCompletionLookup lookup = build(indexReader, field);
                store(lookup, field);

                lookups.put(field, lookup);
            }
        }
    }

    private WFSTCompletionLookup build(final IndexReader indexReader, final String field) throws IOException {
        WFSTInputIterator iterator = new WFSTInputIterator(
                new LuceneDictionary(indexReader, field).getEntryIterator(), indexReader, field, getSearchCounts(field));

        WFSTCompletionLookup lookup = createWFST();
        lookup.build(iterator);

        double averageLength = (double) iterator.termLengthAccumulator / lookup.getCount();
        averageLengths.put(field, averageLength);

        return lookup;
    }

    private void store(final WFSTCompletionLookup WFST, final String field) throws IOException {
        FileOutputStream fos = new FileOutputStream(getWFSTFile(field));

        WFST.store(fos);
    }

    private void createSuggesterDir() throws IOException {
        if (!suggesterDir.toFile().exists()) {
            boolean directoryCreated = suggesterDir.toFile().mkdirs();
            if (!directoryCreated) {
                throw new IOException("Could not create suggester directory " + suggesterDir);
            }
        }
    }

    private void initSearchCountMap() throws IOException {
        searchCountMaps.clear();
        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            for (String field : MultiFields.getIndexedFields(indexReader)) {
                ChronicleMapConfiguration conf = ChronicleMapConfiguration.load(suggesterDir, field);
                if (conf == null) { // it was not yet initialized
                    conf = new ChronicleMapConfiguration((int) lookups.get(field).getCount(), getAverageLength(field));
                    conf.save(suggesterDir, field);
                }

                File f = getChronicleMapFile(field);

                ChronicleMapAdapter m;
                try {
                    m = new ChronicleMapAdapter(field, conf.getAverageKeySize(), conf.getEntries(), f);
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                            "Could not create ChronicleMap, most popular completion disabled, if you are using "
                                    + "JDK9+ make sure to specify: "
                                    + "--add-exports java.base/jdk.internal.ref=ALL-UNNAMED "
                                    + "--add-exports java.base/jdk.internal.misc=ALL-UNNAMED "
                                    + "--add-exports java.base/sun.nio.ch=ALL-UNNAMED", e);
                    return;
                }

                if (getCommitVersion() != getDataVersion()) {
                    removeOldTerms(m, lookups.get(field));

                    if (conf.getEntries() < lookups.get(field).getCount()) {
                        int newEntriesCount = (int) lookups.get(field).getCount();
                        double newKeyAvgLength = getAverageLength(field);

                        conf.setEntries(newEntriesCount);
                        conf.setAverageKeySize(newKeyAvgLength);
                        conf.save(suggesterDir, field);

                        m.resize(newEntriesCount, newKeyAvgLength);
                    }
                }
                searchCountMaps.put(field, m);
            }
        }
    }

    private File getChronicleMapFile(final String field) {
        return suggesterDir.resolve(field + "_" + SEARCH_COUNT_MAP_NAME).toFile();
    }

    private double getAverageLength(final String field) {
        if (averageLengths.containsKey(field)) {
            return averageLengths.get(field);
        }
        return AVERAGE_LENGTH_DEFAULT;
    }

    private void removeOldTerms(final ChronicleMapAdapter adapter, final WFSTCompletionLookup lookup) {
        adapter.removeIf(key -> lookup.get(key.toString()) == null);
    }

    /**
     * Looks up the terms in the WFST data structure.
     * @param field term field
     * @param prefix prefix the returned terms must contain
     * @param resultSize number of terms to return
     * @return terms with highest score
     */
    public List<Lookup.LookupResult> lookup(final String field, final String prefix, final int resultSize) {
        lock.readLock().lock();
        try {
            return lookups.get(field).lookup(prefix, false, resultSize);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not perform lookup in {0} for {1}:{2}",
                    new Object[] {suggesterDir, field, prefix});
        } finally {
            lock.readLock().unlock();
        }
        return Collections.emptyList();
    }

    /**
     * Removes all stored data structures.
     */
    public void remove() {
        lock.writeLock().lock();
        try {
            try {
                close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not close opened index directory {0}", indexDir);
            }

            try {
                FileUtils.deleteDirectory(suggesterDir.toFile());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Cannot remove suggester data: {0}", suggesterDir);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Increments search count for {@code term} by 1.
     * @param term term for which to increment search count
     */
    public void incrementSearchCount(final Term term) {
        incrementSearchCount(term, 1);
    }

    /**
     * Increments search count for {@code term} by {@code value}.
     * @param term term for which to increment search count
     * @param value value to increment by
     */
    public void incrementSearchCount(final Term term, final int value) {
        if (term == null) {
            throw new IllegalArgumentException("Cannot increment search count for null");
        }

        lock.readLock().lock();
        try {
            if (lookups.get(term.field()).get(term.text()) == null) {
                throw new IllegalArgumentException("Unknown term " + term);
            }

            PopularityMap map = searchCountMaps.get(term.field());
            if (map != null) {
                map.increment(term.bytes(), value);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns search counts for term field. For the time the returned data structure is used this object needs to be
     * locked by {@link #tryLock()}.
     * @param field term field
     * @return search counts object
     */
    public PopularityCounter getSearchCounts(final String field) {
        if (!searchCountMaps.containsKey(field)) {
            return key -> 0;
        }

        return key -> searchCountMaps.get(field).get(key);
    }

    /**
     * Closes the open data structures.
     * @throws IOException if the index directory could not be closed
     */
    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            searchCountMaps.values().forEach(val -> {
                try {
                    val.close();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Could not properly close most popular completion data", e);
                }
            });
            indexDir.close();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private long getDataVersion() {
        File versionFile = getFile(VERSION_FILE_NAME);
        if (!versionFile.exists()) {
            return -1;
        }

        try {
            String str = FileUtils.readFileToString(versionFile, StandardCharsets.UTF_8);
            return Long.parseLong(str);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read suggester data version", e);
        }
        return -1;
    }

    private void storeDataVersion(final long version) {
        try {
            FileUtils.writeStringToFile(getFile(VERSION_FILE_NAME), "" + version, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not store version", e);
        }
    }

    /**
     * Tries to lock the inner data structures for reading, so far only for {@link #getSearchCounts(String)}.
     * @return {@code true} if lock was acquired, {@code false} otherwise
     */
    public boolean tryLock() {
        return lock.readLock().tryLock();
    }

    /**
     * Unlocks the inner data structures for reading.
     */
    public void unlock() {
        lock.readLock().unlock();
    }

    /**
     * An {@link InputIterator} for WFST data structure with most popular completion support.
     */
    private static class WFSTInputIterator implements InputIterator {

        private final InputIterator wrapped;

        private final IndexReader indexReader;

        private final String field;

        private long termLengthAccumulator = 0;

        private final PopularityCounter searchCounts;

        WFSTInputIterator(
                final InputIterator wrapped,
                final IndexReader indexReader,
                final String field,
                final PopularityCounter searchCounts
        ) {
            this.wrapped = wrapped;
            this.indexReader = indexReader;
            this.field = field;
            this.searchCounts = searchCounts;
        }

        private BytesRef last;

        @Override
        public long weight() {
            if (last != null) {
                int add = searchCounts.get(last);

                return SuggesterUtils.computeScore(indexReader, field, last)
                        + add * SuggesterSearcher.TERM_ALREADY_SEARCHED_MULTIPLIER;
            }

            return DEFAULT_WEIGHT;
        }

        @Override
        public BytesRef payload() {
            return wrapped.payload();
        }

        @Override
        public boolean hasPayloads() {
            return wrapped.hasPayloads();
        }

        @Override
        public Set<BytesRef> contexts() {
            return wrapped.contexts();
        }

        @Override
        public boolean hasContexts() {
            return wrapped.hasContexts();
        }

        @Override
        public BytesRef next() throws IOException {
            last = wrapped.next();

            // skip very large terms because of the buffer exception
            while (last != null && last.length > MAX_TERM_SIZE) {
                last = wrapped.next();
            }

            if (last != null) {
                termLengthAccumulator += last.length;
            }

            return last;
        }
    }

}
