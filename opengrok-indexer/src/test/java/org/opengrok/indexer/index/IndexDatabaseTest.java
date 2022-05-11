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
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code IndexDatabase} class.
 *
 * This is quite a heavy test class - it runs the indexer before each (parametrized) test,
 * so it might contribute significantly to the overall test run time.
 */
class IndexDatabaseTest {

    private static TestRepository repository;

    private Indexer indexer;

    private RuntimeEnvironment env;

    @BeforeEach
    public void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(true);
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        indexer = Indexer.getInstance();
        indexer.prepareIndexer(
                env, true, true,
                false, null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Arrays.asList("/c")));
        env.generateProjectRepositoriesMap();
        indexer.doIndexerExecution(true, null, null);
    }

    @AfterEach
    public void tearDownClass() throws Exception {
        repository.destroy();
    }

    @Test
    void testGetDefinitions() throws Exception {
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
    void testCleanupAfterIndexRemoval() throws Exception {
        final int origNumFiles;

        String projectName = "git";
        String ppath = "/" + projectName;
        Project project = new Project(projectName, ppath);
        IndexDatabase idb = new IndexDatabase(project);
        assertNotNull(idb);

        // Note that the file to remove has to be different from the one used
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
    void testIndexPath() throws IOException {
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
    void testGetLastRev() throws IOException, ParseException {
        Document doc = IndexDatabase.getDocument(Paths.get(repository.getSourceRoot(),
                "git", "main.c").toFile());
        assertNotNull(doc);
        assertEquals("aa35c258", doc.get(QueryBuilder.LASTREV));
    }

    static void changeFileAndCommit(Git git, File file, String comment) throws Exception {
        String authorName = "Foo Bar";
        String authorEmail = "foobar@example.com";

        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write(comment.getBytes(StandardCharsets.UTF_8));
        }

        git.commit().setMessage(comment).setAuthor(authorName, authorEmail).setAll(true).call();
    }

    private void addFileAndCommit(Git git, String newFileName, File repositoryRoot, String message) throws Exception {
        File newFile = new File(repositoryRoot, newFileName);
        if (!newFile.createNewFile()) {
            throw new IOException("Could not create file " + newFile);
        }
        try (FileOutputStream fos = new FileOutputStream(newFile)) {
            fos.write("foo bar foo bar foo bar".getBytes(StandardCharsets.UTF_8));
        }
        git.add().addFilepattern(newFileName).call();
        git.commit().setMessage(message).setAuthor("foo bar", "foobar@example.com").setAll(true).call();
    }

    /**
     * Add some commits to the Git repository.
     * @param repositoryRoot Git repository root
     */
    private void changeGitRepository(File repositoryRoot) throws Exception {
        try (Git git = Git.init().setDirectory(repositoryRoot).call()) {
            // This name is specifically picked to add file that would exercise the end of term traversal
            // in processFileIncremental(), that is (uidIter == null).
            String newFileName = "zzz.txt";
            addFileAndCommit(git, newFileName, repositoryRoot, "another new file");

            // Add another file that is sorted behind to exercise another code path in processFileIncremental().
            // These 'z'-files are added first so their commits are not the last. This exercises the sorting
            // of the files in FileCollector and the simultaneous traverse of the index and file list
            // in processFileIncremental().
            newFileName = "zzzzzz.txt";
            addFileAndCommit(git, newFileName, repositoryRoot, "another new file");

            // Change one of the pre-existing files.
            File mainFile = new File(repositoryRoot, "main.c");
            assertTrue(mainFile.exists());
            changeFileAndCommit(git, mainFile, "new commit");

            File rmFile = new File(repositoryRoot, "main.o");
            assertTrue(rmFile.exists());
            git.rm().addFilepattern("main.o").call();
            git.commit().setMessage("delete").setAuthor("foo", "foobar@example.com").setAll(true).call();

            // Rename some file.
            File fooFile = new File(repositoryRoot, "Makefile");
            assertTrue(fooFile.exists());
            File barFile = new File(repositoryRoot, "Makefile.renamed");
            assertTrue(fooFile.renameTo(barFile));
            git.add().addFilepattern("Makefile.renamed").call();
            git.rm().addFilepattern("Makefile").call();
            git.commit().setMessage("rename").setAuthor("foo", "foobar@example.com").setAll(true).call();
        }
    }

    private static Stream<Arguments> provideParamsFortestGetIndexDownArgs() {
        return Stream.of(
            Arguments.of(false, false, false),
            Arguments.of(false, false, true),
            Arguments.of(false, true, false),
            Arguments.of(false, true, true),
            Arguments.of(true, false, false),
            Arguments.of(true, false, true),
            Arguments.of(true, true, false),
            Arguments.of(true, true, true)
        );
    }

    /**
     * Test specifically getIndexDownArgs() with IndexDatabase instance.
     * This test ensures that correct set of files is discovered.
     * TODO: add param setHistoryCachePerPartesEnabled()
     */
    @ParameterizedTest
    @MethodSource("provideParamsFortestGetIndexDownArgs")
    void testGetIndexDownArgs(boolean mergeCommits, boolean renamedFiles, boolean historyBased) throws Exception {
        assertTrue(env.isHistoryEnabled());

        env.setHistoryBasedReindex(historyBased);
        env.setHandleHistoryOfRenamedFiles(renamedFiles);
        env.setMergeCommitsEnabled(mergeCommits);

        IndexDownArgsFactory factory = new IndexDownArgsFactory();
        IndexDownArgsFactory spyFactory = spy(factory);
        IndexDownArgs args = new IndexDownArgs();
        // In this case the getIndexDownArgs() should be called from update() just once so this will suffice.
        when(spyFactory.getIndexDownArgs()).thenReturn(args);

        Project gitProject = env.getProjects().get("git");
        assertNotNull(gitProject);
        IndexDatabase idbOrig = new IndexDatabase(gitProject, spyFactory);
        assertNotNull(idbOrig);
        IndexDatabase idb = spy(idbOrig);

        File repositoryRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(repositoryRoot.isDirectory());
        changeGitRepository(repositoryRoot);

        // Re-generate the history cache so that the git repository is ready for history based re-index.
        indexer.prepareIndexer(
                env, true, true,
                false, List.of("/git"), null);

        // Setup and use listener for the "removed" files.
        class RemovedFilesListener implements IndexChangedListener {
            private final Set<String> removedFiles = new HashSet<>();

            @Override
            public void fileAdd(String path, String analyzer) {
            }

            @Override
            public void fileAdded(String path, String analyzer) {
            }

            @Override
            public void fileRemove(String path) {
                removedFiles.add(path);
            }

            @Override
            public void fileRemoved(String path) {
            }

            @Override
            public void fileUpdate(String path) {
            }

            public Set<String> getRemovedFiles() {
                return removedFiles;
            }
        };
        RemovedFilesListener listener = new RemovedFilesListener();
        idb.addIndexChangedListener(listener);
        idb.update();

        verify(spyFactory).getIndexDownArgs();
        // Cannot use args.curCount to compare against because it gets reset in indexParallel()
        // as it is reused in that stage of indexing.
        assertNotEquals(0, args.works.size());
        // The expected data has to match the work done in changeGitRepository().
        assertEquals(Set.of(Path.of("/git/Makefile.renamed"),
                        Path.of("/git/main.c"),
                        Path.of("/git/zzz.txt"),
                        Path.of("/git/zzzzzz.txt")),
                args.works.stream().map(v -> Path.of(v.path)).collect(Collectors.toSet()));

        assertEquals(Set.of(
                Path.of("/git/main.o"),
                Path.of("/git/main.c"),
                Path.of("/git/Makefile")
        ), listener.getRemovedFiles().stream().map(Path::of).collect(Collectors.toSet()));

        // Verify the assumption made above.
        verify(idb, times(1)).getIndexDownArgs(any(), any(), any());

        // The initial index (done in setUpClass()) should use file based IndexWorkArgs discovery.
        // Only the update() done here should lead to indexDownUsingHistory(),
        // hence it should be called just once.
        if (historyBased) {
            verify(idb, times(1)).indexDownUsingHistory(any(), any());
        } else {
            // indexDown() is recursive, so it will be called more than once.
            verify(idb, atLeast(1)).indexDown(any(), any(), any());
        }
    }

    // TODO: add test for project with multiple repositories

    // TODO: add test for forced reindex - see if renamedFile() was called for all files
}
