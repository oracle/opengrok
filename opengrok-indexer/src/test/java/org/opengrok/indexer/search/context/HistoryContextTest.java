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
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search.context;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.search.Hit;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

/**
 * Unit tests for the {@code HistoryContext} class.
 */
class HistoryContextTest {

    private static TestRepository repositories;

    @BeforeAll
    static void setUpClass() throws Exception {
        repositories = new TestRepository();
        repositories.create(HistoryContextTest.class.getResource("/repositories"));
        RuntimeEnvironment.getInstance().setRepositories(repositories.getSourceRoot());
    }

    @AfterAll
    static void tearDownClass() {
        repositories.destroy();
        repositories = null;
    }

    @Test
    void testIsEmpty() {
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
    void testGetContext3Args() throws Exception {
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
    void testGetContext4args() throws Exception {
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
    void testWriteMatch() throws IOException {
        StringBuilder sb = new StringBuilder();
        HistoryContext.writeMatch(sb, "foo", 0, 3, true, "/foo bar/haf+haf",
                "ctx", "1", "2");
        assertEquals("<a href=\"ctx/diff/foo%20bar/haf%2Bhaf?r2=/foo%20bar/haf%2Bhaf@2&amp;" +
                "r1=/foo%20bar/haf%2Bhaf@1\" title=\"diff to previous version\">diff</a> <b>foo</b>",
                sb.toString());
    }

    /**
     * Test that inactive history entries are skipped when generating history context.
     */
    @Test
    void testGetHistoryContextVsInactiveHistoryEntries() {
        Set<String> filePaths = Set.of(File.separator + Paths.get("teamware", "foo.c"));
        History history = new History(List.of(
                new HistoryEntry("1.2", "1.2", new Date(1485438707000L),
                        "Totoro",
                        "Uaaah\n", true, filePaths),
                new HistoryEntry("1.2", "1.2", new Date(1485438706000L),
                        "Catbus",
                        "Miau\n", false, filePaths),
                new HistoryEntry("1.1", "1.1", new Date(1485438705000L),
                        "Totoro",
                        "Hmmm\n", true, filePaths)
        ));

        ArrayList<Hit> hits = new ArrayList<>();
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        HistoryEntry lastHistoryEntry = history.getLastHistoryEntry();
        assertNotNull(lastHistoryEntry);
        query.add(new TermQuery(new Term("hist", lastHistoryEntry.getMessage().trim())), Occur.MUST);
        HistoryContext historyContext = new HistoryContext(query.build());
        final String path = "/foo/bar";
        final String prefix = "prefix";
        assertTrue(historyContext.getHistoryContext(history, path, null, hits, prefix));
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getLine().
                startsWith(String.format("<a href=\"%s/diff%s?r2=%s@1.2&amp;r1=%s@1.1\"",
                        prefix, path, path, path)));
    }
}
