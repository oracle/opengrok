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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;
import org.opengrok.suggest.query.SuggesterPrefixQuery;
import org.opengrok.suggest.query.SuggesterWildcardQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggesterTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private static class SuggesterTestData {

        private Suggester s;
        private Path indexDir;
        private Path suggesterDir;
        private final List<Suggester.NamedIndexReader> namedIndexReaders = new ArrayList<>();

        private void close() throws IOException {
            for (Suggester.NamedIndexReader ir: namedIndexReaders) {
                ir.getReader().close();
            }
            s.close();
            FileUtils.deleteDirectory(indexDir.toFile());
            FileUtils.deleteDirectory(suggesterDir.toFile());
        }

        private Suggester.NamedIndexDir getNamedIndexDir() {
            return new Suggester.NamedIndexDir("test", indexDir);
        }

        private Directory getIndexDirectory() throws IOException {
            return FSDirectory.open(indexDir);
        }

        private Suggester.NamedIndexReader getNamedIndexReader() throws IOException {
            Suggester.NamedIndexReader ir = new Suggester.NamedIndexReader("test", DirectoryReader.open(getIndexDirectory()));
            namedIndexReaders.add(ir);
            return ir;
        }

    }

    @Test
    void testNullSuggesterDir() {
        var terminationDuration = Duration.ofMinutes(5);
        assertThrows(IllegalArgumentException.class,
                () -> new Suggester(null, 10, terminationDuration, false,
                        true, null, Integer.MAX_VALUE, 1, 1, registry,
                        false));
    }

    @Test
    void testNullDuration() {
        assertThrows(IllegalArgumentException.class, () -> createSuggester(null));
    }

    @Test
    void testNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> createSuggester( -4L));
    }

    private void createSuggester(Long duration) throws IOException {
        Path tempFile = Files.createTempFile("opengrok", "test");
        var objDuration = Optional.ofNullable(duration)
                                        .map(Duration::ofMinutes)
                                        .orElse(null);
        try {
            new Suggester(tempFile.toFile(), 10, objDuration, false,
                    true, null, Integer.MAX_VALUE, 1, 1, registry,
                    false);
        } finally {
            tempFile.toFile().delete();
        }
    }

    private SuggesterTestData initSuggester() throws Exception {
        Path tempIndexDir = Files.createTempDirectory("opengrok");
        Directory dir = FSDirectory.open(tempIndexDir);

        addText(dir, "term1 term2 term3");

        dir.close();

        Path tempSuggesterDir = Files.createTempDirectory("opengrok");

        Suggester s = new Suggester(tempSuggesterDir.toFile(), 10, Duration.ofMinutes(1), true,
                true, Collections.singleton("test"), Integer.MAX_VALUE,
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
                registry, false);

        s.init(Collections.singleton(new Suggester.NamedIndexDir("test", tempIndexDir)));
        s.waitForInit(2, TimeUnit.SECONDS);

        SuggesterTestData testData = new SuggesterTestData();
        testData.s = s;
        testData.indexDir = tempIndexDir;
        testData.suggesterDir = tempSuggesterDir;

        return testData;
    }

    private void addText(final Directory dir, final String text) throws IOException {
        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new TextField("test", text, Field.Store.NO));

            iw.addDocument(doc);
        }
    }

    @Test
    void testSimpleSuggestions() throws Exception {
        SuggesterTestData t = initSuggester();

        Suggester.NamedIndexReader ir = t.getNamedIndexReader();

        List<LookupResultItem> res = t.s.search(Collections.singletonList(ir),
                new SuggesterPrefixQuery(new Term("test", "t")), null).getItems();

        assertThat(res.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList()),
                containsInAnyOrder("term1", "term2", "term3"));

        t.close();
    }

    @Test
    void testRefresh() throws Exception {
        SuggesterTestData t = initSuggester();

        addText(t.getIndexDirectory(), "a1 a2");

        t.s.rebuild(Collections.singleton(t.getNamedIndexDir()));

        Suggester.NamedIndexReader ir = t.getNamedIndexReader();

        List<LookupResultItem> res = t.s.search(Collections.singletonList(ir),
                new SuggesterPrefixQuery(new Term("test", "a")), null).getItems();

        assertThat(res.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList()),
                containsInAnyOrder("a1", "a2"));

        t.close();
    }

    @Test
    void testIndexChangedWhileOffline() throws Exception {
        SuggesterTestData t = initSuggester();

        t.s.close();

        addText(t.getIndexDirectory(), "a1 a2");

        t.s = new Suggester(t.suggesterDir.toFile(), 10, Duration.ofMinutes(1), false,
                true, Collections.singleton("test"), Integer.MAX_VALUE,
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
                registry, false);

        t.s.init(Collections.singleton(t.getNamedIndexDir()));
        t.s.waitForInit(2, TimeUnit.SECONDS);

        Suggester.NamedIndexReader ir = t.getNamedIndexReader();

        List<LookupResultItem> res = t.s.search(Collections.singletonList(ir),
                new SuggesterPrefixQuery(new Term("test", "a")), null).getItems();

        assertThat(res.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList()),
                containsInAnyOrder("a1", "a2"));

        t.close();
    }

    @Test
    void testRemove() throws Exception {
        SuggesterTestData t = initSuggester();

        t.s.remove(Collections.singleton("test"));

        assertFalse(t.suggesterDir.resolve("test").toFile().exists());

        FileUtils.deleteDirectory(t.suggesterDir.toFile());
        FileUtils.deleteDirectory(t.indexDir.toFile());
    }

    @Test
    void testComplexQuerySearch() throws Exception {
        SuggesterTestData t = initSuggester();

        List<LookupResultItem> res = t.s.search(Collections.singletonList(t.getNamedIndexReader()),
                new SuggesterWildcardQuery(new Term("test", "*1")), null).getItems();

        assertThat(res.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList()),
                contains("term1"));

        t.close();
    }

    @Test
    @SuppressWarnings("unchecked") // for contains()
    void testOnSearch() throws Exception {
        SuggesterTestData t = initSuggester();

        Query q = new BooleanQuery.Builder()
                .add(new TermQuery(new Term("test", "term1")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("test", "term3")), BooleanClause.Occur.MUST)
                .build();

        t.s.onSearch(Collections.singleton("test"), q);

        List<Entry<BytesRef, Integer>> res = t.s.getSearchCounts("test", "test", 0, 10);

        assertThat(res, containsInAnyOrder(new SimpleEntry<>(new BytesRef("term1"), 1),
                new SimpleEntry<>(new BytesRef("term3"), 1)));

        t.close();
    }

    @Test
    void testGetSearchCountsForUnknown() throws Exception {
        SuggesterTestData t = initSuggester();

        assertTrue(t.s.getSearchCounts("unknown", "unknown", 0, 10).isEmpty());

        t.close();
    }

    @Test
    @SuppressWarnings("unchecked") // for contains()
    void testIncreaseSearchCount() throws Exception {
        SuggesterTestData t = initSuggester();

        t.s.increaseSearchCount("test", new Term("test", "term2"), 100, true);

        List<Entry<BytesRef, Integer>> res = t.s.getSearchCounts("test", "test", 0, 10);

        assertThat(res, contains(new SimpleEntry<>(new BytesRef("term2"), 100)));

        t.close();
    }

    @Test
    void testNamedIndexDirEquals() {
        Suggester.NamedIndexDir namedIndexDir1 = new Suggester.NamedIndexDir("foo", Path.of("/foo"));
        Suggester.NamedIndexDir namedIndexDir2 = new Suggester.NamedIndexDir("foo", Path.of("/foo"));
        assertEquals(namedIndexDir1, namedIndexDir2);
    }

    @Test
    void testNamedIndexDirNotEqualsName() {
        Suggester.NamedIndexDir namedIndexDir1 = new Suggester.NamedIndexDir("foo", Path.of("/foo"));
        Suggester.NamedIndexDir namedIndexDir2 = new Suggester.NamedIndexDir("bar", Path.of("/foo"));
        assertNotEquals(namedIndexDir1, namedIndexDir2);
    }

    @Test
    void testNamedIndexDirNotEqualsPath() {
        Suggester.NamedIndexDir namedIndexDir1 = new Suggester.NamedIndexDir("foo", Path.of("/foo"));
        Suggester.NamedIndexDir namedIndexDir2 = new Suggester.NamedIndexDir("foo", Path.of("/bar"));
        assertNotEquals(namedIndexDir1, namedIndexDir2);
    }
}
