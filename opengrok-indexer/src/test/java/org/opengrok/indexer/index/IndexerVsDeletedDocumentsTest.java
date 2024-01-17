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
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NoMergeScheduler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.FileCollector;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.opengrok.indexer.index.IndexCheck.getDeletedUids;
import static org.opengrok.indexer.index.IndexCheck.getIndexReader;
import static org.opengrok.indexer.index.IndexCheck.getLiveDocumentPaths;

/**
 * This class provides tests that verify the indexer correctly jumps over deleted documents in the index,
 * thus not leading to weird artifacts like negative LOC counts or multiple documents with the same path
 * returned from the search.
 */
class IndexerVsDeletedDocumentsTest {
    RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static String getRandomString(int numChars) {
        Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numChars; i++) {
            sb.append((char) (random.nextInt(26) + 'A'));
        }

        return sb.toString();
    }

    private static class FileWork {
        Path path;
        WorkType workType;
        String content = null;

        FileWork(Path path, WorkType workType) {
            this.path = path;
            this.workType = workType;
        }

        void doIt() throws IOException {
            switch (workType) {
                case CREATE:
                    Files.createFile(path);
                    for (int i = 0; i < 16; i++) {
                        Files.writeString(path, getRandomString(32) + " ", StandardOpenOption.APPEND);
                    }
                    Files.writeString(path, "\n", StandardOpenOption.APPEND);
                    break;
                case DELETE:
                    Files.delete(path);
                    break;
                case UPDATE:
                    if (this.content == null) {
                        content = getRandomString(16) + " ";
                    }
                    Files.writeString(path, content + "\n", StandardOpenOption.APPEND);
                    break;
            }
        }
    }

    private enum WorkType {
        UPDATE,
        DELETE,
        CREATE,
    }

    /**
     * IndexChangedListener derived class that records removed paths in a list.
     * This list is then used for verification.
     */
    static class RecordingIndexChangedListener implements IndexChangedListener {
        public List<String> removedPaths = new ArrayList<>();

        @Override
        public void fileAdd(String path, String analyzer) {
            System.out.printf("Add: %s (%s)%n", path, analyzer);
        }

        @Override
        public void fileRemove(String path) {
            removedPaths.add(path);
            System.out.printf("Remove file: %s%n", path);
        }

        @Override
        public void fileAdded(String path, String analyzer) {
        }

        @Override
        public void fileRemoved(String path) {
        }

        void reset() {
            removedPaths.clear();
        }
    }

    /**
     * {@link IndexWriterConfigFactory} extension that uses NoMerge* settings in order to produce
     * an index with deleted documents.
     */
    static class NoMergeIndexWriterConfigFactory extends IndexWriterConfigFactory {
        @Override
        public IndexWriterConfig get() {
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();

            Analyzer analyzer = AnalyzerGuru.getAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            iwc.setRAMBufferSizeMB(env.getRamBufferSize());

            iwc.setMergePolicy(NoMergePolicy.INSTANCE);
            iwc.setMergeScheduler(NoMergeScheduler.INSTANCE);

            return iwc;
        }
    }

    private Path getIndexPath(String projectName) throws IOException {
        Path indexPath = Path.of(env.getDataRootPath(), IndexDatabase.INDEX_DIR,
                env.isProjectsEnabled() ? projectName : "");
        assertTrue(indexPath.toFile().isDirectory());
        return indexPath;
    }

    @BeforeEach
    void setup() throws IOException {
        // Create and set source and data root.
        TestRepository testRepository = new TestRepository();
        testRepository.createEmpty();
        assertTrue(env.getSourceRootFile().isDirectory());
        assertTrue(env.getDataRootFile().isDirectory());
    }

    @AfterEach
    void cleanup() throws IOException {
        IOUtils.removeRecursive(Path.of(env.getDataRootPath()));
        // FileUtils.deleteDirectory() avoids AccessDeniedException on Windows.
        FileUtils.deleteDirectory(env.getSourceRootFile());
    }

    private static Stream<Arguments> provideAguments() {
        return Stream.of(
                Arguments.of(false, true),
                Arguments.of(false, false),
                Arguments.of(true, true),
                Arguments.of(true, false)
        );
    }

    /**
     * Create index under data root that will contain:
     * <ul>
     * <li>updated documents (i.e. deleted and added)</li>
     * <li>deleted documents</li>
     * </ul>
     * @param projectsEnabled whether projects should be enabled
     * @param useGit whether to initialize Git repository and commit the changes
     */
    @ParameterizedTest
    @MethodSource("provideAguments")
    void testIndexTraversalWithDeletedDocuments(boolean projectsEnabled, boolean useGit) throws Exception {
        // Setup for the indexer.
        String projectName = "foo";
        File projectRoot = Path.of(env.getSourceRootPath(), projectName).toFile();
        env.setProjectsEnabled(projectsEnabled);
        assertTrue(projectRoot.mkdir());
        assertTrue(projectRoot.exists());

        final String fooFileName = "foo.txt";
        final Path fooPath = Path.of(env.getSourceRootPath(), projectName, fooFileName);
        final Path barPath = Path.of(env.getSourceRootPath(), projectName, "bar.txt");

        RepositoryFactory.initializeIgnoredNames(env);
        Indexer indexer = Indexer.getInstance();

        Git gitRepo = null;
        final String authorName = "author";
        final String authorEmail = "author@example.com";
        if (useGit) {
            gitRepo = Git.init().setDirectory(projectRoot).call();
        }

        // Create initial file content.
        List<FileWork> initialWork = List.of(new FileWork(fooPath, WorkType.CREATE),
                        new FileWork(barPath, WorkType.CREATE));
        for (FileWork fileWork : initialWork) {
            fileWork.doIt();
            if (gitRepo != null) {
                gitRepo.add().addFilepattern(fileWork.path.getFileName().toString()).call();
            }
        }
        if (gitRepo != null) {
            gitRepo.commit().setMessage("initial content").setAuthor(authorName, authorEmail).call();
        }
        // Add the project and optionally detect the Git repository.
        env.setProjects(new HashMap<>());
        HistoryGuru.getInstance().clear();
        env.setRepositories(new ArrayList<>());
        env.generateProjectRepositoriesMap();
        indexer.prepareIndexer(
                env, useGit, projectsEnabled,
                null, null);
        Project project = null;
        if (projectsEnabled) {
            project = env.getProjects().get(projectName);
            assertNotNull(project);
        }
        if (useGit) {
            // Check the Git repository was detected.
            assertTrue(env.getRepositories().size() > 0);
        }
        indexer.doIndexerExecution(null, null);

        /*
         * Use IndexDatabase instead of indexer.doIndexerExecution() for fine-grained control over indexing.
         * Specifically, use no merge index writer settings to increase the probability of creating multi-segment index
         * with deleted documents which are necessary for the test of the update() behavior.
         */
        IndexDatabase indexDatabaseOrig = new IndexDatabase(project,
                new IndexDownArgsFactory(), new NoMergeIndexWriterConfigFactory());
        IndexDatabase indexDatabase = spy(indexDatabaseOrig);

        RecordingIndexChangedListener listener = new RecordingIndexChangedListener();
        indexDatabase.addIndexChangedListener(listener);

        // Perform file work and index multiple times. Repeat this until there are deleted files in the index
        // or maximum number of iterations is reached.
        for (int i = 0; i < 16; i++) {
            List<FileWork> work = List.of(new FileWork(fooPath, WorkType.UPDATE),
                    new FileWork(barPath, WorkType.UPDATE));
            for (FileWork fileWork : work) {
                fileWork.doIt();
                if (gitRepo != null) {
                    gitRepo.add().addFilepattern(fileWork.path.getFileName().toString()).call();
                }
            }

            // Creating a bunch of new files increases the chances of the fooPath/barPath updates resulting in
            // deleted documents.
            for (int j = 0; j < 8; j++) {
                // Might as well use temporary file to avoid failures, however 32 chars is enough for now.
                Path filePath = Path.of(env.getSourceRootPath(), projectName,
                        getRandomString(32) + "-" + i + ".txt");
                new FileWork(filePath, WorkType.CREATE).doIt();
                if (gitRepo != null) {
                    gitRepo.add().addFilepattern(filePath.getFileName().toString()).call();
                }
            }

            if (gitRepo != null) {
                gitRepo.commit().setMessage(String.format("iteration %d", i)).setAuthor(authorName, authorEmail).call();
            }

            listener.reset();
            FileCollector fileCollector = env.getFileCollector(projectName);
            if (fileCollector != null) {
                fileCollector.reset();
            }
            assertEquals(0, listener.removedPaths.size());
            System.out.printf("indexing iteration #%d%n", i);
            indexer.prepareIndexer(
                    env, useGit, projectsEnabled,
                    null, null);
            env.generateProjectRepositoriesMap();
            indexDatabase.update();

            if (useGit && projectsEnabled) {
                // Verify the history based reindex was actually used.
                verify(indexDatabase, atLeast(1)).processFileHistoryBased(any(), any(), any());
            } else {
                verify(indexDatabase, atLeast(1)).processFile(any(), any(), any());
            }

            // Only fooPath and barPath are updated in each cycle, hence there should be 2 removals.
            assertEquals(2, listener.removedPaths.size());

            // At most one fileRemove() should be called for each path.
            assertEquals(new HashSet<>(listener.removedPaths).size(), listener.removedPaths.size());

            if (getDeletedUids(getIndexPath(projectName)).size() > 0) {
                break;
            }
        }

        if (gitRepo != null) {
            Status gitStatus = gitRepo.status().call();
            // Check the status is clear, i.e. there are no uncommitted changes.
            assertEquals(0, gitStatus.getUncommittedChanges().size());

            int commits = 0;
            for (RevCommit commit : gitRepo.log().call()) {
                commits++;

                // Check the commit contain fooPath.
                RevTree tree = commit.getTree();
                try (Repository repo = gitRepo.getRepository(); TreeWalk treeWalk = new TreeWalk(repo)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    // Should be sufficient to check just one of the paths expected to be changed.
                    treeWalk.setFilter(PathFilter.create(fooFileName));
                    assertTrue(treeWalk.next());
                }
            }
            assertTrue(commits > 0);
        }

        // Make sure there are some deleted documents.
        try (IndexReader indexReader = getIndexReader(getIndexPath(projectName))) {
            assertTrue(indexReader.numDeletedDocs() > 0);
        }

        checkLiveDocs(projectName);

        if (gitRepo != null) {
            gitRepo.close();
        }
    }

    /**
     * Check there is at most one live document for each path by doing terms traversal,
     * similar to what is done in {@link IndexDatabase#update()}.
     */
    private void checkLiveDocs(String projectName) throws IOException {
        List<String> livePaths = getLiveDocumentPaths(getIndexPath(projectName));

        assertTrue(livePaths.size() > 0);
        assertEquals(new HashSet<>(livePaths).size(), livePaths.size());
    }
}
