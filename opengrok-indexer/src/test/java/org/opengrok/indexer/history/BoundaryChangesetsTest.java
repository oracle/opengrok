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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.opengrok.indexer.util.TestRepository;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoundaryChangesetsTest {

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

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void testInvalidMaxCount(int maxCount) {
        GitRepository gitSpyRepository = Mockito.spy(gitRepository);
        Mockito.when(gitSpyRepository.getPerPartesCount()).thenReturn(maxCount);
        assertThrows(RuntimeException.class, () -> new BoundaryChangesets(gitSpyRepository));
    }

    /**
     * Used to supply test data for testing {@link BoundaryChangesets#getBoundaryChangesetIDs(String)}.
     * @return triplets of (maximum count, start revision, list of expected revisions)
     *
     * The test expects this sequence of changesets in the repository:
     *
     * <pre>
     * {@code 84599b3cccb3eeb5aa9aec64771678d6526bcecb (HEAD -> master) renaming directories
     * 67dfbe2648c94a8825671b0f2c132828d0d43079 renaming renamed -> renamed2
     * 1086eaf5bca6d5a056097aa76017a8ab0eade20f adding some lines into renamed.c
     * b6413947a59f481ddc0a05e0d181731233557f6e moved renamed.c to new location
     * ce4c98ec1d22473d4aa799c046c2a90ae05832f1 adding simple file for renamed file testing
     * aa35c25882b9a60a97758e0ceb276a3f8cb4ae3a Add lint make target and fix lint warnings
     * 8482156421620efbb44a7b6f0eb19d1f191163c7 Add the result of a make on Solaris x86
     * bb74b7e849170c31dc1b1b5801c83bf0094a3b10 Added a small test program}
     * </pre>
     */
    private static Stream<ImmutableTriple<Integer, String, List<String>>> provideMapsForTestPerPartesHistory() {
        // Cannot use List.of() because of the null element.
        List<String> expectedChangesets2 = Arrays.asList("8482156421620efbb44a7b6f0eb19d1f191163c7",
                "ce4c98ec1d22473d4aa799c046c2a90ae05832f1",
                "1086eaf5bca6d5a056097aa76017a8ab0eade20f", null);

        List<String> expectedChangesets4 = Arrays.asList("ce4c98ec1d22473d4aa799c046c2a90ae05832f1", null);

        List<String> expectedChangesets2Middle = Arrays.asList("ce4c98ec1d22473d4aa799c046c2a90ae05832f1",
                "1086eaf5bca6d5a056097aa76017a8ab0eade20f", null);

        return Stream.of(ImmutableTriple.of(2, null, expectedChangesets2),
                ImmutableTriple.of(4, null, expectedChangesets4),
                ImmutableTriple.of(2, "aa35c25882b9a60a97758e0ceb276a3f8cb4ae3a",
                        expectedChangesets2Middle));
    }

    /**
     * Test of {@link BoundaryChangesets#getBoundaryChangesetIDs(String)}.
     * @throws Exception on error
     */
    @ParameterizedTest
    @MethodSource("provideMapsForTestPerPartesHistory")
    void testBasic(ImmutableTriple<Integer, String, List<String>> integerListImmutableTriple) throws Exception {
        GitRepository gitSpyRepository = Mockito.spy(gitRepository);
        Mockito.when(gitSpyRepository.getPerPartesCount()).thenReturn(integerListImmutableTriple.getLeft());

        BoundaryChangesets boundaryChangesets = new BoundaryChangesets(gitSpyRepository);
        List<String> boundaryChangesetList = boundaryChangesets.
                getBoundaryChangesetIDs(integerListImmutableTriple.getMiddle());
        assertEquals(integerListImmutableTriple.getRight().size(), boundaryChangesetList.size());
        assertEquals(integerListImmutableTriple.getRight(), boundaryChangesetList);
    }
}
