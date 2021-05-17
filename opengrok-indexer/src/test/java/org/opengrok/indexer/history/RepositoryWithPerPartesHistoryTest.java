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

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

public class RepositoryWithPerPartesHistoryTest {
    private TestRepository repositories;

    private GitRepository gitRepository;

    @BeforeEach
    public void setUp() throws Exception {
        repositories = new TestRepository();
        repositories.create(getClass().getResourceAsStream("repositories.zip"));

        File reposRoot = new File(repositories.getSourceRoot(), "git");
        Repository repo = RepositoryFactory.getRepository(reposRoot);
        assertNotNull(repo);

        assertTrue(repo instanceof RepositoryWithPerPartesHistory);
        gitRepository = (GitRepository) repo;
    }

    @AfterEach
    public void tearDown() {
        repositories.destroy();
        repositories = null;
    }

    /**
     * The way how history is split into chunks is tested in {@link BoundaryChangesetsTest#testBasic(ImmutableTriple)},
     * this tests how that translates into calls to
     * {@link RepositoryWithPerPartesHistory#getHistory(File, String, String)}, esp. w.r.t. the first and last boundary.
     * @throws HistoryException on error
     */
    @Test
    void testChangesets() throws HistoryException {
        // To avoid calling getHistory() for individual files via createCache() below.
        RuntimeEnvironment.getInstance().setHandleHistoryOfRenamedFiles(false);

        ArgumentCaptor<String> stringArgumentCaptor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stringArgumentCaptor2 = ArgumentCaptor.forClass(String.class);

        FileHistoryCache cache = new FileHistoryCache();
        GitRepository gitSpyRepository = Mockito.spy(gitRepository);
        Mockito.when(gitSpyRepository.getPerPartesCount()).thenReturn(3);
        gitSpyRepository.createCache(cache, null);
        Mockito.verify(gitSpyRepository, times(3)).
                getHistory(any(), stringArgumentCaptor1.capture(), stringArgumentCaptor2.capture());

        List<String> sinceRevisions = new ArrayList<>();
        sinceRevisions.add(null);
        sinceRevisions.add("8482156421620efbb44a7b6f0eb19d1f191163c7");
        sinceRevisions.add("b6413947a59f481ddc0a05e0d181731233557f6e");
        assertEquals(sinceRevisions, stringArgumentCaptor1.getAllValues());

        List<String> tillRevisions = new ArrayList<>();
        tillRevisions.add("8482156421620efbb44a7b6f0eb19d1f191163c7");
        tillRevisions.add("b6413947a59f481ddc0a05e0d181731233557f6e");
        tillRevisions.add(null);
        assertEquals(tillRevisions, stringArgumentCaptor2.getAllValues());
    }
}
