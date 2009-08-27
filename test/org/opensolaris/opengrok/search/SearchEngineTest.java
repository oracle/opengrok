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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.search;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.index.Indexer;
import org.opensolaris.opengrok.index.IndexerTest;
import org.opensolaris.opengrok.util.TestRepository;
import static org.junit.Assert.*;

/**
 * Do basic testing of the SearchEngine
 *
 * @author Trond Norbye
 */
public class SearchEngineTest {

    static TestRepository repository;
    static boolean skip = false;
    static File configFile;

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream("repositories.zip"));

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setCtags(System.getProperty("org.opensolaris.opengrok.configuration.ctags", "ctags"));
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());

        if (env.validateExuberantCtags()) {
            env.setSourceRoot(repository.getSourceRoot());
            env.setDataRoot(repository.getDataRoot());
            env.setVerbose(false);
            Indexer.getInstance().prepareIndexer(env, true, true, "/c", null, false, false, false, null, null);
            Indexer.getInstance().doIndexerExecution(true, 1, null, null);
        } else {
            System.out.println("Skipping test. Could not find a ctags I could use in path.");
            skip = true;
        }

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        RuntimeEnvironment.getInstance().readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        repository.destroy();
        configFile.delete();
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testIsValidQuery() {
        SearchEngine instance = new SearchEngine();
        assertFalse(instance.isValidQuery());
        instance.setFile("foo");
        assertTrue(instance.isValidQuery());
    }

    @Test
    public void testDefinition() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getDefinition());
        String defs = "This is a definition";
        instance.setDefinition(defs);
        assertEquals(defs, instance.getDefinition());
    }

    @Test
    public void testFile() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getFile());
        String file = "This is a File";
        instance.setFile(file);
        assertEquals(file, instance.getFile());
    }

    @Test
    public void testFreetext() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getFreetext());
        String freetext = "This is just a piece of text";
        instance.setFreetext(freetext);
        assertEquals(freetext, instance.getFreetext());
    }

    @Test
    public void testHistory() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getHistory());
        String hist = "This is a piece of history";
        instance.setHistory(hist);
        assertEquals(hist, instance.getHistory());
    }

    @Test
    public void testSymbol() {
        SearchEngine instance = new SearchEngine();
        assertNull(instance.getSymbol());
        String sym = "This is a symbol";
        instance.setSymbol(sym);
        assertEquals(sym, instance.getSymbol());
    }

    @Test
    public void testGetQuery() throws Exception {
        SearchEngine instance = new SearchEngine();
        instance.setHistory("Once upon a time");
        instance.setFile("Makefile");
        instance.setDefinition("std::string");
        instance.setSymbol("toString");
        instance.setFreetext("OpenGrok");
        assertTrue(instance.isValidQuery());
        assertEquals("+full:opengrok +defs:\"std string\" +refs:toString +path:makefile +(+hist:once +hist:upon +hist:time)",
                instance.getQuery());
    }

    @Test
    public void testSearch() {
        if (skip) {
            return;
        }

        List<Hit> hits = new ArrayList<Hit>();
        SearchEngine instance = new SearchEngine();
        instance.setHistory("\"Add lint make target and fix lint warnings\"");
        int noHits =  instance.search();
        if (noHits > 0) {
            instance.results(0, noHits, hits);
            assertEquals(noHits, hits.size());
        }

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

        instance = new SearchEngine();
        instance.setFreetext("arguments");
        instance.setFile("main.c");
        noHits = instance.search();
        hits.clear();
        instance.results(0, noHits, hits);
        for (Hit hit : hits) {
            assertEquals("main.c", hit.getFilename());
            if (hit.getLine().indexOf("arguments") == -1) {
               fail("got an incorrect match: " + hit.getLine());
            }
        }
        assertEquals(8, noHits);

        instance = new SearchEngine();
        instance.setDefinition("main");
        instance.setFile("main.c");
        noHits = instance.search();
        hits.clear();
        instance.results(0, noHits, hits);
        for (Hit hit : hits) {
            assertEquals("main.c", hit.getFilename());
            if (hit.getLine().indexOf("main") == -1) {
               fail("got an incorrect match: " + hit.getLine());
            }
        }
        assertEquals(8, noHits);
    }
}
