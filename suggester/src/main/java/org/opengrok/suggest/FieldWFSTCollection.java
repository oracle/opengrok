package org.opengrok.suggest;

import net.openhft.chronicle.map.ChronicleMap;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.fst.WFSTCompletionLookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

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

class FieldWFSTCollection {

    private static final Logger logger = Logger.getLogger(FieldWFSTCollection.class.getName());

    private static final int MAXIMUM_TERM_SIZE = Short.MAX_VALUE - 3;

    private static final String TEMP_DIR_PREFIX = "opengrok";

    private static final String WFST_FILE_SUFFIX = ".wfst";

    private static final int DEFAULT_WEIGHT = 0;

    private Directory indexDir;

    private Path suggesterDir;

    public final Map<String, WFSTCompletionLookup> map = new HashMap<>();

    public final Map<String, ChronicleMap<String, Integer>> map2 = new HashMap<>();

    FieldWFSTCollection(Directory indexDir, Path suggesterDir) {
        this.indexDir = indexDir;
        this.suggesterDir = suggesterDir;
    }

    public void init() throws IOException {
        if (hasStoredData()) {
            loadStoredWFSTs();
        } else {

            boolean directoryCreated = suggesterDir.toFile().mkdirs();
            if (!directoryCreated) {
                throw new IOException("Could not create suggester directory " + suggesterDir);
            }

            rebuild();
        }

        File f = Paths.get(suggesterDir.toString(), "searchbased_chronicle.db").toFile();

        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            for (String field : MultiFields.getIndexedFields(indexReader)) {
                ChronicleMap<String, Integer> m = ChronicleMap.of(String.class, Integer.class)
                        .name(field)
                        .averageKey("my_phrase")
                        .entries(map.get(field).getCount())
                        .createOrRecoverPersistedTo(f);


                map2.put(field, m);
            }
        }
    }

    private boolean hasStoredData() {
        return suggesterDir.toFile().exists();
    }

    private void loadStoredWFSTs() throws IOException {
        try (IndexReader indexReader = DirectoryReader.open(indexDir)) {
            for (String field : MultiFields.getIndexedFields(indexReader)) {

                File WFSTfile = getWFSTFile(field);
                if (WFSTfile.exists()) {
                    WFSTCompletionLookup WFST = loadStoredWFST(WFSTfile);
                    map.put(field, WFST);
                } else {
                    logger.log(Level.INFO, "Missing FieldWFSTCollection file for {0} field in {1}, creating a new one",
                            new Object[] {field, suggesterDir});

                    WFSTCompletionLookup lookup = build(indexReader, field);
                    store(lookup, field);

                    map.put(field, lookup);
                }
            }
        }
    }

    private WFSTCompletionLookup loadStoredWFST(final File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);

        WFSTCompletionLookup lookup = createWFST();
        lookup.load(fis);
        return lookup;
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

                map.put(field, lookup);
            }
        }
    }

    private WFSTCompletionLookup build(final IndexReader indexReader, final String field) throws IOException {
        InputIterator iterator = new MyInputIterator(
                new LuceneDictionary(indexReader, field).getEntryIterator(), indexReader, field);

        WFSTCompletionLookup lookup = createWFST();
        lookup.build(iterator);

        return lookup;
    }

    private void store(final WFSTCompletionLookup WFST, final String field) throws IOException {
        FileOutputStream fos = new FileOutputStream(getWFSTFile(field));

        WFST.store(fos);
    }

    public List<Lookup.LookupResult> lookup(final String field, final String prefix, final int resultSize) {
        try {
            return map.get(field).lookup(prefix, false, resultSize);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not perform lookup in {0} for {1}:{2}",
                    new Object[] {suggesterDir, field, prefix});
        }
        return Collections.emptyList();
    }

    public void remove() {
        boolean deleteSuccessful = suggesterDir.toFile().delete();
        if (!deleteSuccessful) {
            logger.log(Level.WARNING, "Cannot remove suggester data: {0}", suggesterDir);
        }
    }

    private static class MyInputIterator implements InputIterator {

        private final InputIterator wrapped;

        private final IndexReader indexReader;

        private final String field;

        MyInputIterator(final InputIterator wrapped, final IndexReader indexReader, final String field) {
            this.wrapped = wrapped;
            this.indexReader = indexReader;
            this.field = field;
        }

        private BytesRef last;

        @Override
        public long weight() {
            if (last != null) {
                return SuggesterUtils.computeWeight(indexReader, field, last);
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
            while (last != null && last.length > MAXIMUM_TERM_SIZE) {
                last = wrapped.next();
            }
            return last;
        }
    }

}
