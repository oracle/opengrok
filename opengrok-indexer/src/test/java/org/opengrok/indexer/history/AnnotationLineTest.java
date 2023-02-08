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
 * Portions Copyright (c) 2023, Ric Harris <harrisric@users.noreply.github.com>.
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
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true, null);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "barX", true, null);
        assertNotEquals(annotationLine1, annotationLine2);
    }

    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsNegative2() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0.1", "bar", true, null);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "bar", true, null);
        assertNotEquals(annotationLine1, annotationLine2);
    }

    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsNegative3() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true, null);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "bar", false, null);
        assertNotEquals(annotationLine1, annotationLine2);
    }

    /**
     * This is useful for various equals tests in {@link AnnotationDataTest}.
     */
    @Test
    void testEqualsPositive() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true, null);
        AnnotationLine annotationLine2 = new AnnotationLine("1.0", "bar", true, null);
        assertEquals(annotationLine1, annotationLine2);
    }

    /**
     * Verify that the display revision getter falls back to the revision.
     */
    @Test
    void testDisplayRevisionFallBack() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true, null);
        assertEquals("1.0", annotationLine1.getDisplayRevision());
    }

    /**
     * Verify that the display revision getter overrides the base revision.
     */
    @Test
    void testDisplayRevisionOverrides() {
        AnnotationLine annotationLine1 = new AnnotationLine("1.0", "bar", true, "1.0.abcd");
        assertEquals("1.0.abcd", annotationLine1.getDisplayRevision());
    }
}
