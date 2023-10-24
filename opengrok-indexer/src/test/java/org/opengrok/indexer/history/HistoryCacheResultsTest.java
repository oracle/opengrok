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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HistoryCacheResultsTest {

    private static TestRepository repository = new TestRepository();

    private static RuntimeEnvironment env;

    @BeforeAll
    static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();
        env.setHistoryEnabled(true);

        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResource("/repositories"));
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @AfterAll
    static void tearDownClass() {
        repository.destroy();
    }

    private void corruptGitRepo(File root, final String repoName) throws Exception {
        GitRepository gitrepo = (GitRepository) RepositoryFactory.getRepository(root);
        assertNotNull(gitrepo);

        Path localPath = Path.of(repository.getSourceRoot(), repoName);
        Path l = Path.of(".git", "refs", "heads", "master");
        Path objPath = localPath.resolve(l);
        assertTrue(objPath.toFile().exists());
        try (Writer writer = new FileWriter(objPath.toFile())) {
            writer.write("corrupt");
        }
    }

    /**
     * Test that the return value of {@link Indexer#prepareIndexer(RuntimeEnvironment, boolean, boolean, List, List)}
     * contains the repository for which history cache cannot be generated and associated exception.
     */
    @Test
    void testCorruptRepository() throws Exception {
        Map<Repository, Optional<Exception>> results = Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                null, // subFiles
                null); // repositories

        assertEquals(0, results.values().stream().filter(Optional::isPresent).count());

        File root = new File(repository.getSourceRoot(), "git");
        assertTrue(root.isDirectory());
        final String repoName = "git";
        corruptGitRepo(root, repoName);

        results = Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                null, // subFiles
                null); // repositories

        assertEquals(1, results.values().stream().filter(Optional::isPresent).count());
        List<Repository> repos = results.entrySet().stream().filter(e -> e.getValue().isPresent()).
                map(Map.Entry::getKey).collect(Collectors.toList());
        assertEquals(1, repos.size());
        Repository repo = repos.get(0);
        assertTrue(repo.getDirectoryName().contains(repoName));
        Optional<Exception> repoResult = results.get(repo);
        assertTrue(repoResult.isPresent());
        Exception exception = repoResult.get();
        assertTrue(exception instanceof HistoryException);
    }
}
