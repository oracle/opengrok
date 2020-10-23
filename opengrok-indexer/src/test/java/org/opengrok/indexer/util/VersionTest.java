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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Krystof Tulinger (tulinkry).
 */
package org.opengrok.indexer.util;

import org.junit.Assert;
import org.junit.Test;

public class VersionTest {

    @Test
    public void testFromString() {
        Assert.assertEquals(new Version(1, 2, 1), Version.from("1.2.   1    "));
        Assert.assertEquals(new Version(3, 2, 6), Version.from("   3.  2.6"));
        Assert.assertEquals(new Version(100, 200, 104), Version.from("100.   200.   104"));
        Assert.assertEquals(new Version(1), Version.from("   1    "));
    }

    @Test(expected = NumberFormatException.class)
    public void testFromEmptyString() {
        Version.from("");
    }


    @Test(expected = NumberFormatException.class)
    public void testFromInvalidString() {
        Version.from("1.2.abcd.2");
    }

    @Test
    public void testLowerComparsion() {
        Assert.assertTrue(new Version(1).compareTo(new Version(2)) < 0);
        Assert.assertTrue(new Version(1).compareTo(new Version(20)) < 0);
        Assert.assertTrue(new Version(1, 2).compareTo(new Version(1, 3)) < 0);
        Assert.assertTrue(new Version(1, 2).compareTo(new Version(2)) < 0);
        Assert.assertTrue(new Version(1, 2).compareTo(new Version(1, 100)) < 0);
        Assert.assertTrue(new Version(1, 2, 3).compareTo(new Version(1, 3)) < 0);
        Assert.assertTrue(new Version(1, 2, 3).compareTo(new Version(1, 2, 4)) < 0);
        Assert.assertTrue(new Version(2, 1, 2).compareTo(new Version(2, 11, 0)) < 0);
        Assert.assertTrue(new Version(2, 1, 2).compareTo(new Version(2, 20, 1)) < 0);
        Assert.assertTrue(new Version(1, 0, 0).compareTo(new Version(1, 0, 0, 0, 0, 1)) < 0);
    }

    @Test
    public void testGreaterComparsion() {
        Assert.assertTrue(new Version(2).compareTo(new Version(1)) > 0);
        Assert.assertTrue(new Version(20).compareTo(new Version(1)) > 0);
        Assert.assertTrue(new Version(1, 3).compareTo(new Version(1, 2)) > 0);
        Assert.assertTrue(new Version(2).compareTo(new Version(1, 2)) > 0);
        Assert.assertTrue(new Version(1, 100).compareTo(new Version(1, 2)) > 0);
        Assert.assertTrue(new Version(1, 3).compareTo(new Version(1, 2, 3)) > 0);
        Assert.assertTrue(new Version(1, 2, 4).compareTo(new Version(1, 2, 3)) > 0);
        Assert.assertTrue(new Version(2, 11, 0).compareTo(new Version(2, 1, 2)) > 0);
        Assert.assertTrue(new Version(2, 20, 1).compareTo(new Version(2, 1, 2)) > 0);
        Assert.assertTrue(new Version(1, 0, 0, 0, 0, 1).compareTo(new Version(1, 0, 0)) > 0);
    }

    @Test
    public void testEqualsComparsion() {
        Assert.assertTrue(new Version(1).compareTo(new Version(1)) == 0);
        Assert.assertTrue(new Version(1, 3).compareTo(new Version(1, 3)) == 0);
        Assert.assertTrue(new Version(1, 2).compareTo(new Version(1, 2)) == 0);
        Assert.assertTrue(new Version(1, 100).compareTo(new Version(1, 100)) == 0);
        Assert.assertTrue(new Version(1, 2, 3).compareTo(new Version(1, 2, 3)) == 0);
        Assert.assertTrue(new Version(1, 2, 4).compareTo(new Version(1, 2, 4)) == 0);
        Assert.assertTrue(new Version(1, 0, 0).compareTo(new Version(1, 0, 0)) == 0);
        Assert.assertTrue(new Version(1, 0, 0).compareTo(new Version(1, 0, 0, 0, 0)) == 0);
    }
}
