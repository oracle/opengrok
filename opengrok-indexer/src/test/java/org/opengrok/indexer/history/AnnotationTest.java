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
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.history;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author austvik
 */
public class AnnotationTest {

    public AnnotationTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getRevision method, of class Annotation.
     */
    @Test
    public void getRevision() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getRevision(1), "");
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getRevision(1), "1.0");
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.getRevision(2), "1.1.0");
    }

    /**
     * Test of getAuthor method, of class Annotation.
     */
    @Test
    public void getAuthor() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getAuthor(1), "");
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getAuthor(1), "Author");
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.getAuthor(2), "Author 2");
    }

    /**
     * Test of isEnabled method, of class Annotation.
     */
    @Test
    public void isEnabled() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.isEnabled(1), false);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.isEnabled(1), true);
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.isEnabled(2), false);
    }

    /**
     * Test of size method, of class Annotation.
     */
    @Test
    public void size() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.size(), 0);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.size(), 1);
        instance.addLine("1.1", "Author 2", true);
        assertEquals(instance.size(), 2);
    }

    /**
     * Test of getWidestRevision method, of class Annotation.
     */
    @Test
    public void getWidestRevision() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getWidestRevision(), 0);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getWidestRevision(), 3);
        instance.addLine("1.1.0", "Author 2", true);
        assertEquals(instance.getWidestRevision(), 5);
    }

    /**
     * Test of getWidestAuthor method, of class Annotation.
     */
    @Test
    public void getWidestAuthor() {
        Annotation instance = new Annotation("testfile.tst");
        assertEquals(instance.getWidestAuthor(), 0);
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.getWidestAuthor(), 6);
        instance.addLine("1.1.0", "Author 2", false);
        assertEquals(instance.getWidestAuthor(), 8);
    }

    /**
     * Test of addLine method, of class Annotation.
     */
    @Test
    public void addLine() {
        Annotation instance = new Annotation("testfile.tst");
        instance.addLine("1.0", "Author", true);
        assertEquals(instance.size(), 1);
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
