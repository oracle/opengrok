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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoundaryChangesetsTest {

    private TestRepository repositories;
    private FileHistoryCache cache;

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

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void testInvalidMaxCount(int maxCount) {
        GitRepository gitSpyRepository = Mockito.spy(gitRepository);
        Mockito.when(gitSpyRepository.getPerPartesCount()).thenReturn(maxCount);
        assertThrows(RuntimeException.class, () -> new BoundaryChangesets(gitSpyRepository));
    }

    private static Stream<ImmutablePair<Integer, List<String>>> provideMapsForTestPerPartesHistory() {
        // Cannot use List.of() because of the null element.
        List<String> expectedChangesets2 = new ArrayList<>();
        expectedChangesets2.add("8482156421620efbb44a7b6f0eb19d1f191163c7");
        expectedChangesets2.add("ce4c98ec1d22473d4aa799c046c2a90ae05832f1");
        expectedChangesets2.add("1086eaf5bca6d5a056097aa76017a8ab0eade20f");
        expectedChangesets2.add(null);

        List<String> expectedChangesets4 = new ArrayList<>();
        expectedChangesets4.add("ce4c98ec1d22473d4aa799c046c2a90ae05832f1");
        expectedChangesets4.add(null);

        return Stream.of(ImmutablePair.of(2, expectedChangesets2),
                ImmutablePair.of(4, expectedChangesets4));
    }

    /**
     * Indirect test of {@link BoundaryChangesets#getBoundaryChangesetIDs(String)}.
     * @throws Exception on error
     */
    @ParameterizedTest
    @MethodSource("provideMapsForTestPerPartesHistory")
    void testBasic(ImmutablePair<Integer, List<String>> expectedChangesetsPair) throws Exception {
        GitRepository gitSpyRepository = Mockito.spy(gitRepository);
        Mockito.when(gitSpyRepository.getPerPartesCount()).thenReturn(expectedChangesetsPair.getLeft());

        BoundaryChangesets boundaryChangesets = new BoundaryChangesets(gitSpyRepository);
        // TODO test also with non-null sinceRevision
        List<String> boundaryChangesetList = boundaryChangesets.
                getBoundaryChangesetIDs(null);
        assertEquals(expectedChangesetsPair.getRight().size(), boundaryChangesetList.size());
        assertEquals(expectedChangesetsPair.getRight(), boundaryChangesetList);
    }
}
