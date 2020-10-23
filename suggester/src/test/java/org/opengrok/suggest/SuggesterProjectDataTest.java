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
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertTrue;

public class SuggesterProjectDataTest {

    private static final String FIELD = "test";

    private Directory dir;

    private Path tempDir;

    private SuggesterProjectData data;

    @Before
    public void setUp() throws IOException {
        dir = new ByteBuffersDirectory();
        tempDir = Files.createTempDirectory("test");
    }

    @After
    public void tearDown() throws IOException {
        if (data != null) {
            data.close();
        }
        if (tempDir.toFile().exists()) {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    public void testLookup() throws IOException {
        addText(FIELD, "term1 term2");

        init(false);

        List<String> suggestions = getSuggestions(FIELD, "t", 10);

        assertThat(suggestions, Matchers.containsInAnyOrder("term1", "term2"));

        data.close();
    }

    private void init(boolean allowMostPopular) throws IOException {
        data = new SuggesterProjectData(dir, tempDir, allowMostPopular, Collections.singleton(FIELD));
        data.init();
    }

    private void addText(final String field, final String text) throws IOException {
        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new TextField(field, text, Field.Store.NO));

            iw.addDocument(doc);
        }
    }

    private List<String> getSuggestions(String field, String prefix, int size) {
        List<Lookup.LookupResult> res = data.lookup(field, prefix, size);

        return res.stream().map(r -> r.key.toString()).collect(Collectors.toList());
    }

    @Test
    public void testMultipleTermsAreFirstByDefault() throws IOException {
        addText(FIELD, "term1 term2 term1");

        init(false);

        List<String> suggestions = getSuggestions(FIELD, "t", 10);

        assertThat(suggestions, Matchers.contains("term1", "term2"));
    }

    @Test
    public void testMostPopularSearch() throws IOException {
        addText(FIELD, "term1 term2 term1");

        init(true);

        data.incrementSearchCount(new Term(FIELD, "term2"));
        data.incrementSearchCount(new Term(FIELD, "term2"));

        data.rebuild();

        List<String> suggestions = getSuggestions(FIELD, "t", 10);

        assertThat(suggestions, Matchers.contains("term2", "term1"));
    }

    @Test
    public void testRebuild() throws IOException {
        addText(FIELD, "term1 term2 term1");

        init(false);

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            iw.deleteAll();
        }

        addText(FIELD, "term3 term4 term5");

        data.rebuild();

        List<String> suggestions = getSuggestions(FIELD, "t", 10);

        assertThat(suggestions, Matchers.containsInAnyOrder("term3", "term4", "term5"));
    }

    @Test
    public void testDifferentPrefixes() throws IOException {
        addText(FIELD, "abc bbc cbc dbc efc gfc");

        init(false);

        List<String> suggestions = getSuggestions(FIELD, "e", 10);

        assertThat(suggestions, Matchers.contains("efc"));
    }

    @Test
    public void testResultSize() throws IOException {
        addText(FIELD, "a1 a2 a3 a4 a5 a6 a7");

        init(false);

        List<String> suggestions = getSuggestions(FIELD, "a", 2);

        assertEquals(2, suggestions.size());
    }

    @Test
    public void incrementTest() throws IOException {
        addText(FIELD, "text");

        init(true);

        data.incrementSearchCount(new Term(FIELD, "text"));

        assertEquals(1, data.getSearchCounts(FIELD).get(new BytesRef("text")));
    }

    @Test
    public void incrementByValueTest() throws IOException {
        addText(FIELD, "some text");

        init(true);

        data.incrementSearchCount(new Term(FIELD, "some"), 20);

        assertEquals(20, data.getSearchCounts(FIELD).get(new BytesRef("some")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incrementByNegativeValueTest() throws IOException {
        addText(FIELD, "another text example");

        init(true);

        data.incrementSearchCount(new Term(FIELD, "example"), -10);
    }

    @Test
    public void rebuildRemoveOldTermsTest() throws IOException {
        addText(FIELD, "term");

        init(true);

        data.incrementSearchCount(new Term(FIELD, "term"), 10);

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            iw.deleteAll();
        }

        addText(FIELD, "term2");

        data.rebuild();

        assertEquals(0, data.getSearchCounts(FIELD).get(new BytesRef("term")));
    }

    @Test
    public void initAfterChangingIndexTest() throws IOException {
        addText(FIELD, "term");
        init(false);

        addText(FIELD, "term2");

        data.init();

        assertThat(getSuggestions(FIELD, "t", 2), containsInAnyOrder("term", "term2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incrementSearchCountNullTest() throws IOException {
        addText(FIELD, "term");
        init(false);

        data.incrementSearchCount(null);
    }

    @Test
    public void getSearchCountMapNullTest() throws IOException {
        addText(FIELD, "term");
        init(true);

        data.getSearchCounts(null);
    }

    @Test
    public void testRemove() throws IOException {
        Directory dir = new ByteBuffersDirectory();
        Path tempDir = Files.createTempDirectory("test");

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new TextField("test", "text", Field.Store.NO));

            iw.addDocument(doc);
        }

        SuggesterProjectData data = new SuggesterProjectData(dir, tempDir, false, Collections.singleton("test"));
        data.init();
        data.remove();

        assertFalse(tempDir.toFile().exists());
    }

    @Test
    public void testUnknownFieldIgnored() throws IOException {
        addText(FIELD, "term");
        data = new SuggesterProjectData(dir, tempDir, false, new HashSet<>(Arrays.asList(FIELD, "unknown")));
        data.init();

        List<Lookup.LookupResult> res = data.lookup("unknown", "a", 10);

        assertTrue(res.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked") // for contains()
    public void testGetSearchCountMapSorted() throws IOException {
        addText(FIELD, "test1 test2");
        init(true);

        Term t1 = new Term(FIELD, "test1");
        Term t2 = new Term(FIELD, "test2");

        data.incrementSearchCount(t1, 10);
        data.incrementSearchCount(t2, 5);

        List<Entry<BytesRef, Integer>> searchCounts = data.getSearchCountsSorted(FIELD, 0, 10);

        assertThat(searchCounts, contains(new SimpleEntry<>(t1.bytes(), 10), new SimpleEntry<>(t2.bytes(), 5)));
    }

}
