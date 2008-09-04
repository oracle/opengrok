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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
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
 * Basic testing of the Search class
 *
 * @author Trond Norbye
 */
public class SearchTest {

    static TestRepository repository;
    static boolean skip = false;
    static PrintStream err = System.err;
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
        PrintStream stream = new PrintStream(new ByteArrayOutputStream());
        System.setErr(stream);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.setErr(err);
        repository.destroy();
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testParseCmdLine() {
        if (skip) {
            return;
        }
        Search instance = new Search();

        assertTrue(instance.parseCmdLine(new String[] {}));
        assertTrue(instance.parseCmdLine(new String[] {"-f", "foo"}));
        assertTrue(instance.parseCmdLine(new String[] {"-r", "foo"}));
        assertTrue(instance.parseCmdLine(new String[] {"-d", "foo"}));
        assertTrue(instance.parseCmdLine(new String[] {"-h", "foo"}));
        assertTrue(instance.parseCmdLine(new String[] {"-p", "foo"}));
        assertTrue(instance.parseCmdLine(new String[] {"-R", configFile.getAbsolutePath()}));

        assertFalse(instance.parseCmdLine(new String[] {"-f"}));
        assertFalse(instance.parseCmdLine(new String[] {"-r"}));
        assertFalse(instance.parseCmdLine(new String[] {"-d"}));
        assertFalse(instance.parseCmdLine(new String[] {"-h"}));
        assertFalse(instance.parseCmdLine(new String[] {"-p"}));
        assertFalse(instance.parseCmdLine(new String[] {"-R"}));
        assertFalse(instance.parseCmdLine(new String[] {"-R", "nonexisting-config-file"}));

        assertTrue(instance.parseCmdLine(new String[] {
            "-f", "foo",
            "-r", "foo",
            "-d", "foo",
            "-d", "foo",
            "-h", "foo",
            "-p", "foo", "-R", configFile.getAbsolutePath()}));
    }

    /**
     * Test of search method, of class Search.
     */
    @Test
    public void testSearch() {
        if (skip) {
            return;
        }
        Search instance = new Search();
        assertFalse(instance.search());
        assertTrue(instance.parseCmdLine(new String[] {"-p", "Makefile"}));
        assertTrue(instance.search());
        assertEquals(1, instance.results.size());
}

    @Test
    public void testSearchNotFound() {
        if (skip) {
            return;
        }
        Search instance = new Search();
        
        assertTrue(instance.parseCmdLine(new String[] {"-p", "path_that_can't_be_found"}));
        assertTrue(instance.search());
        assertEquals(0, instance.results.size());        

        assertTrue(instance.parseCmdLine(new String[] {"-d", "definition_that_can't_be_found"}));
        assertTrue(instance.search());
        assertEquals(0, instance.results.size());        

        assertTrue(instance.parseCmdLine(new String[] {"-r", "reference_that_can't_be_found"}));
        assertTrue(instance.search());
        assertEquals(0, instance.results.size());        

        assertTrue(instance.parseCmdLine(new String[] {"-h", "history_that_can't_be_found"}));
        assertTrue(instance.search());
        assertEquals(0, instance.results.size());        

        assertTrue(instance.parseCmdLine(new String[] {"-f", "fulltext_that_can't_be_found"}));
        assertTrue(instance.search());
        assertEquals(0, instance.results.size());        
     }

    @Test
    public void testDumpResults() {
        if (skip) {
            return;
        }
        Search instance = new Search();
        assertTrue(instance.parseCmdLine(new String[] {"-p", "Non-existing-makefile-Makefile"}));
        assertTrue(instance.search());
        assertEquals(0, instance.results.size());
        instance.dumpResults();

        assertTrue(instance.parseCmdLine(new String[] {"-p", "Makefile"}));
        assertTrue(instance.search());

        PrintStream out = System.out;
        ByteArrayOutputStream array = new ByteArrayOutputStream();
        System.setOut(new PrintStream(array));
        instance.dumpResults();
        System.out.flush();
        assertTrue(array.toString().indexOf("Makefile: [...]") != -1);
        System.setOut(out);
    }

    /**
     * Test of main method, of class Search.
     */
    @Test
    public void testMain() {
        if (skip) {
            return;
        }
        PrintStream out = System.out;
        ByteArrayOutputStream array = new ByteArrayOutputStream();
        System.setOut(new PrintStream(array));
        Search.main(new String[] {"-p", "Makefile"});
        System.out.flush();
        assertTrue(array.toString().indexOf("Makefile: [...]") != -1);
        System.setOut(out);
    }
}