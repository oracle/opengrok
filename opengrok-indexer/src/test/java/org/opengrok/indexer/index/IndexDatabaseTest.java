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
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.search.SearchEngine;
import org.opengrok.indexer.util.TandemPath;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code IndexDatabase} class.
 */
public class IndexDatabaseTest {

    private static TestRepository repository;

    @BeforeAll
    public static void setUpClass() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        repository = new TestRepository();
        repository.create(
                HistoryGuru.class.getResourceAsStream("repositories.zip"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(true);
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        // Note that all tests in this class share the index created below.
        // Ergo, if they need to modify it, this has to be done in such a way
        // so that it does not affect other tests, no matter in which order
        // the tests are run.
        Indexer indexer = Indexer.getInstance();
        indexer.prepareIndexer(
                env, true, true,
                false, null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Arrays.asList("/c")));
        indexer.doIndexerExecution(true, null, null);
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        repository.destroy();
    }

    @Test
    public void testGetDefinitions() throws Exception {
        // Test that we can get definitions for one of the files in the
        // repository.
        File f1 = new File(repository.getSourceRoot() + "/git/main.c");
        Definitions defs1 = IndexDatabase.getDefinitions(f1);
        assertNotNull(defs1);
        assertTrue(defs1.hasSymbol("main"));
        assertTrue(defs1.hasSymbol("argv"));
        assertFalse(defs1.hasSymbol("b"));
        assertTrue(defs1.hasDefinitionAt("main", 3, new String[1]));

        //same for windows delimiters
        f1 = new File(repository.getSourceRoot() + "\\git\\main.c");
        defs1 = IndexDatabase.getDefinitions(f1);
        assertNotNull(defs1);
        assertTrue(defs1.hasSymbol("main"));
        assertTrue(defs1.hasSymbol("argv"));
        assertFalse(defs1.hasSymbol("b"));
        assertTrue(defs1.hasDefinitionAt("main", 3, new String[1]));

        // Test that we get null back if we request definitions for a file
        // that's not in the repository.
        File f2 = new File(repository.getSourceRoot() + "/git/foobar.d");
        Definitions defs2 = IndexDatabase.getDefinitions(f2);
        assertNull(defs2);
    }

    private void checkDataExistence(String fileName, boolean shouldExist) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        for (String dirName : new String[] {"historycache", IndexDatabase.XREF_DIR}) {
            File dataDir = new File(env.getDataRootFile(), dirName);
            File dataFile = new File(dataDir, TandemPath.join(fileName, ".gz"));

            if (shouldExist) {
                assertTrue(dataFile.exists(), "file " + fileName + " not found in " + dirName);
            } else {
                assertFalse(dataFile.exists(), "file " + fileName + " found in " + dirName);
            }
        }
    }

    /**
     * Test removal of IndexDatabase. xrefs and history index entries after
     * file has been removed from a repository.
     */
    @Test
    public void testCleanupAfterIndexRemoval() throws Exception {
        final int origNumFiles;

        String projectName = "git";
        String ppath = "/" + projectName;
        Project project = new Project(projectName, ppath);
        IndexDatabase idb = new IndexDatabase(project);
        assertNotNull(idb);

        // Note that the file to remove has to be different than the one used
        // in {@code testGetDefinitions} because it shares the same index
        // and this test is going to remove the file and therefore related
        // definitions.
        String fileName = "header.h";
        File gitRoot = new File(repository.getSourceRoot(), projectName);
        assertTrue(new File(gitRoot, fileName).exists());

        // Check that the file was indexed successfully in terms of generated data.
        checkDataExistence(projectName + File.separator + fileName, true);
        origNumFiles = idb.getNumFiles();

        /*
         * Initially was 6, then IndexAnalysisSettings added 1, then
         * NumLinesLOCAggregator added 3.
         */
        assertEquals(10, origNumFiles, "Lucene number of documents");

        // Remove the file and reindex using IndexDatabase directly.
        File file = new File(repository.getSourceRoot(), projectName + File.separator + fileName);
        file.delete();
        assertFalse(file.exists(), "file " + fileName + " not removed");
        idb.update();

        // Check that the data for the file has been removed.
        checkDataExistence(projectName + File.separator + fileName, false);
        assertEquals(origNumFiles - 1, idb.getNumFiles());
    }

    /**
     * This is a test of {@code populateDocument} so it should be rather in {@code AnalyzerGuruTest}
     * however it lacks the pre-requisite indexing phase.
     */
    @Test
    public void testIndexPath() throws IOException {
        SearchEngine instance = new SearchEngine();
        // Use as broad search as possible.
        instance.setFile("c");
        instance.search();
        ScoreDoc[] scoredocs = instance.scoreDocs();
        assertTrue(scoredocs.length > 0, "need some search hits to perform the check");
        for (ScoreDoc sd : scoredocs) {
            Document doc = instance.doc(sd.doc);
            assertFalse(doc.getField(QueryBuilder.PATH).stringValue().contains("\\"),
                    "PATH field should not contain backslash characters");
        }
    }

    @Test
    public void testGetLastRev() throws IOException, ParseException {
        Document doc = IndexDatabase.getDocument(Paths.get(repository.getSourceRoot(),
                "git", "main.c").toFile());
        assertNotNull(doc);
        assertEquals("aa35c258", doc.get(QueryBuilder.LASTREV));
    }
}
