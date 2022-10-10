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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for {@link AnnotationLine}.
 */
class AnnotationLineTest {
    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsNegative1() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "barX", true);
        assertNotEquals(annotationLine1, annotationLine2);
    }

    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsNegative2() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0.1", "bar", true);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "bar", true);
        assertNotEquals(annotationLine1, annotationLine2);
    }

    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsNegative3() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "bar", false);
        assertNotEquals(annotationLine1, annotationLine2);
    }

    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsPositive() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "bar", true);
        assertEquals(annotationLine1, annotationLine2);
    }
}
