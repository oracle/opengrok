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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.FileCollector;
import org.opengrok.indexer.history.History;
import org.opengrok.indexer.history.HistoryEntry;
import org.opengrok.indexer.history.HistoryException;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.Repository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.history.RepositoryWithHistoryTraversal;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.search.SearchEngine;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.IOUtils;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.CVS;

/**
 * Unit tests for the {@code IndexDatabase} class.
 * <p>
 * This is quite a heavy test class - it runs the indexer before each (parametrized) test,
 * so it might contribute significantly to the overall test run time.
 * </p>
 */
class IndexDatabaseTest {

    private static TestRepository repository;

    private Indexer indexer;

    private RuntimeEnvironment env;

    @BeforeEach
    public void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        // This needs to be set before HistoryGuru is instantiated for the first time,
        // otherwise the annotation cache therein would be permanently set to null.
        env.setAnnotationCacheEnabled(true);

        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));

        // After copying the files from the archive, Git will consider the files to be changed,
        // at least on Windows. This causes some tests, particularly testGetIndexDownArgs() to fail.
        // To avoid this, clone the Git repository.
        Path gitRepositoryRootPath = Path.of(repository.getSourceRoot(), "git");
        Path gitCheckoutPath = Path.of(repository.getSourceRoot(), "gitcheckout");
        Git git = Git.cloneRepository()
                .setURI(gitRepositoryRootPath.toFile().toURI().toString())
                .setDirectory(gitCheckoutPath.toFile())
                .call();
        // The Git object has to be closed, otherwise the move below would fail on Windows with
        // AccessDeniedException due to the file handle still being open.
        git.close();
        IOUtils.removeRecursive(gitRepositoryRootPath);
        Files.move(gitCheckoutPath, gitRepositoryRootPath);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setHistoryEnabled(true);
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        // Restore the project and repository information.
        env.setProjects(new HashMap<>());
        HistoryGuru.getInstance().removeRepositories(List.of("/git"));
        env.setRepositories(repository.getSourceRoot());
        HistoryGuru.getInstance().invalidateRepositories(env.getRepositories(), CommandTimeoutType.INDEXER);
        env.generateProjectRepositoriesMap();

        indexer = Indexer.getInstance();
        indexer.prepareIndexer(
                env, true, true,
                null, null);

        // Reset the state of the git project w.r.t. history based reindex.
        // It is the responsibility of each test that relies on the per project tunable
        // to call gitProject.completeWithDefaults().
        Project gitProject = env.getProjects().get("git");
        gitProject.clearProperties();

        env.setDefaultProjectsFromNames(new TreeSet<>(Arrays.asList("/c")));

        indexer.doIndexerExecution(null, null);

        env.clearFileCollector();
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

    /**
     * Assumes the following.
     * <ul>
     *     <li>default history/annotation check are {@link org.opengrok.indexer.history.FileHistoryCache} and
     *      {@link org.opengrok.indexer.history.FileAnnotationCache}, respectively</li>
     *     <li>the directory names used</li>
     *     <li>the way file paths are constructed ({@link TandemPath})</li>
     * </ul>
     * @param fileName file name to check
     * @param shouldExist whether the data file should exist
     */
    private void checkDataExistence(String fileName, boolean shouldExist) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        HistoryGuru historyGuru = HistoryGuru.getInstance();

        for (String dirName : new String[] {"historycache", "annotationcache", IndexDatabase.XREF_DIR}) {
            File dataDir = new File(env.getDataRootFile(), dirName);
            File file = new File(env.getServerName(), fileName);
            String cacheFile;
            if (dirName.equals("annotationcache")) {
                if (shouldExist) {
                    assertTrue(historyGuru.hasAnnotationCacheForFile(file));
                } else {
                    assertFalse(historyGuru.hasAnnotationCacheForFile(file));
                }
            } else {
                cacheFile = TandemPath.join(fileName, ".gz");
                File dataFile = new File(dataDir, cacheFile);

                if (shouldExist) {
                    assertTrue(dataFile.exists(), "file " + fileName + " not found in " + dirName);
                } else {
                    assertFalse(dataFile.exists(), "file " + fileName + " found in " + dirName);
                }
            }
        }
    }

    /**
     * Test removal of IndexDatabase after file has been removed from a repository.
     * Specifically the xrefs, history/annotation cache, index entries.
     * <p>
     * Assumes the indexer in the setup ran with annotation cache enabled.
     * </p>
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCleanupAfterIndexRemoval(boolean historyBasedReindex) throws Exception {
        final int origNumFiles;

        env.setHistoryBasedReindex(historyBasedReindex);

        String projectName = "git";
        Project project = env.getProjects().get(projectName);
        assertNotNull(project);
        IndexDatabase idb = new IndexDatabase(project);
        assertNotNull(idb);

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
        assertTrue(file.delete());
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
        assertEquals("aa35c25882b9a60a97758e0ceb276a3f8cb4ae3a", doc.get(QueryBuilder.LASTREV));
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

    private void addMergeCommit(Git git, File repositoryRoot) throws Exception {
        // Create and checkout a branch.
        final String branchName = "mybranch";
        git.branchCreate().setName(branchName).call();
        git.checkout().setName(branchName).call();

        // Change a file on the branch.
        addFileAndCommit(git, "new.txt", repositoryRoot, "new file on a branch");

        // Checkout the master branch again.
        git.checkout().setName("master").call();

        // Retrieve the objectId of the latest commit on the branch.
        ObjectId mergeBase = git.getRepository().resolve(branchName);

        // Perform the actual merge without FastForward to see the
        // actual merge-commit even though the merge is trivial.
        git.merge().
                include(mergeBase).
                setCommit(false).
                setFastForward(MergeCommand.FastForwardMode.NO_FF).
                setMessage("merge commit").
                call();

        // Commit the merge separately so that the author can be set.
        // (MergeCommand - a result of git.merge() - does not have the setAuthor() method)
        git.commit().setAuthor("foo bar", "foobar@example.com").call();
    }

    /**
     * Add some commits to the Git repository - change/remove/add/rename a file in separate commits,
     * also add a merge commit.
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

            // Delete a file.
            final String deletedFileName = "header.h";
            File rmFile = new File(repositoryRoot, deletedFileName);
            assertTrue(rmFile.exists());
            git.rm().addFilepattern(deletedFileName).call();
            git.commit().setMessage("delete").setAuthor("foo", "foobar@example.com").setAll(true).call();
            assertFalse(rmFile.exists());

            // Rename some file.
            final String fooFileName = "Makefile";
            final String barFileName = "Makefile.renamed";
            File fooFile = new File(repositoryRoot, fooFileName);
            assertTrue(fooFile.exists());
            File barFile = new File(repositoryRoot, barFileName);
            assertTrue(fooFile.renameTo(barFile));
            git.add().addFilepattern(barFileName).call();
            git.rm().addFilepattern(fooFileName).call();
            git.commit().setMessage("rename").setAuthor("foo", "foobar@example.com").setAll(true).call();
            assertTrue(barFile.exists());
            assertFalse(fooFile.exists());

            addMergeCommit(git, repositoryRoot);
        }
    }

    private static Stream<Arguments> provideParamsFortestGetIndexDownArgs() {
        return Stream.of(
            Arguments.of(false, false, false, false),
            Arguments.of(false, false, false, true),
            Arguments.of(false, false, true, false),
            Arguments.of(false, false, true, true),
            Arguments.of(false, true, false, false),
            Arguments.of(false, true, false, true),
            Arguments.of(false, true, true, false),
            Arguments.of(false, true, true, true),
            Arguments.of(true, false, false, false),
            Arguments.of(true, false, false, true),
            Arguments.of(true, false, true, false),
            Arguments.of(true, false, true, true),
            Arguments.of(true, true, false, false),
            Arguments.of(true, true, false, true),
            Arguments.of(true, true, true, false),
            Arguments.of(true, true, true, true)
        );
    }

    static class AddRemoveFilesListener implements IndexChangedListener {
        // The file sets need to be thread safe because the methods that modify them can be called in parallel.
        private final Set<String> removedFiles = Collections.synchronizedSet(new HashSet<>());

        private final Set<String> addedFiles = Collections.synchronizedSet(new HashSet<>());

        @Override
        public void fileAdd(String path, String analyzer) {
            addedFiles.add(path);
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

        public Set<String> getAddedFiles() {
            return addedFiles;
        }
    }

    /**
     * Test specifically getIndexDownArgs() with IndexDatabase instance.
     * This test ensures that correct set of files is discovered.
     */
    @ParameterizedTest
    @MethodSource("provideParamsFortestGetIndexDownArgs")
    void testGetIndexDownArgs(boolean mergeCommits, boolean renamedFiles, boolean historyBased, boolean perPartes)
            throws Exception {

        assertTrue(env.isHistoryEnabled());

        env.setHistoryBasedReindex(historyBased);
        env.setHandleHistoryOfRenamedFiles(renamedFiles);
        env.setMergeCommitsEnabled(mergeCommits);
        env.setHistoryCachePerPartesEnabled(perPartes);

        IndexDownArgsFactory factory = new IndexDownArgsFactory();
        IndexDownArgsFactory spyFactory = spy(factory);
        IndexDownArgs args = new IndexDownArgs();
        // In this case the getIndexDownArgs() should be called from update() just once so this will suffice.
        when(spyFactory.getIndexDownArgs()).thenReturn(args);

        Project gitProject = env.getProjects().get("git");
        assertNotNull(gitProject);
        gitProject.completeWithDefaults();
        IndexDatabase idbOrig = new IndexDatabase(gitProject, spyFactory);
        assertNotNull(idbOrig);
        IndexDatabase idb = spy(idbOrig);

        File repositoryRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(repositoryRoot.isDirectory());
        changeGitRepository(repositoryRoot);

        // Re-generate the history cache so that the data is ready for history based re-index.
        HistoryGuru.getInstance().clear();
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);
        env.generateProjectRepositoriesMap();

        // Check history cache w.r.t. the merge changeset.
        File mergeFile = new File(repositoryRoot, "new.txt");
        History history = HistoryGuru.getInstance().getHistory(mergeFile, false, false, false);
        assertNotNull(history);
        assertNotNull(history.getHistoryEntries());
        boolean containsMergeCommitMessage = history.getHistoryEntries().stream().
                map(HistoryEntry::getMessage).collect(Collectors.toSet()).contains("merge commit");
        if (mergeCommits) {
            assertTrue(containsMergeCommitMessage);
        } else {
            assertFalse(containsMergeCommitMessage);
        }

        // Setup and use listener for the "removed" files.
        AddRemoveFilesListener listener = new AddRemoveFilesListener();
        idb.addIndexChangedListener(listener);
        idb.update();

        verify(spyFactory).getIndexDownArgs();
        // Cannot use args.curCount to compare against because it gets reset in indexParallel()
        // as it is reused in that stage of indexing.
        assertNotEquals(0, args.works.size());
        // The expected data has to match the work done in changeGitRepository().
        Set<Path> expectedFileSet = new HashSet<>();
        expectedFileSet.add(Path.of("/git/Makefile.renamed"));
        expectedFileSet.add(Path.of("/git/main.c"));
        expectedFileSet.add(Path.of("/git/zzz.txt"));
        expectedFileSet.add(Path.of("/git/zzzzzz.txt"));
        expectedFileSet.add(Path.of("/git/new.txt"));
        assertEquals(expectedFileSet, args.works.stream().map(v -> Path.of(v.path)).collect(Collectors.toSet()));

        assertEquals(Set.of(
                Path.of("/git/header.h"),
                Path.of("/git/main.c"),
                Path.of("/git/Makefile")
        ), listener.getRemovedFiles().stream().map(Path::of).collect(Collectors.toSet()));

        // Verify the assumption made above.
        verify(idb, times(1)).getIndexDownArgs(any(), any(), any());

        checkIndexDown(historyBased, idb);
    }

    private void checkIndexDown(boolean historyBased, IndexDatabase idb) throws IOException {
        // The initial index (done in setUpClass()) should use file based IndexWorkArgs discovery.
        // Only the update() done in the actual test should lead to indexDownUsingHistory(),
        // hence it should be called just once.
        if (historyBased) {
            verify(idb, times(1)).indexDownUsingHistory(any(), any());
            verify(idb, times(0)).indexDown(any(), any(), any());
        } else {
            // indexDown() is recursive, so it will be called more than once.
            verify(idb, times(0)).indexDownUsingHistory(any(), any());
            verify(idb, atLeast(1)).indexDown(any(), any(), any());
        }
    }

    /**
     * Make sure that history based reindex is not performed for projects
     * where some repositories are not instances of {@code RepositoryWithHistoryTraversal}
     * or have the history based reindex explicitly disabled.
     * <p>
     * Instead of checking the result of the functions that make the decision, check the actual indexing.
     * </p>
     */
    @EnabledForRepository(CVS)
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testHistoryBasedReindexVsProjectWithDiverseRepos(boolean useCvs) throws Exception {
        env.setHistoryBasedReindex(true);

        // Create a new project with two repositories.
        String projectName = "new";
        Path projectPath = Path.of(repository.getSourceRoot(), projectName);
        assertTrue(projectPath.toFile().mkdirs());
        assertTrue(projectPath.toFile().isDirectory());

        String disabledGitRepoName = "git1";

        if (useCvs) {
            // Copy CVS repository underneath the project.
            String subrepoName = "cvssubrepo";
            Path destinationPath = Path.of(repository.getSourceRoot(), projectName, subrepoName);
            Path sourcePath = Path.of(repository.getSourceRoot(), "cvs_test", "cvsrepo");
            assertTrue(sourcePath.toFile().exists());
            assertTrue(destinationPath.toFile().mkdirs());
            repository.copyDirectory(sourcePath, destinationPath);
            assertTrue(destinationPath.toFile().exists());

            Repository subRepo = RepositoryFactory.getRepository(destinationPath.toFile());
            assertFalse(subRepo instanceof RepositoryWithHistoryTraversal);
        } else {
            // Clone Git repository underneath the project.
            String cloneUrl = Path.of(repository.getSourceRoot(), "git").toFile().toURI().toString();
            Path repositoryRootPath = Path.of(repository.getSourceRoot(), projectName, disabledGitRepoName);
            Git git = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(repositoryRootPath.toFile())
                    .call();
            git.close();
            assertTrue(repositoryRootPath.toFile().isDirectory());
        }

        // Clone Git repository underneath the project and make a change there.
        String cloneUrl = Path.of(repository.getSourceRoot(), "git").toFile().toURI().toString();
        Path repositoryRootPath = Path.of(repository.getSourceRoot(), projectName, "git");
        Git git = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(repositoryRootPath.toFile())
                .call();
        git.close();
        assertTrue(repositoryRootPath.toFile().isDirectory());
        changeGitRepository(repositoryRootPath.toFile());

        // Rescan the repositories.
        HistoryGuru.getInstance().clear();
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);
        env.setRepositories(new ArrayList<>(HistoryGuru.getInstance().getRepositories()));
        env.generateProjectRepositoriesMap();

        // Assert the repositories were detected.
        Project project = env.getProjects().get(projectName);
        assertNotNull(project);
        List<RepositoryInfo> projectRepos = env.getProjectRepositoriesMap().get(project);
        assertNotNull(projectRepos);
        assertEquals(2, projectRepos.size());

        if (!useCvs) {
            for (RepositoryInfo repo : projectRepos) {
                if (repo.getDirectoryNameRelative().equals(disabledGitRepoName)) {
                    repo.setHistoryBasedReindex(false);
                }
            }
        }

        verifyIndexDown(project, false);
    }

    /**
     * Make sure the files detected for a sub-repository are correctly stored in the appropriate
     * {@code FileCollector} instance.
     */
    @Test
    void testHistoryBasedReindexWithEligibleSubRepo() throws Exception {
        env.setHistoryBasedReindex(true);

        assertNull(env.getFileCollector("git"));

        Project gitProject = env.getProjects().get("git");
        assertNotNull(gitProject);
        gitProject.completeWithDefaults();

        // Create a Git repository underneath the existing git repository and make a change there.
        File repositoryRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(repositoryRoot.isDirectory());
        changeGitRepository(repositoryRoot);
        String subRepoName = "subrepo";
        File subRepositoryRoot = new File(repositoryRoot, subRepoName);
        String changedFileName = "subfile.txt";
        try (Git git = Git.init().setDirectory(subRepositoryRoot).call()) {
            addFileAndCommit(git, changedFileName, subRepositoryRoot, "new file in subrepo");
        }
        assertTrue(new File(subRepositoryRoot, changedFileName).exists());

        HistoryGuru.getInstance().clear();

        // Rescan the repositories and refresh the history cache which should also collect the files
        // for the 2nd stage of indexing.
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);

        // Verify the collected files.
        FileCollector fileCollector = env.getFileCollector("git");
        assertNotNull(fileCollector);
        assertTrue(fileCollector.getFiles().size() > 1);
        assertTrue(fileCollector.getFiles().
                contains(File.separator + gitProject.getName() +
                        File.separator + subRepoName +
                        File.separator + changedFileName));
    }

    /**
     * Verify project specific tunable has effect on how the indexing will be performed.
     * The global history based tunable is tested in testGetIndexDownArgs().
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testHistoryBasedReindexProjectTunable(boolean historyBased) throws Exception {
        env.setHistoryBasedReindex(!historyBased);

        // Make a change in the git repository.
        File repositoryRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(repositoryRoot.isDirectory());
        changeGitRepository(repositoryRoot);

        // The per project tunable should override the global tunable.
        Project gitProject = env.getProjects().get("git");
        gitProject.setHistoryBasedReindex(historyBased);
        gitProject.completeWithDefaults();

        HistoryGuru.getInstance().clear();
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);
        env.generateProjectRepositoriesMap();

        verifyIndexDown(gitProject, historyBased);

        gitProject.setHistoryBasedReindex(true);
    }

    /**
     * Test history based reindex if there was no change to the repository.
     */
    @Test
    void testHistoryBasedReindexWithNoChange() throws Exception {
        env.setHistoryBasedReindex(true);

        Project gitProject = env.getProjects().get("git");
        gitProject.completeWithDefaults();

        HistoryGuru.getInstance().clear();
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);
        env.generateProjectRepositoriesMap();

        verifyIndexDown(gitProject, true);
    }

    private void verifyIndexDown(Project gitProject, boolean historyBased) throws Exception {
        // verify that indexer did not use history based reindex.
        IndexDatabase idbOrig = new IndexDatabase(gitProject);
        assertNotNull(idbOrig);
        IndexDatabase idb = spy(idbOrig);
        idb.update();
        checkIndexDown(historyBased, idb);
    }

    /**
     * Test forced reindex - see if removeFile() was called for all files in the repository
     * even though there was no change.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testForcedReindex(boolean historyBased) throws Exception {

        env.setHistoryBasedReindex(historyBased);

        Project gitProject = env.getProjects().get("git");
        assertNotNull(gitProject);
        gitProject.completeWithDefaults();
        IndexDatabase idbOrig = new IndexDatabase(gitProject);
        assertNotNull(idbOrig);
        IndexDatabase idb = spy(idbOrig);

        // Re-generate the history cache so that the git repository is ready for history based re-index.
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);
        env.generateProjectRepositoriesMap();

        // Emulate forcing reindex from scratch.
        doReturn(false).when(idb).checkSettings(any(), any());

        // Setup and use listener for the "removed" files.
        AddRemoveFilesListener listener = new AddRemoveFilesListener();
        idb.addIndexChangedListener(listener);
        idb.update();

        checkIndexDown(historyBased, idb);

        // List the files in the /git directory tree and compare that to the IndexDatabase file sets.
        Path repoRoot = Path.of(repository.getSourceRoot(), "git");
        Set<Path> result;
        try (Stream<Path> walk = Files.walk(repoRoot)) {
            result = walk.filter(Files::isRegularFile).
                    filter(p -> !p.toString().contains(".git")).
                    collect(Collectors.toSet());
        }
        Set<Path> expectedFileSet = result.stream().map(f -> {
                try {
                    return Path.of(RuntimeEnvironment.getInstance().getPathRelativeToSourceRoot(f.toFile()));
                } catch (IOException | ForbiddenSymlinkException e) {
                    return null;
                }
            }).collect(Collectors.toSet());
        assertEquals(expectedFileSet, listener.getRemovedFiles().stream().map(Path::of).collect(Collectors.toSet()));
        assertEquals(expectedFileSet, listener.getAddedFiles().stream().map(Path::of).collect(Collectors.toSet()));
    }

    /**
     * make sure the initial indexing is made using indexDown() even though history based reindex is possible.
     */
    @Test
    void testInitialReindexWithHistoryBased() throws Exception {
        env.setHistoryBasedReindex(true);

        // Delete the index (and all data in fact).
        assertFalse(repository.getDataRoot().isEmpty());
        IOUtils.removeRecursive(Path.of(repository.getDataRoot()));
        assertFalse(Path.of(repository.getDataRoot()).toFile().exists());

        // Update the index of the project.
        Project gitProject = env.getProjects().get("git");
        assertNotNull(gitProject);
        IndexDatabase idbOrig = new IndexDatabase(gitProject);
        assertNotNull(idbOrig);
        IndexDatabase idb = spy(idbOrig);
        idb.update();

        // Check that the index for the git project was created.
        Document doc = IndexDatabase.getDocument(Path.of(repository.getSourceRoot(), "git", "main.c").toFile());
        assertNotNull(doc);

        checkIndexDown(false, idb);
    }

    /**
     * project-less configuration should lead to file-system based reindex.
     */
    @Test
    void testProjectLessReindexVsHistoryBased() throws Exception {
        env.setProjectsEnabled(false);

        // Make a change in the git repository.
        File repositoryRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(repositoryRoot.isDirectory());
        changeGitRepository(repositoryRoot);

        IndexDatabase idbOrig = new IndexDatabase();
        assertNotNull(idbOrig);
        IndexDatabase idb = spy(idbOrig);
        idb.update();

        checkIndexDown(false, idb);
    }

    /**
     * Test that incremental reindex will re-generate the annotation with correct revision.
     * This is done to prevent {@link HistoryGuru#createAnnotationCache(File, String)} to be called
     * with invalid/old revision in {@code IndexDatabase#addFile(File, String, Ctags)}.
     * <p>
     * Assumes the fallback argument of {@link HistoryGuru#annotate(File, String, boolean)}
     * does the right thing.
     * </p>
     */
    @Test
    void testUpdateAnnotationCacheRevision() throws Exception {
        File repoRoot = new File(env.getSourceRootPath(), "git");
        assertTrue(repoRoot.isDirectory());
        File file = new File(repoRoot, "main.c");
        assertTrue(file.exists());

        // Check annotation cache existence.
        HistoryGuru historyGuru = HistoryGuru.getInstance();
        assertTrue(historyGuru.hasAnnotationCacheForFile(file));

        // Get the latest revision of given file in the history before changing it.
        String prevRev = getLastRevisionFromHistory(file, historyGuru);
        assertNotNull(prevRev);

        // Check the annotation cache entry for the file has the correct revision set.
        // Make sure the annotation was taken from the cache by setting the fallback argument to false.
        Annotation annotation = historyGuru.annotate(file, null, false);
        assertNotNull(annotation);
        assertEquals(prevRev, annotation.getRevision());

        // Add some changesets to the repository.
        changeGitRepository(repoRoot);

        // Verify the changeset was applied. At this point LatestRevisionUtil#getLatestRevision() should
        // fall back to the history method, because the index document is out of sync w.r.t. its pre-image file,
        // however to be sure use the history directly.
        String newRev = getLastRevisionFromHistory(file, historyGuru);
        assertNotEquals(newRev, prevRev);

        // Perform reindex.
        indexer.prepareIndexer(
                env, true, true,
                null, null);
        indexer.doIndexerExecution(null, null);

        // Check that the annotation cache entry contains the new revision.
        // Make sure the annotation was taken from the cache by setting the fallback argument to false.
        annotation = historyGuru.annotate(file, null, false);
        assertNotNull(annotation);
        assertEquals(newRev, annotation.getRevision());
    }

    private static String getLastRevisionFromHistory(File file, HistoryGuru historyGuru) throws HistoryException {
        History history = historyGuru.getHistory(file, false);
        assertNotNull(history);
        HistoryEntry historyEntry = history.getLastHistoryEntry();
        assertNotNull(historyEntry);
        String prevRev = historyEntry.getRevision();
        return prevRev;
    }

    private static Stream<Arguments> provideParamsForTestAnnotationCacheProjectTunable() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(true, true)
        );
    }

    /**
     * Per project tunable should control how whether annotation cache will be created.
     * <p>
     * Depends on the {@code fallback} argument {@link HistoryGuru#annotate(File, String, boolean)}
     * to be implemented correctly, i.e. do not perform fallback to repository annotate method
     * if annotation cache is not present.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("provideParamsForTestAnnotationCacheProjectTunable")
    void testAnnotationCacheProjectTunable(boolean useAnnotationCache, boolean isHistoryEnabled) throws Exception {
        env.setAnnotationCacheEnabled(!useAnnotationCache);
        env.setHistoryEnabled(isHistoryEnabled);

        // Ignore the reindex performed in the setup and set custom data root,
        // so that annotation cache can be checked reliably.
        File repositoryRoot = new File(repository.getSourceRoot(), "git");
        assertTrue(repositoryRoot.isDirectory());
        String dataRootOrig = env.getDataRootPath();
        Path dataRoot = Files.createTempDirectory("indexDbAnnotationTest");
        env.setDataRoot(dataRoot.toString());

        // Set the per project tunable.
        Project gitProject = env.getProjects().get("git");
        boolean projectUseAnnotationOrig = gitProject.isAnnotationCacheEnabled();
        gitProject.setAnnotationCacheEnabled(useAnnotationCache);
        gitProject.completeWithDefaults();

        // verify initial state
        File file = new File(repositoryRoot, "main.c");
        assertTrue(file.exists());
        HistoryGuru historyGuru = HistoryGuru.getInstance();
        assertNull(historyGuru.annotate(file, null, false));

        // reindex
        HistoryGuru.getInstance().clear();
        indexer.prepareIndexer(
                env, true, true,
                List.of("/git"), null);
        env.generateProjectRepositoriesMap();
        indexer.doIndexerExecution(null, null);

        // verify
        assertTrue(file.exists());
        Annotation annotation = historyGuru.annotate(file, null, false);
        if (useAnnotationCache && isHistoryEnabled) {
            assertNotNull(annotation);
        } else {
            assertNull(annotation);
        }

        // cleanup
        gitProject.setHistoryBasedReindex(projectUseAnnotationOrig);
        env.setDataRoot(dataRootOrig);
        IOUtils.removeRecursive(dataRoot);
    }
}
