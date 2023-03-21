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
     * {@link AnnotationData} with different filenames should differ.
     */
    @Test
    void testEqualsNegativeFileName() {
        AnnotationData annotationData1 = new AnnotationData("foo.txt");
        AnnotationData annotationData2 = new AnnotationData("bar.txt");
        assertNotEquals(annotationData1, annotationData2);
    }

    /**
     * This is useful for {@link FileAnnotationCacheTest#testSerialization()}.
     * {@link AnnotationData} with different lists of {@link AnnotationLine} should differ.
     */
    @Test
    void testEqualsNegative() {
        final AnnotationLine annotationLine1 = new AnnotationLine("1.0", "Me", true);
        final AnnotationLine annotationLine2 = new AnnotationLine("1.1", "Me", true);
        final AnnotationLine annotationLine3 = new AnnotationLine("1.2", "Me", true);

        AnnotationData annotationData1 = new AnnotationData();
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);
        annotationData1.addLine(annotationLine3);

        AnnotationData annotationData2 = new AnnotationData();
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);

        assertNotEquals(annotationData1, annotationData2);
    }

    /**
     * This is useful for {@link FileAnnotationCacheTest#testSerialization()}.
     * {@link AnnotationData} with equal lists of {@link AnnotationLine} and different revisions should differ.
     */
    @Test
    void testEqualsNegativeStoredRevision() {
        final AnnotationLine annotationLine1 = new AnnotationLine("1.0", "Me", true);
        final AnnotationLine annotationLine2 = new AnnotationLine("1.1", "Me", true);

        AnnotationData annotationData1 = new AnnotationData();
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);
        annotationData1.setRevision("1.1");

        AnnotationData annotationData2 = new AnnotationData();
        annotationData2.addLine(annotationLine1);
        annotationData2.addLine(annotationLine2);

        assertNotEquals(annotationData1, annotationData2);
    }

    /**
     * This is useful for {@link FileAnnotationCacheTest#testSerialization()}.
     * The comparison of {@link AnnotationData} objects should compare lists of {@link AnnotationLine} objects
     * filenames and revisions.
     */
    @Test
    void testEqualsPositive() {
        final String fileName = "foo.txt";
        final AnnotationLine annotationLine1 = new AnnotationLine("1.0", "Me", true);
        final AnnotationLine annotationLine2 = new AnnotationLine("1.1", "Me", true);
        final AnnotationLine annotationLine3 = new AnnotationLine("1.2", "Me", true);

        AnnotationData annotationData1 = new AnnotationData(fileName);
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);
        annotationData1.addLine(annotationLine3);
        annotationData1.setRevision("1.2");

        AnnotationData annotationData2 = new AnnotationData(fileName);
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);
        annotationData1.addLine(annotationLine3);
        annotationData1.setRevision("1.2");

        assertNotEquals(annotationData1, annotationData2);
    }



    /**
     * Test retrieval of the revision from annotation data lines.
     */
    @Test
    void testRevisionFromLine() {
        final AnnotationLine annotationLine1 = new AnnotationLine("1.0.aaaaaaaaa", "Me", true, "1.0");
        final AnnotationLine annotationLine2 = new AnnotationLine("1.1.aaaaaaaaa", "Me", true, "1.1");
        final AnnotationLine annotationLine3 = new AnnotationLine("1.2.aaaaaaaaa", "Me", true, "1.2");

        AnnotationData annotationData1 = new AnnotationData();
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);
        annotationData1.addLine(annotationLine3);

        assertEquals("1.0.aaaaaaaaa", annotationData1.getRevision(1));
        assertEquals("1.2.aaaaaaaaa", annotationData1.getRevision(3));
        assertEquals("", annotationData1.getRevision(0), "Line not present, return empty string");
        assertEquals("", annotationData1.getRevision(4), "Line not present, return empty string");
    }


    /**
     * Test retrieval of the revision from annotation data lines.
     */
    @Test
    void testDisplayRevisionFromLine() {
        final AnnotationLine annotationLine1 = new AnnotationLine("1.0.aaaaaaaaa", "Me", true, "1.0");
        final AnnotationLine annotationLine2 = new AnnotationLine("1.1.aaaaaaaaa", "Me", true, "1.1");
        final AnnotationLine annotationLine3 = new AnnotationLine("1.2.aaaaaaaaa", "Me", true, "1.2");

        AnnotationData annotationData1 = new AnnotationData();
        annotationData1.addLine(annotationLine1);
        annotationData1.addLine(annotationLine2);
        annotationData1.addLine(annotationLine3);

        assertEquals("1.0", annotationData1.getRevisionForDisplay(1));
        assertEquals("1.2", annotationData1.getRevisionForDisplay(3));
        assertEquals("", annotationData1.getRevisionForDisplay(0), "Line not present, return empty string");
        assertEquals("", annotationData1.getRevisionForDisplay(4), "Line not present, return empty string");
    }
}

