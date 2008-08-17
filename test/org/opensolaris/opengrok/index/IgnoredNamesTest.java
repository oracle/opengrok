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
package org.opensolaris.opengrok.index;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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

        List<String> names = instance.getIgnoredPatterns();
        assertNotNull(names);

        int total = names.size();

        for (String name : names) {
            assertTrue(instance.ignore(name));
        }
        
        names = new ArrayList<String>();
        names.add("*.o");
        
        instance.setIgnoredPatterns(names);
        names = instance.getIgnoredPatterns();
        assertEquals(1, names.size());
        
        assertTrue(instance.ignore("foo.o"));
        assertFalse(instance.ignore("foo"));
        
        instance.add("Makefile");
        names = instance.getIgnoredPatterns();
        assertEquals(2, names.size());
        assertTrue(instance.ignore(new File("Makefile")));
        assertFalse(instance.ignore("main.c"));

        instance.clear();
        names = instance.getIgnoredPatterns();
        assertEquals(0, names.size());
        instance.addDefaultPatterns();
        assertEquals(total, instance.getIgnoredPatterns().size());
    }
}