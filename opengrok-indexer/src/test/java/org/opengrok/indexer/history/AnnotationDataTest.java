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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link AnnotationData}.
 */
class AnnotationDataTest {
    @Test
    void testEqualsFileName() {
        String filename = "foo.txt";
        AnnotationData annotationData = new AnnotationData(filename);
        assertNotNull(annotationData.getFilename());
        assertEquals(filename, annotationData.getFilename());
    }

    @Test
    void testEqualsSetFileName() {
        String filename = "foo.txt";
        AnnotationData annotationData = new AnnotationData();
        annotationData.setFilename(filename);
        assertNotNull(annotationData.getFilename());
        assertEquals(filename, annotationData.getFilename());
    }

    /**
     * This is useful for {@link FileAnnotationCacheTest#testSerialization()}.
     */
    @Test
    void testEqualsNegativeFileName() {
        AnnotationData annotationData1 = new AnnotationData("foo.txt");
        AnnotationData annotationData2 = new AnnotationData("bar.txt");
        assertNotEquals(annotationData1, annotationData2);
    }

    /**
     * This is useful for {@link FileAnnotationCacheTest#testSerialization()}.
     */
    @Test
    void testEqualsNegative() {
        AnnotationData annotationData1 = new AnnotationData();
        annotationData1.addLine("1.0", "Me", true);
        annotationData1.addLine("1.1", "Me", true);
        annotationData1.addLine("1.2", "Me", true);

        AnnotationData annotationData2 = new AnnotationData();
        annotationData2.addLine("1.0", "Me", true);
        annotationData2.addLine("1.1", "Me", true);

        assertNotEquals(annotationData1, annotationData2);
    }

    /**
     * This is useful for {@link FileAnnotationCacheTest#testSerialization()}.
     */
    @Test
    void testEqualsPositive() {
        AnnotationData annotationData1 = new AnnotationData();
        annotationData1.addLine("1.0", "Me", true);
        annotationData1.addLine("1.1", "Me", true);
        annotationData1.addLine("1.2", "Me", true);

        AnnotationData annotationData2 = new AnnotationData();
        annotationData2.addLine("1.0", "Me", true);
        annotationData2.addLine("1.1", "Me", true);
        annotationData1.addLine("1.2", "Me", true);

        assertNotEquals(annotationData1, annotationData2);
    }
}

