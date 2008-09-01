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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.search;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Do basic sanity testing of the Hit class
 *
 * @author Trond Norbye
 */
public class HitTest {

    @Test
    public void testFilename() {
        Hit instance = new Hit();
        assertNull(instance.getFilename());
        String expResult = "foobar";
        instance.setFilename(expResult);
        assertEquals(expResult, instance.getFilename());
    }

    @Test
    public void testPath() {
        Hit instance = new Hit("/foo/bar", null, null, false, false);
        assertEquals("/foo/bar", instance.getPath());
        assertEquals("/foo", instance.getDirectory());
    }

    @Test
    public void testLine() {
        Hit instance = new Hit();
        assertNull(instance.getLine());
        String expResult = "This is a line of text";
        instance.setLine(expResult);
        assertEquals(expResult, instance.getLine());
    }

    @Test
    public void testLineno() {
        Hit instance = new Hit();
        assertNull(instance.getLineno());
        String expResult = "12";
        instance.setLineno(expResult);
        assertEquals(expResult, instance.getLineno());
    }

    @Test
    public void testCompareTo() {
        Hit o1 = new Hit("/foo", null, null, false, false);
        Hit o2 = new Hit("/foo", "hi", "there", false, false);
        assertEquals(o2.compareTo(o1), o1.compareTo(o2));
        o1.setFilename("bar");
        assertFalse(o2.compareTo(o1) == o1.compareTo(o2));
    }

    @Test
    public void testBinary() {
        Hit instance = new Hit();
        assertFalse(instance.isBinary());
        instance.setBinary(true);
        assertTrue(instance.isBinary());
    }

    @Test
    public void testTag() {
        Hit instance = new Hit();
        assertNull(instance.getTag());
        String expResult = "foobar";
        instance.setTag(expResult);
        assertEquals(expResult, instance.getTag());
    }


    @Test
    public void testAlt() {
        Hit instance = new Hit();
        assertFalse(instance.getAlt());
        Hit o2 = new Hit(null, null, null, false, true);
        assertTrue(o2.getAlt());
    }

    @Test
    public void testEquals() {
        Hit o1 = new Hit("/foo", null, null, false, false);
        Hit o2 = new Hit("/foo", "hi", "there", false, false);
        assertEquals(o2.equals(o1), o1.equals(o2));
        o1.setFilename("bar");
        assertFalse(o2.equals(o1));
        assertFalse(o1.equals(o2));
    }

    @Test
    public void testHashCode() {
        String filename = "bar";
        Hit instance = new Hit(filename, null, null, false, false);
        assertEquals(filename.hashCode(), instance.hashCode());
    }
}