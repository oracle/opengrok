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

import net.openhft.chronicle.hash.ChronicleHash;
import net.openhft.chronicle.map.ChronicleMap;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
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
import org.opengrok.suggest.data.SearchCountMap;
import org.opengrok.suggest.util.ChronicleMapConfiguration;
import org.opengrok.suggest.util.ChronicleMapUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class FieldWFSTCollection implements Closeable {

    private static final Logger logger = Logger.getLogger(FieldWFSTCollection.class.getName());

    private static final int MAX_TERM_SIZE = Short.MAX_VALUE - 3;

    private static final String TEMP_DIR_PREFIX = "opengrok";

    private static final String WFST_FILE_SUFFIX = ".wfst";

    private static final String SEARCH_COUNT_MAP_NAME = "search_count.db";

    private static final int DEFAULT_WEIGHT = 0;

    private static final double AVERAGE_LENGTH_DEFAULT = 22;

    private Directory indexDir;

    private Path suggesterDir;

    private final Map<String, WFSTCompletionLookup> lookups = new HashMap<>();

    private final Map<String, ChronicleMap<String, Integer>> searchCountMaps = new HashMap<>();

    private final Map<String, Double> averageLengths = new HashMap<>();

    private boolean allowMostPopular;

    FieldWFSTCollection(final Directory indexDir, final Path suggesterDir, final boolean allowMostPopular) {
        this.indexDir = indexDir;
        this.suggesterDir = suggesterDir;
        this.allowMostPopular = allowMostPopular;
    }

    public void init() throws IOException {
        if (hasStoredData()) {
            loadStoredWFSTs();
        } else {
            if (!suggesterDir.toFile().exists()) {
                boolean directoryCreated = suggesterDir.toFile().mkdirs();
                if (!directoryCreated) {
                    throw new IOException("Could not create suggester directory " + suggesterDir);
                }
            }

            rebuild();
        }

        if (allowMostPopular) {
            initSearchCountMap();
        }
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
        return Paths.get(suggesterDir.toString(), field + WFST_FILE_SUFFIX).toFile();
    }

    public void rebuild() throws IOException {
        build();
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
                new LuceneDictionary(indexReader, field).getEntryIterator(), indexReader, field, getSearchCountMap(field));

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

    private void initSearchCountMap() throws IOException {
        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            for (String field : MultiFields.getIndexedFields(indexReader)) {
                ChronicleMapConfiguration conf = ChronicleMapConfiguration.load(suggesterDir, field);
                if (conf == null) { // it was not yet initialized
                    conf = new ChronicleMapConfiguration((int) lookups.get(field).getCount(), getAverageLength(field));
                    conf.save(suggesterDir, field);
                }

                File f = getChronicleMapFile(field);

                ChronicleMap<String, Integer> m = ChronicleMap.of(String.class, Integer.class)
                        .name(field)
                        .averageKeySize(conf.getAverageKeySize())
                        .entries(conf.getEntries())
                        .createOrRecoverPersistedTo(f);

                removeOldTerms(m, lookups.get(field));

                if (conf.getEntries() < lookups.get(field).getCount()) {
                    int newEntriesCount = (int) lookups.get(field).getCount();
                    double newKeyAvgLength = getAverageLength(field);

                    conf.setEntries(newEntriesCount);
                    conf.setAverageKeySize(newKeyAvgLength);
                    conf.save(suggesterDir, field);

                    m = ChronicleMapUtils.resize(f, m, newEntriesCount, newKeyAvgLength);

                    searchCountMaps.put(field, m);
                } else {
                    searchCountMaps.put(field, m);
                }
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

    private void removeOldTerms(final ChronicleMap<String, Integer> map, final WFSTCompletionLookup lookup) {
        if (!map.isEmpty()) {
            map.entrySet().removeIf(e -> lookup.get(e.getKey()) == null);
        }
    }

    public List<Lookup.LookupResult> lookup(final String field, final String prefix, final int resultSize) {
        try {
            return lookups.get(field).lookup(prefix, false, resultSize);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not perform lookup in {0} for {1}:{2}",
                    new Object[] {suggesterDir, field, prefix});
        }
        return Collections.emptyList();
    }

    public void remove() {
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
    }

    public void incrementSearchCount(final Term term) {
        ChronicleMap<String, Integer> m = searchCountMaps.get(term.field());
        if (m != null) {
            m.merge(term.text(), 1, (a, b) -> a + b);
        }
    }

    public SearchCountMap getSearchCountMap(final String field) {
        if (!searchCountMaps.containsKey(field)) {
            return (key) -> 0;
        }

        return (key) -> searchCountMaps.get(field).getOrDefault(key, 0);
    }

    @Override
    public void close() throws IOException {
        searchCountMaps.values().forEach(ChronicleHash::close);
        indexDir.close();
    }

    private static class WFSTInputIterator implements InputIterator {

        private final InputIterator wrapped;

        private final IndexReader indexReader;

        private final String field;

        private long termLengthAccumulator = 0;

        private final SearchCountMap searchCounts;

        WFSTInputIterator(
                final InputIterator wrapped,
                final IndexReader indexReader,
                final String field,
                final SearchCountMap searchCounts
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
                String str = last.utf8ToString();

                int add = searchCounts.get(str);

                return SuggesterUtils.computeWeight(indexReader, field, last)
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
                // it might be a little bigger because of UTF8 but overestimating is fine
                // source code is almost always in English so there should not be much overhead
                termLengthAccumulator += last.length;
            }

            return last;
        }
    }

}
