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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author austvik
 */
public class AnnotationTest {

    /**
     * Test of getRevision method, of class Annotation.
     */
    @Test
    public void getRevision() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals("", instance.getRevision(1));
        instance.addLine("1.0", "Author", true);
        assertEquals("1.0", instance.getRevision(1));
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals("1.1.0", instance.getRevision(2));
    }

    /**
     * Test of getAuthor method, of class Annotation.
     */
    @Test
    public void getAuthor() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals("", instance.getAuthor(1));
        instance.addLine("1.0", "Author", true);
        assertEquals("Author", instance.getAuthor(1));
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals("Author 2", instance.getAuthor(2));
    }

    /**
     * Test of isEnabled method, of class Annotation.
     */
    @Test
    public void isEnabled() {
        Annotation instance = new Annotation("testfile.tst");
        assertFalse(instance.isEnabled(1));
        instance.addLine("1.0", "Author", true);
        assertTrue(instance.isEnabled(1));
        instance.addLine("1.1.0", "Author 2", false);
        assertFalse(instance.isEnabled(2));
    }

    /**
     * Test of size method, of class Annotation.
     */
    @Test
    public void size() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(0, instance.size());
        instance.addLine("1.0", "Author", true);
        assertEquals(1, instance.size());
        instance.addLine("1.1", "Author 2", true);
        assertEquals(2, instance.size());
    }

    /**
     * Test of getWidestRevision method, of class Annotation.
     */
    @Test
    public void getWidestRevision() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(0, instance.getWidestRevision());
        instance.addLine("1.0", "Author", true);
        assertEquals(3, instance.getWidestRevision());
        instance.addLine("1.1.0", "Author 2", true);
        assertEquals(5, instance.getWidestRevision());
    }

    /**
     * Test of getWidestAuthor method, of class Annotation.
     */
    @Test
    public void getWidestAuthor() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(0, instance.getWidestAuthor());
        instance.addLine("1.0", "Author", true);
        assertEquals(6, instance.getWidestAuthor());
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(8, instance.getWidestAuthor());
    }

    /**
     * Test of addLine method, of class Annotation.
     */
    @Test
    public void addLine() {
        Annotation instance = new Annotation("testfile.tst");
        instance.addLine("1.0", "Author", true);
        assertEquals(1, instance.size());
        instance.addLine(null, null, true);
    }

    /**
     * Test of getFilename method, of class Annotation.
     */
    @Test
    public void getFilename() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals("testfile.tst", instance.getFilename());
    }

    @Test
    public void testColorPalette() {
        final Annotation annotation = new Annotation("testfile.txt");
        annotation.addLine("1.0", "Me", true);
        annotation.addLine("1.1", "Me", true);
        annotation.addLine("1.2", "Me", true);
        Assert.assertEquals(3, annotation.getColors().size());
    }

    @Test
    public void testSortedColorPalette() {
        final Annotation annotation = new Annotation("testfile.txt");
        annotation.addLine("1.0", "Me", true);
        annotation.addLine("1.1", "Me", true);
        annotation.addLine("1.2", "Me", true);
        annotation.addFileVersion("1.0", 3);
        annotation.addFileVersion("1.2", 2);
        Assert.assertEquals(3, annotation.getColors().size());
        // tracked by history entries
        Assert.assertEquals("rgb(234, 255, 226)", annotation.getColors().get("1.0"));
        Assert.assertEquals("rgb(213, 220, 233)", annotation.getColors().get("1.2"));
        // 1.1 us untracked by history entries (no addFileVersion called)
        Assert.assertEquals("rgb(255, 191, 195)", annotation.getColors().get("1.1"));
    }
}
