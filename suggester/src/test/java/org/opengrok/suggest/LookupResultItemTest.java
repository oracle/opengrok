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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

public class LookupResultItemTest {

    @Test
    public void combineTest() {
        LookupResultItem item1 = new LookupResultItem("p1", "proj1", 2);
        LookupResultItem item2 = new LookupResultItem("p1", "proj2", 4);

        item1.combine(item2);

        assertEquals("p1", item1.getPhrase());
        assertEquals(6, item1.getScore());
        assertThat(item1.getProjects(), containsInAnyOrder("proj1", "proj2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void combineNullTest() {
        LookupResultItem item = new LookupResultItem("p1", "proj1", 2);

        item.combine(null);
    }

}
