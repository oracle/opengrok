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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest;

import org.apache.lucene.util.PriorityQueue;

import java.util.Arrays;
import java.util.List;

class LookupPriorityQueue extends PriorityQueue<LookupResultItem> {

    LookupPriorityQueue(final int maxSize) {
        super(maxSize);
    }

    @Override
    protected boolean lessThan(final LookupResultItem item1, final LookupResultItem item2) {
        return item1.getScore() < item2.getScore();
    }

    List<LookupResultItem> getResult() {
        int size = this.size();
        LookupResultItem[] res = new LookupResultItem[size];

        for (int i = size - 1; i >= 0; i--) { // iterate from top so results are ordered in descending order
            res[i] = this.pop();
        }

        return Arrays.asList(res);
    }

}
