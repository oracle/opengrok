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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.File;
import java.util.Collections;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;

import org.opengrok.indexer.history.RepositoryFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Do basic testing of the SearchEngine.
 *
 * @author Trond Norbye
 */
public class SearchEngineTest {

    static TestRepository repository;
    static File configFile;

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        RepositoryFactory.initializeIgnoredNames(env);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(false);

        Indexer.getInstance().prepareIndexer(env, true, true,
                null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Collections.singletonList("/c")));
        Indexer.getInstance().doIndexerExecution(null, null);

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        RuntimeEnvironment.getInstance().readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        repository.destroy();
        configFile.delete();
    }

    @Test
    void testIsValidQuery() {
        SearchEngine instance = new SearchEngine();
        assertFalse(instance.isValidQuery());
        instance.setFile("foo");
        assertTrue(instance.isValidQuery());
    }

    @Test
    void testDefinition() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getDefinition());
        String defs = "This is a definition";
        instance.setDefinition(defs);
        assertEquals(defs, instance.getDefinition());
    }

    @Test
    void testFile() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getFile());
        String file = "This is a File";
        instance.setFile(file);
        assertEquals(file, instance.getFile());
    }

    @Test
    void testFreetext() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getFreetext());
        String freetext = "This is just a piece of text";
        instance.setFreetext(freetext);
        assertEquals(freetext, instance.getFreetext());
    }

    @Test
    void testHistory() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getHistory());
        String hist = "This is a piece of history";
        instance.setHistory(hist);
        assertEquals(hist, instance.getHistory());
    }

    @Test
    void testSymbol() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getSymbol());
        String sym = "This is a symbol";
        instance.setSymbol(sym);
        assertEquals(sym, instance.getSymbol());
    }

    @Test
    void testGetQuery() throws Exception {
        SearchEngine instance = new SearchEngine();
        instance.setHistory("Once upon a time");
        instance.setFile("Makefile");
        instance.setDefinition("\"std::string\"");
        instance.setSymbol("toString");
        instance.setFreetext("OpenGrok");
        assertTrue(instance.isValidQuery());
        assertEquals("+defs:\"std string\" +full:opengrok +hist:once +hist:upon +hist:time +path:makefile +refs:toString",
                instance.getQuery());
    }

    /* see https://github.com/oracle/opengrok/issues/2030
    @Test
    public void testSearch() {
        List<Hit> hits = new ArrayList<>();

        SearchEngine instance = new SearchEngine();
        instance.setHistory("\"Add lint make target and fix lint warnings\"");
        int noHits =  instance.search();
        if (noHits > 0) {
            instance.results(0, noHits, hits);
            assertEquals(noHits, hits.size());
        }
        instance.destroy();

        instance = new SearchEngine();
        instance.setSymbol("printf");
        instance.setFile("main.c");
        noHits = instance.search();
        assertEquals(8, noHits);
        hits.clear();
        instance.results(0, noHits, hits);
        for (Hit hit : hits) {
            assertEquals("main.c", hit.getFilename());
            assertEquals(1, 1);
        }
        instance.setFile("main.c OR Makefile");
        noHits = instance.search();
        assertEquals(8, noHits);
        instance.destroy();

        instance = new SearchEngine();
        instance.setFreetext("arguments");
        instance.setFile("main.c");
        noHits = instance.search();
        hits.clear();
        instance.results(0, noHits, hits);
        for (Hit hit : hits) {
            assertEquals("main.c", hit.getFilename());
            if (!hit.getLine().contains("arguments")) {
               fail("got an incorrect match: " + hit.getLine());
            }
        }
        assertEquals(8, noHits);
        instance.destroy();

        instance = new SearchEngine();
        instance.setDefinition("main");
        instance.setFile("main.c");
        noHits = instance.search();
        hits.clear();
        instance.results(0, noHits, hits);
        for (Hit hit : hits) {
            assertEquals("main.c", hit.getFilename());
            if (!hit.getLine().contains("main")) {
               fail("got an incorrect match: " + hit.getLine());
            }
        }
        assertEquals(8, noHits);
        instance.destroy();

        // negative symbol test (comments should be ignored)
        instance = new SearchEngine();
        instance.setSymbol("Ordinary");
        instance.setFile("\"Main.java\"");
        instance.search();
        assertEquals("+path:\"main . java\" +refs:Ordinary",
                     instance.getQuery());
        assertEquals(0, instance.search());
        instance.destroy();

        // wildcards and case sensitivity of definition search
        instance = new SearchEngine();
        instance.setDefinition("Mai*"); // definition is case sensitive
        instance.setFile("\"Main.java\" OR \"main.c\"");
        instance.search();
        assertEquals("+defs:Mai* +(path:\"main . java\" path:\"main . c\")",
                     instance.getQuery());
        assertEquals(2, instance.search());
        instance.setDefinition("MaI*"); // should not match Main
        instance.search();
        assertEquals(0, instance.search());
        instance.destroy();

        // wildcards and case sensitivity of symbol search
        instance = new SearchEngine();
        instance.setSymbol("Mai*"); // symbol is case sensitive
        instance.setFile("\"Main.java\" OR \"main.c\"");
        instance.search();
        assertEquals(2, instance.search());
        instance.setSymbol("MaI*"); // should not match Main
        instance.search();
        assertEquals(0, instance.search());
        instance.destroy();

        // wildcards and case insensitivity of freetext search
        instance = new SearchEngine();
        instance.setFreetext("MaI*"); // should match both Main and main
        instance.setFile("\"Main.java\" OR \"main.c\"");
        assertEquals(10, instance.search());
        instance.destroy();

        // file name search is case insensitive
        instance = new SearchEngine();
        instance.setFile("JaVa"); // should match java
        int count=instance.search();
        if (count > 0) {
        instance.results(0, count, hits);
        }
        assertEquals(8, count); // path is now case sensitive ... but only in SearchEngine !
        instance.destroy();

        //test eol and eof
        instance = new SearchEngine();
        instance.setFreetext("makeW");
        assertEquals(1, instance.search());
        instance.destroy();

        instance = new SearchEngine();
        instance.setFreetext("WeirdEOL");
        assertEquals(1, instance.search());
        instance.destroy();

        //test bcel jar parser
        instance = new SearchEngine();
        instance.setFreetext("InstConstraintVisitor");
        assertEquals(1, instance.search());
        instance.destroy();
    }
    */
}
