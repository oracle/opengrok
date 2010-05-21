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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@code Interner} class.
 */
public class InternerTest {

    @Test
    public void testInternString() {
        String s1_1 = new String("s1");
        String s1_2 = new String("s1");
        String s1_3 = new String("s1");
        assertNotSame(s1_1, s1_2);
        assertNotSame(s1_1, s1_3);
        assertNotSame(s1_2, s1_3);

        String s2_1 = new String("s2");
        String s2_2 = new String("s2");
        assertNotSame(s2_1, s2_2);

        Interner<String> interner = new Interner<String>();

        String s1_1_i = interner.intern(s1_1);
        assertEquals(s1_1, s1_1_i);
        String s1_2_i = interner.intern(s1_2);
        assertEquals(s1_2, s1_2_i);
        String s1_3_i = interner.intern(s1_3);
        assertEquals(s1_3, s1_3_i);

        String s2_1_i = interner.intern(s2_1);
        assertEquals(s2_1, s2_1_i);
        String s2_2_i = interner.intern(s2_2);
        assertEquals(s2_2, s2_2_i);

        assertSame(s1_1_i, s1_2_i);
        assertSame(s1_1_i, s1_3_i);
        assertSame(s1_2_i, s1_3_i);

        assertSame(s2_1_i, s2_2_i);

        assertNotSame(s1_1_i, s2_1_i);
        assertFalse(s1_1_i.equals(s2_1_i));
    }

    @Test
    public void testInternList() {
        String[] array1 = {"a", "b", "c"};
        String[] array2 = {"d", "e", "f"};

        ArrayList<String> l1_1 = new ArrayList<String>(Arrays.asList(array1));
        LinkedList<String> l1_2 = new LinkedList<String>(Arrays.asList(array1));
        ArrayList<String> l2_1 = new ArrayList<String>(Arrays.asList(array2));
        LinkedList<String> l2_2 = new LinkedList<String>(Arrays.asList(array2));

        assertNotSame(l1_1, l1_2);
        assertNotSame(l2_1, l2_2);

        assertEquals(l1_1, l1_2);
        assertEquals(l2_1, l2_2);

        assertFalse(l1_1.equals(l2_1));

        Interner<List<String>> interner = new Interner<List<String>>();

        List<String> l1_1_i = interner.intern(l1_1);
        List<String> l1_2_i = interner.intern(l1_2);
        List<String> l2_1_i = interner.intern(l2_1);
        List<String> l2_2_i = interner.intern(l2_2);

        assertEquals(l1_1, l1_1_i);
        assertEquals(l1_2, l1_2_i);
        assertEquals(l2_1, l2_1_i);
        assertEquals(l2_2, l2_2_i);

        assertSame(l1_1_i, l1_2_i);
        assertSame(l2_1_i, l2_2_i);

        assertFalse(l1_1_i.equals(l2_1_i));
    }

    @Test
    public void testInternNull() {
        assertNull(new Interner().intern(null));
    }

    @Test
    public void testInvariant() {
        Object[] s1 = {
            null, new String("1"), new String("2"), new String("3")
        };

        Object[] s2 = {
            null, new String("1"), new String("2"), new String("3")
        };

        assertEquals(s1.length, s2.length);

        Interner<Object> interner = new Interner<Object>();

        for (int i = 0; i < s1.length; i++) {
            Object o1 = s1[i];
            Object o2 = s2[i];

            // The javadoc for Interner.intern() mentions this invariant:
            assertTrue((o1 == null) ?
                (interner.intern(o1) == null) :
                o1.equals(o2) == (interner.intern(o1) == interner.intern(o2)));
        }
    }

}
