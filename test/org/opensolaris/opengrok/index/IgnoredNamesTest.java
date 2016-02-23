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
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.c.CAnalyzerFactoryTest;
import org.opensolaris.opengrok.util.TestRepository;

/**
 *
 * @author Trond Norbye
 */
public class IgnoredNamesTest {

    private static TestRepository repository;

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(CAnalyzerFactoryTest.class.getResourceAsStream(
                "/org/opensolaris/opengrok/index/source.zip"));
    }

    @Test
    public void testIgnoredPatterns() {
        IgnoredNames instance = new IgnoredNames();

        List<String> names = instance.getItems();
        assertNotNull(names);

        /* self-test */
        for (String name : names) {
            assertTrue(instance.ignore(name));
        }

        /* Make sure common paths are not ignored by default. */
        assertFalse(instance.ignore("usr/src/foo/bin"));
        assertFalse(instance.ignore("usr/src/foo/bin/bar.ksh"));
        assertFalse(instance.ignore("usr/src/bar/obj"));
        assertFalse(instance.ignore("usr/src/bar/obj/foo.ksh"));
        assertFalse(instance.ignore("usr/src/foo/bar/usr.lib/main.c"));
        assertFalse(instance.ignore("usr/src/foo/bar/usr.lib"));

        /* Test handling of special directories. */
        assertTrue(instance.ignore("usr/src/.git"));
        assertFalse(instance.ignore("usr/src/.git/foo"));
        assertFalse(instance.ignore("usr/src/foo.git"));

        /* cumulative test */
        names = new ArrayList<>();
        names.add("*.o");

        instance.setItems(names);
        names = instance.getItems();
        assertEquals(1, names.size());

        assertTrue(instance.ignore("foo.o"));
        assertFalse(instance.ignore("foo"));
        assertTrue(instance.ignore(".o"));
        assertFalse(instance.ignore("foo.oo"));

        instance.add("f:Makefile");
        names = instance.getItems();
        assertEquals(2, names.size());
        assertTrue(instance.ignore(new File(repository.getSourceRoot()
                + "/c/Makefile")));

        assertFalse(instance.ignore("main.c"));

        instance.add("o*o?.a?c*");
        assertTrue(instance.ignore("opengrok.abc"));
        assertTrue(instance.ignore("opengrok.abcd"));
        assertFalse(instance.ignore("opengrok.ac"));
        assertFalse(instance.ignore("grok.abcd"));

        instance.add("d:haskell");
        assertTrue(instance.ignore(new File(repository.getSourceRoot()
                + "/haskell")));

        instance.clear();
        names = instance.getItems();
        assertEquals(0, names.size());
    }
}
