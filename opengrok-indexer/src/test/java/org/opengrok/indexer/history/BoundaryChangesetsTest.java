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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoundaryChangesetsTest {

    private TestRepository repositories;
    private FileHistoryCache cache;

    @BeforeEach
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("repositories.zip"));

        cache = new FileHistoryCache();
        cache.initialize();
    }

    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;

        cache = null;
    }

    @Test
    void testBoundaryChangesetsBasic() {
        // TODO
    }

    /**
     * Indirect test of {@link BoundaryChangesets#getBoundaryChangesetIDs(String)}.
     * @throws Exception
     */
    @Test
    void testPerPartesHistory() throws Exception {
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(true);

        File reposRoot = new File(repositories.getSourceRoot(), "git");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        assertNotNull(repo);

        assertTrue(repo instanceof RepositoryWithPerPartesHistory);
        GitRepository repository = (GitRepository) repo;
        GitRepository gitRepository = Mockito.spy(repository);
        // TODO: parametrized ?
        Mockito.when(gitRepository.getPerPartesCount()).thenReturn(2);
        // TODO Mockito.when(gitRepository.getHistory(Any, Any, Any)).

        // TODO: test also with non-null sinceRevision
        gitRepository.createCache(cache, null);

        // TODO
    }
}
