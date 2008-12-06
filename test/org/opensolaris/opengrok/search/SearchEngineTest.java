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
        repository.create(IndexerTest.class.getResourceAsStream("source.zip"));

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

        SearchEngine instance = new SearchEngine();
        instance.setFile("Makefile");
        assertEquals(1, instance.search());
        List<Hit> hits = new ArrayList<Hit>();

        hits.clear();
        instance.more(0, 1, hits);
        assertEquals(1, hits.size());

        instance.setFile("main~");
        assertEquals(6, instance.search());

        hits.clear();
        instance.more(0, 3, hits);
        assertEquals(3, hits.size());
        instance.more(3, 6, hits);
        assertEquals(6, hits.size());

        instance.setFile("\"main troff\"~5");
        assertEquals(0, instance.search());

        instance.setFile("Main OR main");
        assertEquals(6, instance.search());

        instance.setFile("main file");
        assertEquals(0, instance.search());

        instance.setFile("+main -file");
        assertEquals(6, instance.search());

        instance.setFile("main AND (file OR field)");
        assertEquals(0, instance.search());

        instance.setFreetext("opengrok && something || else");
        assertEquals(4, instance.search());

        instance.setFreetext("op*ng?ok");
        assertEquals(3, instance.search());

        instance.setFreetext("\"op*n g?ok\"");
        assertEquals(0, instance.search());

        instance.setFreetext("title:[a TO b]");
        assertEquals(0, instance.search());

        instance.setFreetext("title:{a TO c}");
        assertEquals(0, instance.search());

        instance.setFreetext("\"contains some strange\"");
        assertEquals(1, instance.search());

        RuntimeEnvironment.getInstance().setAllowLeadingWildcard(true);
        instance.setFile("?akefile");
        assertEquals(1, instance.search());
    }
}
