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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.search.Hit;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

/**
 * Unit tests for the {@code HistoryContext} class.
 */
public class HistoryContextTest {

    private static TestRepository repositories;

    @BeforeAll
    public static void setUpClass() throws Exception {
        repositories = new TestRepository();
        repositories.create(HistoryContextTest.class.getResource("/repositories"));
        RuntimeEnvironment.getInstance().setRepositories(repositories.getSourceRoot());
    }

    @AfterAll
    public static void tearDownClass() {
        repositories.destroy();
        repositories = null;
    }

    @Test
    public void testIsEmpty() {
        TermQuery q1 = new TermQuery(new Term("refs", "isEmpty"));
        TermQuery q2 = new TermQuery(new Term("defs", "isEmpty"));
        TermQuery q3 = new TermQuery(new Term("hist", "isEmpty"));
        BooleanQuery.Builder q4 = new BooleanQuery.Builder();
        q4.add(q1, Occur.MUST);
        q4.add(q2, Occur.MUST);
        BooleanQuery.Builder q5 = new BooleanQuery.Builder();
        q5.add(q2, Occur.MUST);
        q5.add(q3, Occur.MUST);

        // The queries that don't contain a "hist" term are considered empty.
        assertTrue(new HistoryContext(q1).isEmpty());
        assertTrue(new HistoryContext(q2).isEmpty());
        assertFalse(new HistoryContext(q3).isEmpty());
        assertTrue(new HistoryContext(q4.build()).isEmpty());
        assertFalse(new HistoryContext(q5.build()).isEmpty());
    }

    @Test
    @EnabledForRepository(MERCURIAL)
    public void testGetContext3Args() throws Exception {
        String path = "/mercurial/Makefile";
        String filename = Paths.get(repositories.getSourceRoot(), "mercurial", "Makefile").toString();

        // Construct a query equivalent to hist:dummy
        TermQuery q1 = new TermQuery(new Term("hist", "dummy"));
        ArrayList<Hit> hits = new ArrayList<>();
        boolean gotCtx = new HistoryContext(q1).getContext(filename, path, hits);
        assertTrue(gotCtx, filename + " has context");
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getLine().contains(
                "Created a small <b>dummy</b> program"));

        // Construct a query equivalent to hist:"dummy program"
        PhraseQuery.Builder q2 = new PhraseQuery.Builder();
        q2.add(new Term("hist", "dummy"));
        q2.add(new Term("hist", "program"));
        hits.clear();
        gotCtx = new HistoryContext(q2.build()).getContext(filename, path, hits);
        assertTrue(gotCtx, filename + " has context");
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getLine().contains(
                "Created a small <b>dummy program</b>"));

        // Search for a term that doesn't exist
        TermQuery q3 = new TermQuery(new Term("hist", "term_does_not_exist"));
        hits.clear();
        gotCtx = new HistoryContext(q3).getContext(filename, path, hits);
        assertFalse(gotCtx, filename + " has no context");
        assertEquals(0, hits.size());

        // Search for term with multiple hits - hist:small OR hist:target
        BooleanQuery.Builder q4 = new BooleanQuery.Builder();
        q4.add(new TermQuery(new Term("hist", "small")), Occur.SHOULD);
        q4.add(new TermQuery(new Term("hist", "target")), Occur.SHOULD);
        hits.clear();
        gotCtx = new HistoryContext(q4.build()).getContext(filename, path, hits);
        assertTrue(gotCtx, filename + " has context");
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).getLine().contains(
                "Add lint make <b>target</b> and fix lint warnings"));
        assertTrue(hits.get(1).getLine().contains(
                "Created a <b>small</b> dummy program"));
    }

    @Test
    @EnabledForRepository(MERCURIAL)
    public void testGetContext4args() throws Exception {
        String path = "/mercurial/Makefile";
        Path file = Paths.get(repositories.getSourceRoot(), "mercurial", "Makefile");
        String parent = file.getParent().toString();
        String base = file.getFileName().toString();

        // Construct a query equivalent to hist:dummy
        TermQuery q1 = new TermQuery(new Term("hist", "dummy"));
        StringWriter sw = new StringWriter();
        assertTrue(new HistoryContext(q1).getContext(parent, base, path, sw, null), parent + " has context");
        assertTrue(sw.toString().contains(
                "Created a small <b>dummy</b> program"));

        // Construct a query equivalent to hist:"dummy program"
        PhraseQuery.Builder q2 = new PhraseQuery.Builder();
        q2.add(new Term("hist", "dummy"));
        q2.add(new Term("hist", "program"));
        sw = new StringWriter();
        assertTrue(new HistoryContext(q2.build()).getContext(parent, base, path, sw, null), parent + " has context");
        assertTrue(sw.toString().contains(
                "Created a small <b>dummy program</b>"));

        // Search for a term that doesn't exist
        TermQuery q3 = new TermQuery(new Term("hist", "term_does_not_exist"));
        sw = new StringWriter();
        assertFalse(new HistoryContext(q3).getContext(parent, base, path, sw, null), parent + " has no context");
        assertEquals("", sw.toString());

        // Search for term with multiple hits - hist:small OR hist:target
        BooleanQuery.Builder q4 = new BooleanQuery.Builder();
        q4.add(new TermQuery(new Term("hist", "small")), Occur.SHOULD);
        q4.add(new TermQuery(new Term("hist", "target")), Occur.SHOULD);
        sw = new StringWriter();
        assertTrue(new HistoryContext(q4.build()).getContext(parent, base, path, sw, null), parent + " has context");
        String result = sw.toString();
        assertTrue(result.contains(
                "Add lint make <b>target</b> and fix lint warnings"));
        assertTrue(result.contains(
                "Created a <b>small</b> dummy program"));
    }

    /**
     * Test URI and HTML encoding of {@code writeMatch()}.
     * @throws IOException I/O exception
     */
    @Test
    public void testWriteMatch() throws IOException {
        StringBuilder sb = new StringBuilder();
        HistoryContext.writeMatch(sb, "foo", 0, 3, true, "/foo bar/haf+haf",
                "ctx", "1", "2");
        assertEquals("<a href=\"ctx/diff/foo%20bar/haf%2Bhaf?r2=/foo%20bar/haf%2Bhaf@2&amp;" +
                "r1=/foo%20bar/haf%2Bhaf@1\" title=\"diff to previous version\">diff</a> <b>foo</b>",
                sb.toString());
    }
}
