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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.util;

import java.text.ParseException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * JUnit test for org.opensolaris.opengrok.util.Getopt
 */
public class GetoptTest {

    public GetoptTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testParseNormal() throws Exception {
        String[] argv = new String[]{"-a", "foo", "-bc", "--", "-f" };
        Getopt instance = new Getopt(argv, "a:bcr:f");

        instance.parse();

        assertTrue(instance.getOpt() == 'a');
        assertEquals(instance.getOptarg(), "foo");
        assertTrue(instance.getOpt() == 'b');
        assertEquals(instance.getOptarg(), null);
        assertTrue(instance.getOpt() == 'c');
        assertEquals(instance.getOptarg(), null);
        assertTrue(instance.getOpt() == -1);
        assertTrue(instance.getOptind() == 4);
        assertTrue(instance.getOptind() < argv.length);
        assertEquals(argv[instance.getOptind()], "-f");
    }

    @Test
    public void reset() throws ParseException {
        String[] argv = new String[]{"-a", "foo", "-bc", "argument1" };
        Getopt instance = new Getopt(argv, "a:bc");

        instance.parse();

        assertTrue(instance.getOpt() == 'a');
        assertEquals(instance.getOptarg(), "foo");
        assertTrue(instance.getOpt() == 'b');
        assertEquals(instance.getOptarg(), null);
        assertTrue(instance.getOpt() == 'c');
        assertEquals(instance.getOptarg(), null);
        assertTrue(instance.getOpt() == -1);
        assertTrue(instance.getOptind() == 3);
        assertTrue(instance.getOptind() < argv.length);
        assertEquals(argv[instance.getOptind()], "argument1");

        instance.reset();

        assertTrue(instance.getOpt() == 'a');
        assertEquals(instance.getOptarg(), "foo");
        assertTrue(instance.getOpt() == 'b');
        assertEquals(instance.getOptarg(), null);
        assertTrue(instance.getOpt() == 'c');
        assertEquals(instance.getOptarg(), null);
        assertTrue(instance.getOpt() == -1);
        assertTrue(instance.getOptind() == 3);
        assertTrue(instance.getOptind() < argv.length);
        assertEquals(argv[instance.getOptind()], "argument1");
    } /* Test of reset method, of class Getopt. */

    @Test
    public void testParseFailure() throws Exception {
        String[] argv = new String[]{ "-a" };
        Getopt instance = new Getopt(argv, "a:");

        try {
            instance.parse();
            fail("Parse shall not allow missing arguments");
        } catch (ParseException exp) {
            if (!exp.getMessage().contains("requires an argument")) {
                // not the exception we expected
                throw exp;
            }
        }
        
        instance = new Getopt(argv, "b");
        try {
            instance.parse();
            fail("Parse shall not allow unknown arguments");
        } catch (ParseException exp) {
            if (!exp.getMessage().contains("Unknown argument: ")) {
                // not the exception we expected
                throw exp;
            }
        }
    }
}