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
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.junit.jupiter.api.Test;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetoptTest {

    @Test
    void testParseNormal() throws Exception {
        String[] argv = new String[] {"-a", "foo", "-bc", "--", "-f"};
        Getopt instance = new Getopt(argv, "a:bcr:f");

        instance.parse();

        assertEquals('a', (char) instance.getOpt());
        assertEquals("foo", instance.getOptarg());
        assertEquals('b', (char) instance.getOpt());
        assertNull(instance.getOptarg());
        assertEquals('c', (char) instance.getOpt());
        assertNull(instance.getOptarg());
        assertEquals(-1, instance.getOpt());
        assertEquals(4, instance.getOptind());
        assertTrue(instance.getOptind() < argv.length);
        assertEquals("-f", argv[instance.getOptind()]);
    }

    @Test
    void reset() throws ParseException {
        String[] argv = new String[] {"-a", "foo", "-bc", "argument1"};
        Getopt instance = new Getopt(argv, "a:bc");

        instance.parse();

        assertEquals('a', (char) instance.getOpt());
        assertEquals("foo", instance.getOptarg());
        assertEquals('b', (char) instance.getOpt());
        assertNull(instance.getOptarg());
        assertEquals('c', (char) instance.getOpt());
        assertNull(instance.getOptarg());
        assertEquals(-1, instance.getOpt());
        assertEquals(3, instance.getOptind());
        assertTrue(instance.getOptind() < argv.length);
        assertEquals("argument1", argv[instance.getOptind()]);

        instance.reset();

        assertEquals('a', (char) instance.getOpt());
        assertEquals("foo", instance.getOptarg());
        assertEquals('b', (char) instance.getOpt());
        assertNull(instance.getOptarg());
        assertEquals('c', (char) instance.getOpt());
        assertNull(instance.getOptarg());
        assertEquals(-1, instance.getOpt());
        assertEquals(3, instance.getOptind());
        assertTrue(instance.getOptind() < argv.length);
        assertEquals("argument1", argv[instance.getOptind()]);
    } /* Test of reset method, of class Getopt. */

    @Test
    void testParseFailure() throws Exception {
        String[] argv = new String[] {"-a"};
        Getopt instance = new Getopt(argv, "a:");

        assertThrows(ParseException.class, instance::parse);

        Getopt instance2 = new Getopt(argv, "b");
        assertThrows(ParseException.class, instance2::parse);
    }
}
