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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.index;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Trond Norbye
 */
public class IgnoredNamesTest {
    @Test
    public void testIgnoredPatterns() {
        IgnoredNames instance = new IgnoredNames();

        List<String> names = instance.getItems();
        assertNotNull(names);

        for (String name : names) {
            assertTrue(instance.ignore(name));
        }
        
        names = new ArrayList<String>();
        names.add("*.o");
        
        instance.setItems(names);
        names = instance.getItems();
        assertEquals(1, names.size());
        
        assertTrue(instance.ignore("foo.o"));
        assertFalse(instance.ignore("foo"));
        assertTrue(instance.ignore(".o"));
        assertFalse(instance.ignore("foo.oo"));
        
        instance.add("Makefile");
        names = instance.getItems();
        assertEquals(2, names.size());
        assertTrue(instance.ignore(new File("Makefile")));
        assertFalse(instance.ignore("main.c"));

        instance.add("o*o?.a?c*");
        assertTrue(instance.ignore("opengrok.abc"));
        assertTrue(instance.ignore("opengrok.abcd"));
        assertFalse(instance.ignore("opengrok.ac"));
        assertFalse(instance.ignore("grok.abcd"));

        instance.clear();
        names = instance.getItems();
        assertEquals(0, names.size());
    }
}
