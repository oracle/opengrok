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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.search;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Do basic sanity testing of the Hit class.
 * @author Trond Norbye
 */
class HitTest {

    @Test
    void testFilename() {
        Hit instance = new Hit();
        assertNull(instance.getFilename());
        String expResult = "foobar";
        instance.setFilename(expResult);
        assertEquals(expResult, instance.getFilename());
    }

    @Test
    void testPath() {
        Hit instance = new Hit("/foo/bar", null, null, false, false);
        assertEquals("/foo/bar", instance.getPath());
        assertEquals(File.separator + "foo", instance.getDirectory());
    }

    @Test
    void testLine() {
        Hit instance = new Hit();
        assertNull(instance.getLine());
        String expResult = "This is a line of text";
        instance.setLine(expResult);
        assertEquals(expResult, instance.getLine());
    }

    @Test
    void testLineno() {
        Hit instance = new Hit();
        assertNull(instance.getLineno());
        String expResult = "12";
        instance.setLineno(expResult);
        assertEquals(expResult, instance.getLineno());
    }

    @Test
    void testCompareTo() {
        Hit o1 = new Hit("/foo", null, null, false, false);
        Hit o2 = new Hit("/foo", "hi", "there", false, false);
        assertEquals(o2.compareTo(o1), o1.compareTo(o2));
        o1.setFilename("bar");
        assertNotEquals(o2.compareTo(o1), o1.compareTo(o2));
    }

    @Test
    void testBinary() {
        Hit instance = new Hit();
        assertFalse(instance.isBinary());
        instance.setBinary(true);
        assertTrue(instance.isBinary());
    }

    @Test
    void testTag() {
        Hit instance = new Hit();
        assertNull(instance.getTag());
        String expResult = "foobar";
        instance.setTag(expResult);
        assertEquals(expResult, instance.getTag());
    }


    @Test
    void testAlt() {
        Hit instance = new Hit();
        assertFalse(instance.getAlt());
        Hit o2 = new Hit(null, null, null, false, true);
        assertTrue(o2.getAlt());
    }

    @Test
    void testEquals() {
        Hit o1 = new Hit("/foo", null, null, false, false);
        Hit o2 = new Hit("/foo", "hi", "there", false, false);
        assertEquals(o2.equals(o1), o1.equals(o2));
        o1.setFilename("bar");
        assertNotEquals(o2, o1);
        assertNotEquals(o1, o2);
        assertNotEquals(o1, new Object());
    }

    @Test
    void testHashCode() {
        String filename = "bar";
        Hit instance = new Hit(filename, null, null, false, false);
        assertEquals(filename.hashCode(), instance.hashCode());
    }
}
