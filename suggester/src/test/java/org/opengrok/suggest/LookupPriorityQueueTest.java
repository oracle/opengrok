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

import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LookupPriorityQueueTest {

    @Test
    public void testOverflow() {
        LookupPriorityQueue queue = new LookupPriorityQueue(2);

        queue.insertWithOverflow(new LookupResultItem("1", "test", 1));
        queue.insertWithOverflow(new LookupResultItem("2", "test", 2));
        queue.insertWithOverflow(new LookupResultItem("3", "test", 3));

        assertThat(queue.getResult().stream().map(LookupResultItem::getPhrase).collect(Collectors.toList()),
                contains("3", "2"));
    }

    @Test
    public void testCanInsert() {
        LookupPriorityQueue queue = new LookupPriorityQueue(2);

        queue.insertWithOverflow(new LookupResultItem("1", "test", 1));
        queue.insertWithOverflow(new LookupResultItem("2", "test", 2));

        assertFalse(queue.canInsert(0));
        assertTrue(queue.canInsert(3));
    }

}
