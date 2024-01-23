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
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.util.FileUtilities;
import org.opengrok.indexer.util.TestRepository;

/**
 * Verify index check.
 * @author Vladimír Kotal
 */
class IndexCheckTest {

    private TestRepository repository;
    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    private Configuration configuration;

    @BeforeAll
    static void setUpClass() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        repository = new TestRepository();
        repository.create(IndexerTest.class.getResource("/repositories"));

        configuration = new Configuration();
        configuration.setDataRoot(env.getDataRootPath());
        configuration.setSourceRoot(env.getSourceRootPath());
    }

    @AfterEach
    void tearDown() throws IOException {
        repository.destroy();
    }

    /**
     * Generate index(es) with history enabled, check the index.
     */
    private void testIndex(boolean projectsEnabled, List<String> subFiles, IndexCheck.IndexCheckMode mode) throws Exception {
        env.setHistoryEnabled(false);
        configuration.setHistoryEnabled(false);
        env.setProjectsEnabled(projectsEnabled);
        configuration.setProjectsEnabled(projectsEnabled);
        Indexer.getInstance().prepareIndexer(env, true, true,
                null, null);
        Indexer.getInstance().doIndexerExecution(null, null);

        // The configuration needs to be populated with projects discovered in prepareIndexer()
        // for the project based index check to work as it uses configuration rather than RuntimeEnvironment.
        if (projectsEnabled) {
            configuration.setProjects(env.getProjects());
        }

        try (IndexCheck indexCheck = new IndexCheck(configuration, subFiles)) {
            assertDoesNotThrow(() -> indexCheck.check(mode));
        }
    }

    @Test
    void testIndexVersionNoIndex() throws Exception {
        try (IndexCheck indexCheck = new IndexCheck(configuration)) {
            assertDoesNotThrow(() -> indexCheck.check(IndexCheck.IndexCheckMode.VERSION));
        }
    }

    @Test
    void testIndexVersionProjects() throws Exception {
        testIndex(true, new ArrayList<>(), IndexCheck.IndexCheckMode.VERSION);
    }

    @Test
    void testIndexVersionSelectedProjects() throws Exception {
        testIndex(true, Arrays.asList("mercurial", "git"), IndexCheck.IndexCheckMode.VERSION);
    }

    @Test
    void testIndexVersionNoProjects() throws Exception {
        testIndex(false, new ArrayList<>(), IndexCheck.IndexCheckMode.VERSION);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIndexVersionOldIndex(boolean isProjectsEnabled) throws Exception {
        Path indexPath = Path.of(configuration.getDataRoot(), "index");

        configuration.setProjectsEnabled(isProjectsEnabled);
        if (isProjectsEnabled) {
            final String[] projectNames = new String[]{"foo", "bar"};
            Files.createDirectory(indexPath);
            for (String projectName : projectNames) {
                Path i = indexPath.resolve(projectName);
                Files.createDirectory(i);
                extractOldIndex(i);
            }
            configuration.setProjects(Arrays.stream(projectNames).map(Project::new).
                    collect(Collectors.toMap(Project::getName, Function.identity())));
        } else {
            Files.createDirectory(indexPath);
            extractOldIndex(indexPath);
        }

        try (IndexCheck indexCheck = new IndexCheck(configuration)) {
            IndexCheck.IndexCheckMode mode = IndexCheck.IndexCheckMode.VERSION;
            assertThrows(IndexCheckException.class, () -> indexCheck.check(mode));

            // Recheck to see if the exception contains the expected list of paths that failed the check.
            IndexCheckException exception = null;
            try {
                indexCheck.check(mode);
            } catch (IndexCheckException e) {
                exception = e;
            }
            assertNotNull(exception);

            // The webapp index check handling relies on the paths to be the source root paths.
            Set<Path> failedPaths = exception.getFailedPaths();
            assertFalse(failedPaths.isEmpty());
            assertEquals(0,
                    failedPaths.stream().filter(p -> !p.startsWith(configuration.getSourceRoot())).count());

            if (isProjectsEnabled) {
                assertEquals(configuration.getProjects().keySet().size(), failedPaths.size());
            }
        }
    }

    private void extractOldIndex(Path indexPath) throws IOException {
        File indexDir = new File(indexPath.toString());
        assertTrue(indexDir.isDirectory(), "index directory check");
        URL oldIndex = getClass().getResource("/index/oldindex.zip");
        assertNotNull(oldIndex, "resource needs to be non null");
        File archive = new File(oldIndex.getPath());
        assertTrue(archive.isFile(), "archive exists");
        FileUtilities.extractArchive(archive, indexDir);
    }

    /**
     * Empty directory should pass the index version check.
     */
    @Test
    void testEmptyDir(@TempDir Path tempDir) throws Exception {
        assertEquals(0, Objects.requireNonNull(tempDir.toFile().list()).length);
        try (IndexCheck indexCheck = new IndexCheck(configuration)) {
            indexCheck.checkDir(null, tempDir, IndexCheck.IndexCheckMode.VERSION);
        }
    }

    /**
     * Check that {@link IOException} thrown during index check is propagated further.
     * Runs only on Unix systems because the {@link IOException} is not thrown on Windows
     * for non-existent directories.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIndexCheckIOException(boolean isProjectsEnabled) throws Exception {
        configuration.setProjectsEnabled(isProjectsEnabled);
        if (isProjectsEnabled) {
            final String projectName = "foo";
            configuration.setProjects(Map.of(projectName, new Project(projectName)));
        }

        final IndexCheck.IndexCheckMode mode = IndexCheck.IndexCheckMode.VERSION;
        try (IndexCheck indexCheck = Mockito.spy(new IndexCheck(configuration))) {
            doThrow(IOException.class).when(indexCheck).checkDir(isA(Path.class), isA(Path.class), eq(mode));

            assertThrows(IOException.class, () -> indexCheck.check(mode));
            verify(indexCheck).checkDir(isA(Path.class), isA(Path.class), eq(mode));
        }
    }

    @Test
    void testIndexDocumentsCheckProjects() throws Exception {
        testIndex(true, Arrays.asList("mercurial", "git"), IndexCheck.IndexCheckMode.DEFINITIONS);
    }

    @Test
    void testIndexDocumentsCheckNoProjects() throws Exception {
        testIndex(false, new ArrayList<>(), IndexCheck.IndexCheckMode.DEFINITIONS);
    }

    /**
     * Make sure the {@link IndexCheck#check(IndexCheck.IndexCheckMode)} can be called multiple times
     * from the same {@link IndexCheck} instance.
     * This is essentially testing that the executor used within is not shutdown at the end of each check.
     */
    @Test
    void testMultipleTestsWithSameInstance() throws Exception {
        env.setHistoryEnabled(false);
        configuration.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        configuration.setProjectsEnabled(true);
        Indexer.getInstance().prepareIndexer(env, true, true,
                null, null);
        Indexer.getInstance().doIndexerExecution(null, null);

        // The configuration needs to be populated with projects discovered in prepareIndexer()
        // for the project based index check to work as it uses configuration rather than RuntimeEnvironment.
        configuration.setProjects(env.getProjects());

        try (IndexCheck indexCheck = new IndexCheck(configuration)) {
            for (int i = 0; i < 3; i++) {
                assertDoesNotThrow(() -> indexCheck.check(IndexCheck.IndexCheckMode.VERSION));
            }
        }
    }

    @Test
    void testNullConfiguration() throws Exception {
        assertThrows(NullPointerException.class, () -> {
                    new IndexCheck(null);
                }
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testMissingSourceDocumentCheck(boolean projectsEnabled) throws Exception {
        env.setProjectsEnabled(projectsEnabled);
        configuration.setProjectsEnabled(projectsEnabled);
        Indexer.getInstance().prepareIndexer(env, true, projectsEnabled,
                null, null);
        Indexer.getInstance().doIndexerExecution(null, null);

        final String sourceRoot = env.getSourceRootPath();
        Path originPath = Path.of(sourceRoot, "git", "main.c");
        assertTrue(originPath.toFile().isFile());
        Path tempPath = Path.of(sourceRoot, "git", "main.c.tmp");
        Files.move(originPath, tempPath);

        // The configuration needs to be populated with projects discovered in prepareIndexer()
        // for the project based index check to work as it uses configuration rather than RuntimeEnvironment.
        if (projectsEnabled) {
            configuration.setProjects(env.getProjects());
        }

        try (IndexCheck indexCheck = new IndexCheck(configuration)) {
            IndexCheckException exception = assertThrows(IndexCheckException.class,
                    () -> indexCheck.check(IndexCheck.IndexCheckMode.DOCUMENTS));
            assertEquals(1, exception.getFailedPaths().size());
            Path expectedPath;
            if (projectsEnabled) {
                expectedPath = Path.of(sourceRoot, "git");
            } else {
                expectedPath = Path.of(sourceRoot);
            }
            assertTrue(exception.getFailedPaths().contains(expectedPath));
        }

        // cleanup
        Files.move(tempPath, originPath);
    }
}
