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
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the functionality of {@link IndexDatabase#FILEPATH_COMPARATOR}.
 * In reality, the comparator will be used on paths returned from {@link org.opengrok.indexer.history.FileCollector}.
 * These will be a bit different to these used in the tests below. Specifically, they will start with
 * {@code File.separator} and will be relative to source root as per
 * {@link org.opengrok.indexer.configuration.RuntimeEnvironment#getPathRelativeToSourceRoot(File)}.
 * Here, the paths do not start with the separator because {@code Path.of(File.separator, "foo", "bar")}
 * does not work too well on Windows, however this should not impact the test functionality.
 */
public class IndexDatabasePathComparatorTest {

    private static final Comparator<Path> comparator = IndexDatabase.FILEPATH_COMPARATOR;

    @Test
    void testPathComparator() {
        final Path p1 = Path.of("bar");
        final Path p2 = Path.of("foo");
        final Path p3 = Path.of("foo", "bar", "Makefile");
        final Path p4 = Path.of("foo", "bar", "main.o");
        final Path p5 = Path.of("foo", "bar-module", "Makefile");
        Path[] paths = new Path[]{p3, p5, p1, p2, p4};
        List<Path> sorted = Arrays.stream(paths).sorted(comparator).collect(Collectors.toList());
        assertEquals(Arrays.asList(p1, p2, p3, p4, p5), sorted);
    }

    @Test
    void testPathComparatorEquals() {
        final Path p1 = Path.of("foo", "bar-module", "Makefile");
        final Path p2 = Path.of("foo", "bar-module", "Makefile");
        assertEquals(0, comparator.compare(p1, p2));
    }

    private static Stream<Arguments> provideArguments() {
        return Stream.of(
                Arguments.of("foo", "bar"),
                Arguments.of("foo", "foo"),
                Arguments.of("Makefile", "makefile"),
                Arguments.of("a", "z")
                );
    }

    /**
     * The {@link IndexDatabase#FILENAME_COMPARATOR} and {@link IndexDatabase#FILEPATH_COMPARATOR} need to be
     * consistent, because the initial reindex will be done using the former and incremental reindex might be
     * done with the latter. The term traversal inside
     * {@link IndexDatabase#processFileHistoryBased(IndexDownArgs, File, String)} depends on the consistency.
     */
    @ParameterizedTest
    @MethodSource("provideArguments")
    void testFilenameVsPathComparator(String fileName1, String fileName2) {
        final String prefix = Path.of("foo", "bar-module").toString();
        final Path p1 = Path.of(prefix, fileName1);
        final Path p2 = Path.of(prefix, fileName2);
        assertEquals(IndexDatabase.FILENAME_COMPARATOR.compare(p1.toFile(), p2.toFile()), comparator.compare(p1, p2));
    }
}
