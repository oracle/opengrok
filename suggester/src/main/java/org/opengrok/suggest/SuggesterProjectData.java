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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds all the necessary data for one index directory. In the context of OpenGrok it is one project.
 */
class SuggesterProjectData implements Closeable {

    private static final String TMP_DIR_PROPERTY = "java.io.tmpdir";

    private static final Logger logger = Logger.getLogger(SuggesterProjectData.class.getName());

    private static final int MAX_TERM_SIZE = Short.MAX_VALUE - 3;

    private static final String WFST_TEMP_FILE_PREFIX = "opengrok_suggester_wfst";

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

    private Set<String> fields;

    private final Directory tempDir;

    SuggesterProjectData(
            final Directory indexDir,
            final Path suggesterDir,
            final boolean allowMostPopular,
            final Set<String> fields
    ) throws IOException {
        this.indexDir = indexDir;
        this.suggesterDir = suggesterDir;
        this.allowMostPopular = allowMostPopular;

        tempDir = FSDirectory.open(Paths.get(System.getProperty(TMP_DIR_PROPERTY)));

        initFields(fields);
    }

    private void initFields(final Set<String> fields) throws IOException {
        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            Collection<String> indexedFields = FieldInfos.getIndexedFields(indexReader);
            if (fields == null) {
                this.fields = new HashSet<>(indexedFields);
            } else if (!indexedFields.containsAll(fields)) {
                Set<String> copy = new HashSet<>(fields);
                copy.removeAll(indexedFields);
                logger.log(Level.WARNING,
                        "Fields {0} will be ignored because they were not found in index directory {1}",
                        new Object[] {copy, indexDir});

                copy = new HashSet<>(fields);
                copy.retainAll(indexedFields);
                this.fields = copy;
            } else {
                this.fields = new HashSet<>(fields);
            }
        }
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
            for (String field : fields) {

                File WFSTfile = getWFSTFile(field);
                if (WFSTfile.exists()) {
                    WFSTCompletionLookup WFST = loadStoredWFST(WFSTfile);
                    lookups.put(field, WFST);
                } else {
                    logger.log(Level.INFO, "Missing WFST file for {0} field in {1}, creating a new one",
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

    private WFSTCompletionLookup createWFST() {
        return new WFSTCompletionLookup(tempDir, WFST_TEMP_FILE_PREFIX);
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
            for (String field : fields) {
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

        if (lookup.getCount() > 0) {
            double averageLength = (double) iterator.termLengthAccumulator / lookup.getCount();
            averageLengths.put(field, averageLength);
        }

        return lookup;
    }

    private void store(final WFSTCompletionLookup WFST, final String field) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(getWFSTFile(field))) {
            WFST.store(fos);
        }
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
        searchCountMaps.values().forEach(PopularityMap::close);
        searchCountMaps.clear();

        for (String field : fields) {
            int numEntries = (int) lookups.get(field).getCount();
            if (numEntries == 0) {
                logger.log(Level.FINE, "Skipping creation of ChronicleMap for field " + field + " in directory "
                        + suggesterDir + " due to zero number of entries");
                continue;
            }

            ChronicleMapConfiguration conf = ChronicleMapConfiguration.load(suggesterDir, field);
            if (conf == null) { // it was not yet initialized
                conf = new ChronicleMapConfiguration(numEntries, getAverageLength(field));
                conf.save(suggesterDir, field);
            }

            File f = getChronicleMapFile(field);

            ChronicleMapAdapter m;
            try {
                m = new ChronicleMapAdapter(field, conf.getAverageKeySize(), conf.getEntries(), f);
            } catch (IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Could not create ChronicleMap for field " + field + " in directory "
                        + suggesterDir + " due to invalid key size ("
                        + conf.getAverageKeySize() + ") or number of entries: (" + conf.getEntries() + "):", e);
                return;
            } catch (Throwable t) {
                logger.log(Level.SEVERE,
                        "Could not create ChronicleMap for field " + field + " in directory "
                                + suggesterDir + " , most popular completion disabled, if you are using "
                                + "JDK9+ make sure to specify: "
                                + "--add-exports java.base/jdk.internal.ref=ALL-UNNAMED "
                                + "--add-exports java.base/jdk.internal.misc=ALL-UNNAMED "
                                + "--add-exports java.base/sun.nio.ch=ALL-UNNAMED", t);
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

    private File getChronicleMapFile(final String field) {
        return suggesterDir.resolve(field + "_" + SEARCH_COUNT_MAP_NAME).toFile();
    }

    private double getAverageLength(final String field) {
        if (averageLengths.containsKey(field)) {
            return averageLengths.get(field);
        }
        logger.log(Level.FINE, "Could not determine average length for field {0}, using default one", field);
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
            WFSTCompletionLookup lookup = lookups.get(field);
            if (lookup == null) {
                logger.log(Level.WARNING, "No WFST for field {0} in {1}", new Object[] {field, suggesterDir});
                return Collections.emptyList();
            }
            return lookup.lookup(prefix, false, resultSize);
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
     * @return false if update failed, otherwise true
     */
    public boolean incrementSearchCount(final Term term, final int value) {
        return incrementSearchCount(term, value, false);
    }

    boolean incrementSearchCount(final Term term, final int value, boolean waitForLock) {
        if (term == null) {
            throw new IllegalArgumentException("Cannot increment search count for null");
        }

        boolean ret = false;
        boolean gotLock;
        if (waitForLock) {
            lock.readLock().lock();
        } else {
            gotLock = lock.readLock().tryLock();
            if (!gotLock) {
                logger.log(Level.INFO, "Cannot increment search count for term {0} in {1}, rebuild in progress",
                        new Object[]{term, suggesterDir});
                return false;
            }
        }

        try {
            if (lookups.get(term.field()).get(term.text()) == null) {
                logger.log(Level.WARNING, "Cannot increment search count for unknown term {0} in {1}",
                        new Object[]{term, suggesterDir});
                return false; // unknown term
            }

            PopularityMap map = searchCountMaps.get(term.field());
            if (map != null) {
                map.increment(term.bytes(), value);
                ret = true;
            }
        } finally {
            lock.readLock().unlock();
        }
        return ret;
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

            tempDir.close();
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
     * Returns the searched terms sorted according to their popularity.
     * @param field field for which to return the data
     * @param page which page of data to retrieve
     * @param pageSize number of results to return
     * @return list of terms with their popularity
     */
    public List<Entry<BytesRef, Integer>> getSearchCountsSorted(final String field, int page, int pageSize) {
        lock.readLock().lock();
        try {
            PopularityMap map = searchCountMaps.get(field);
            if (map == null) {
                logger.log(Level.FINE, "No search count map initialized for field {0}", field);
                return Collections.emptyList();
            }

            return map.getPopularityData(page, pageSize);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String toString() {
        return "SuggesterProjectData{" +
                "indexDir=" + indexDir +
                ", suggesterDir=" + suggesterDir +
                ", allowMostPopular=" + allowMostPopular +
                '}';
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
