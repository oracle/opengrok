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
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.util;

import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static opengrok.auth.plugin.util.FilterUtil.doTransform;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterUtilTest {

    @Test
    void testTransforms() {
        assertEquals("FOO", doTransform("foo", "toUpperCase"));
        assertEquals("foo", doTransform("FOO", "toLowerCase"));
    }

    @Test
    void testTransformsUTF() throws UnsupportedEncodingException {
        assertEquals(new String("ČUČKAŘ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
                doTransform(new String("čučkař".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "toUpperCase"));
        assertEquals(new String("čučkař".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
                doTransform(new String("ČUČKAŘ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), "toLowerCase"));
    }

    @Test
    void testInvalidTransform() {
        assertThrows(UnsupportedOperationException.class, () -> doTransform("foo", "bar"));
    }

    @Test
    void testReplace() {
        Map<String, String> transforms = new TreeMap<>();
        transforms.put("uid", "toUpperCase");
        assertEquals("fooUSERbar",
                FilterUtil.replace("foo%uid%bar", "uid", "user", transforms));
    }

    @Test
    void testCheckTransforms() {
        Map<String, String> transforms = new TreeMap<>();
        transforms.put("uid", "xxx");
        assertThrows(UnsupportedOperationException.class, () -> FilterUtil.checkTransforms(transforms));
    }
}
