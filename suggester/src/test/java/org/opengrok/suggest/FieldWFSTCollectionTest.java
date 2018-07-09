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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

public class FieldWFSTCollectionTest {

    private Directory dir;

    private Path tempDir;

    private FieldWFSTCollection f;

    @Before
    public void setUp() throws IOException {
        dir = new RAMDirectory();
        tempDir = Files.createTempDirectory("test");
    }

    @After
    public void tearDown() throws IOException {
        f.close();
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void testLookup() throws IOException {
        addText("test", "term1 term2");

        init(false);

        List<String> suggestions = getSuggestions("test", "t", 10);

        assertThat(suggestions, Matchers.containsInAnyOrder("term1", "term2"));

        f.close();
    }

    private void init(boolean allowMostPopular) throws IOException {
        f = new FieldWFSTCollection(dir, tempDir, allowMostPopular);
        f.init();
    }

    private void addText(final String field, final String text) throws IOException {
        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new TextField(field, text, Field.Store.NO));

            iw.addDocument(doc);
        }
    }

    private List<String> getSuggestions(String field, String prefix, int size) {
        List<Lookup.LookupResult> res = f.lookup(field, prefix, size);

        return res.stream().map(r -> r.key.toString()).collect(Collectors.toList());
    }

    @Test
    public void testMultipleTermsAreFirstByDefault() throws IOException {
        addText("test", "term1 term2 term1");

        init(false);

        List<String> suggestions = getSuggestions("test", "t", 10);

        assertThat(suggestions, Matchers.contains("term1", "term2"));
    }

    @Test
    public void testMostPopularSearch() throws IOException {
        addText("test", "term1 term2 term1");

        init(true);

        f.incrementSearchCount(new Term("test", "term2"));
        f.incrementSearchCount(new Term("test", "term2"));

        f.rebuild();

        List<String> suggestions = getSuggestions("test", "t", 10);

        assertThat(suggestions, Matchers.contains("term2", "term1"));
    }

    @Test
    public void testRebuild() throws IOException {
        addText("test", "term1 term2 term1");

        init(false);

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            iw.deleteAll();
        }

        addText("test", "term3 term4 term5");

        f.rebuild();

        List<String> suggestions = getSuggestions("test", "t", 10);

        assertThat(suggestions, Matchers.containsInAnyOrder("term3", "term4", "term5"));
    }

    @Test
    public void testDifferentPrefixes() throws IOException {
        addText("test", "abc bbc cbc dbc efc gfc");

        init(false);

        List<String> suggestions = getSuggestions("test", "e", 10);

        assertThat(suggestions, Matchers.contains("efc"));
    }

    @Test
    public void testResultSize() throws IOException {
        addText("test", "a1 a2 a3 a4 a5 a6 a7");

        init(false);

        List<String> suggestions = getSuggestions("test", "a", 2);

        assertEquals(2, suggestions.size());
    }

    @Test
    public void incrementTest() throws IOException {
        addText("test", "text");

        init(true);

        f.incrementSearchCount(new Term("test", "text"));

        assertEquals(1, f.getSearchCounts("test").get(new BytesRef("text")));
    }

    @Test
    public void incrementByValueTest() throws IOException {
        addText("test", "some text");

        init(true);

        f.incrementSearchCount(new Term("test", "some"), 20);

        assertEquals(20, f.getSearchCounts("test").get(new BytesRef("some")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incrementByNegativeValueTest() throws IOException {
        addText("test", "another text example");

        init(true);

        f.incrementSearchCount(new Term("test", "example"), -10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void incrementUnknownTermTest() throws IOException {
        addText("test", "test case document");

        init(true);

        f.incrementSearchCount(new Term("test", "unknown"));
    }

    @Test
    public void rebuildRemoveOldTermsTest() throws IOException {
        addText("test", "term");

        init(true);

        f.incrementSearchCount(new Term("test", "term"), 10);

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            iw.deleteAll();
        }

        addText("test", "term2");

        f.rebuild();

        assertEquals(0, f.getSearchCounts("test").get(new BytesRef("term")));
    }

    @Test
    public void initAfterChangingIndexTest() throws IOException {
        addText("test", "term");
        init(false);

        addText("test", "term2");

        f.init();

        assertThat(getSuggestions("test", "t", 2), containsInAnyOrder("term", "term2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void incrementSearchCountNullTest() throws IOException {
        addText("test", "term");
        init(false);

        f.incrementSearchCount(null);
    }

    @Test
    public void getSearchCountMapNullTest() throws IOException {
        addText("test", "term");
        init(true);

        f.getSearchCounts(null);
    }

}
