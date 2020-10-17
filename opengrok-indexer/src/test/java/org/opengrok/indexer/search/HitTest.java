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
 * Copyright (c) 2008, 2018 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.io.File;

/**
 * Do basic sanity testing of the Hit class
 *
 * @author Trond Norbye
 */
public class HitTest {

    @Test
    public void testFilename() {
        Hit instance = new Hit("a/b/foobar");
        String expResult = "foobar";
        assertEquals(expResult, instance.getFilename());
    }

    @Test
    public void testPath() {
        Hit instance = new Hit("/foo/bar", null, null, false, false);
        assertEquals("/foo/bar", instance.getPath());
        assertEquals(File.separator + "foo", instance.getDirectory());
    }

    @Test
    public void testLine() {
        Hit instance = new Hit("a/b/c");
        assertNull(instance.getLine());
        String expResult = "This is a line of text";
        instance.setLine(expResult);
        assertEquals(expResult, instance.getLine());
    }

    @Test
    public void testLineno() {
        Hit instance = new Hit("a/b");
        assertNull(instance.getLineno());
        String expResult = "12";
        instance.setLineno(expResult);
        assertEquals(expResult, instance.getLineno());
    }

    @Test
    public void testBinary() {
        Hit instance = new Hit("abc");
        assertFalse(instance.isBinary());
        instance.setBinary(true);
        assertTrue(instance.isBinary());
    }

    @Test
    public void testTag() {
        Hit instance = new Hit("def");
        assertNull(instance.getTag());
        String expResult = "foobar";
        instance.setTag(expResult);
        assertEquals(expResult, instance.getTag());
    }


    @Test
    public void testAlt() {
        Hit instance = new Hit("ghi/d");
        assertFalse(instance.getAlt());
        Hit o2 = new Hit(null, null, null, false, true);
        assertTrue(o2.getAlt());
    }
}
