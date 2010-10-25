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
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.search.context;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.util.TestRepository;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@code HistoryContext} class.
 */
public class HistoryContextTest {

    private static TestRepository repositories;

    @BeforeClass
    public static void setUpClass() throws Exception {
        repositories = new TestRepository();
        repositories.create(HistoryContextTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/history/repositories.zip"));
        HistoryGuru.getInstance().addRepositories(repositories.getSourceRoot());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        repositories.destroy();
        repositories = null;
    }

    @Test
    public void testIsEmpty() {
        TermQuery q1 = new TermQuery(new Term("refs", "isEmpty"));
        TermQuery q2 = new TermQuery(new Term("defs", "isEmpty"));
        TermQuery q3 = new TermQuery(new Term("hist", "isEmpty"));
        BooleanQuery q4 = new BooleanQuery();
        q4.add(q1, Occur.MUST);
        q4.add(q2, Occur.MUST);
        BooleanQuery q5 = new BooleanQuery();
        q5.add(q2, Occur.MUST);
        q5.add(q3, Occur.MUST);

        // The queries that don't contain a "hist" term are considered empty.
        assertTrue(new HistoryContext(q1).isEmpty());
        assertTrue(new HistoryContext(q2).isEmpty());
        assertFalse(new HistoryContext(q3).isEmpty());
        assertTrue(new HistoryContext(q4).isEmpty());
        assertFalse(new HistoryContext(q5).isEmpty());
    }

    @Test
    public void testGetContext_3args() throws Exception {
        String path = "/mercurial/Makefile";
        String filename = repositories.getSourceRoot() + path;

        // Construct a query equivalent to hist:dummy
        TermQuery q1 = new TermQuery(new Term("hist", "dummy"));
        ArrayList<Hit> hits = new ArrayList<Hit>();
        assertTrue(new HistoryContext(q1).getContext(filename, path, hits));
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getLine().contains(
                "Created a small <b>dummy</b> program"));

        // Construct a query equivalent to hist:"dummy program"
        PhraseQuery q2 = new PhraseQuery();
        q2.add(new Term("hist", "dummy"));
        q2.add(new Term("hist", "program"));
        hits.clear();
        assertTrue(new HistoryContext(q2).getContext(filename, path, hits));
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getLine().contains(
                "Created a small <b>dummy program</b>"));

        // Search for a term that doesn't exist
        TermQuery q3 = new TermQuery(new Term("hist", "term_does_not_exist"));
        hits.clear();
        assertFalse(new HistoryContext(q3).getContext(filename, path, hits));
        assertEquals(0, hits.size());

        // Search for term with multiple hits - hist:small OR hist:target
        BooleanQuery q4 = new BooleanQuery();
        q4.add(new TermQuery(new Term("hist", "small")), Occur.SHOULD);
        q4.add(new TermQuery(new Term("hist", "target")), Occur.SHOULD);
        hits.clear();
        assertTrue(new HistoryContext(q4).getContext(filename, path, hits));
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).getLine().contains(
                "Add lint make <b>target</b> and fix lint warnings"));
        assertTrue(hits.get(1).getLine().contains(
                "Created a <b>small</b> dummy program"));
    }

    @Test
    public void testGetContext_4args() throws Exception {
        String path = "/mercurial/Makefile";
        File file = new File(repositories.getSourceRoot() + path);
        String parent = file.getParent();
        String base = file.getName();

        // Construct a query equivalent to hist:dummy
        TermQuery q1 = new TermQuery(new Term("hist", "dummy"));
        StringWriter sw = new StringWriter();
        assertTrue(new HistoryContext(q1).getContext(parent, base, path, sw, null));
        assertTrue(sw.toString().contains(
                "Created a small <b>dummy</b> program"));

        // Construct a query equivalent to hist:"dummy program"
        PhraseQuery q2 = new PhraseQuery();
        q2.add(new Term("hist", "dummy"));
        q2.add(new Term("hist", "program"));
        sw = new StringWriter();
        assertTrue(new HistoryContext(q2).getContext(parent, base, path, sw, null));
        assertTrue(sw.toString().contains(
                "Created a small <b>dummy program</b>"));

        // Search for a term that doesn't exist
        TermQuery q3 = new TermQuery(new Term("hist", "term_does_not_exist"));
        sw = new StringWriter();
        assertFalse(new HistoryContext(q3).getContext(parent, base, path, sw, null));
        assertEquals("", sw.toString());

        // Search for term with multiple hits - hist:small OR hist:target
        BooleanQuery q4 = new BooleanQuery();
        q4.add(new TermQuery(new Term("hist", "small")), Occur.SHOULD);
        q4.add(new TermQuery(new Term("hist", "target")), Occur.SHOULD);
        sw = new StringWriter();
        assertTrue(new HistoryContext(q4).getContext(parent, base, path, sw, null));
        String result = sw.toString();
        assertTrue(result.contains(
                "Add lint make <b>target</b> and fix lint warnings"));
        assertTrue(result.contains(
                "Created a <b>small</b> dummy program"));
    }

}
