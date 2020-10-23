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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.suggest.query.SuggesterFuzzyQuery;
import org.opengrok.suggest.query.SuggesterPhraseQuery;
import org.opengrok.suggest.query.SuggesterPrefixQuery;
import org.opengrok.suggest.query.SuggesterRangeQuery;
import org.opengrok.suggest.query.SuggesterRegexpQuery;
import org.opengrok.suggest.query.SuggesterWildcardQuery;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class SuggesterSearcherTest {

    private static Directory dir;

    private static SuggesterSearcher searcher;

    @BeforeClass
    public static void setUpClass() throws IOException {
        dir = new ByteBuffersDirectory();

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc1 = new Document();
            Document doc2 = new Document();

            doc1.add(new TextField("test", "opengrok opengrok2", Field.Store.NO));
            doc2.add(new TextField("test", "opengrok test", Field.Store.NO));

            iw.addDocument(doc1);
            iw.addDocument(doc2);
        }

        IndexReader ir = DirectoryReader.open(dir);

        searcher = new SuggesterSearcher(ir, 10);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        dir.close();
    }

    @Test
    public void suggesterPrefixQueryTest() {
        List<LookupResultItem> suggestions = searcher.suggest(new TermQuery(new Term("test", "test")), "test",
                new SuggesterPrefixQuery(new Term("test", "o")), k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("opengrok"));
    }

    @Test
    public void suggesterWildcardQueryTest() {
        List<LookupResultItem> suggestions = searcher.suggest(new TermQuery(new Term("test", "test")), "test",
                new SuggesterWildcardQuery(new Term("test", "?pengrok")), k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("opengrok"));
    }

    @Test
    public void suggesterRegexpQueryTest() {
        List<LookupResultItem> suggestions = searcher.suggest(new TermQuery(new Term("test", "test")), "test",
                new SuggesterRegexpQuery(new Term("test", ".pengrok")), k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("opengrok"));
    }

    @Test
    public void suggesterFuzzyQueryTest() {
        List<LookupResultItem> suggestions = searcher.suggest(new TermQuery(new Term("test", "test")), "test",
                new SuggesterFuzzyQuery(new Term("test", "opengroc"), 1, 0), k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("opengrok"));
    }

    @Test
    public void suggesterPhraseQueryTest() {
        SuggesterPhraseQuery q = new SuggesterPhraseQuery("test", "abc", Arrays.asList("opengrok", "openabc"), 0);

        List<LookupResultItem> suggestions = searcher.suggest(q.getPhraseQuery(), "test",
                q.getSuggesterQuery(), k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("opengrok2"));
    }

    @Test
    public void testRangeQueryUpper() {
        SuggesterRangeQuery q = new SuggesterRangeQuery("test", new BytesRef("opengrok"),
                new BytesRef("t"), true, true, SuggesterRangeQuery.SuggestPosition.UPPER);

        List<LookupResultItem> suggestions = searcher.suggest(null, "test", q, k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("test"));
    }

    @Test
    public void testRangeQueryLower() {
        SuggesterRangeQuery q = new SuggesterRangeQuery("test", new BytesRef("o"),
                new BytesRef("test"), true, true, SuggesterRangeQuery.SuggestPosition.LOWER);

        List<LookupResultItem> suggestions = searcher.suggest(null, "test", q, k -> 0);

        List<String> tokens = suggestions.stream().map(LookupResultItem::getPhrase).collect(Collectors.toList());

        assertThat(tokens, contains("opengrok", "opengrok2"));
    }

}
