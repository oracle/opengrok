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
 * Copyright (c) 2023, Oracle and/or its affiliates.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Represents a container for tests of {@link ErrorMessageCollector}.
 */
class ErrorMessageCollectorTest {
    @Test
    void noEmptyStringEmptyCollectionReturnOptionalEmpty() {
        var collector = new ErrorMessageCollector("TestPrefix");
        var returnValue = Stream.<String>empty().collect(collector);
        Assertions.assertTrue(returnValue.isEmpty());
    }
    @Test
    void emptyStringWithEmptyCollectionReturnOptionalEmpty() {
        var collector = new ErrorMessageCollector("TestPrefix", "TestEmptyString");
        var returnValue = Stream.<String>empty().collect(collector);
        Assertions.assertEquals("TestEmptyString", returnValue.orElse(""));
    }
    @Test
    void noEmptyStringWithMultiElementCollectionReturnJoinedString() {
        var collector = new ErrorMessageCollector("TestPrefix ");
        var returnValue = Set.of("a", "b").stream().collect(collector);
        Assertions.assertTrue(returnValue.orElse("").startsWith("TestPrefix "));
    }
    @Test
    void emptyStringWithMultiElementCollectionReturnJoinedStringWithoutEmptyString() {
        var collector = new ErrorMessageCollector("TestPrefix ", "TestEmptyString");
        var returnValue = Set.of("a", "b").stream().collect(collector);
        Assertions.assertTrue(returnValue.orElse("").startsWith("TestPrefix "));
    }

}