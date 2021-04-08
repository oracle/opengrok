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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputFilter.FilterInfo;
import java.io.ObjectInputFilter.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhitelistObjectInputFilterTest {

    private static class DummyFilterInfo implements FilterInfo {

        private final Class<?> cl;

        DummyFilterInfo(@Nullable Class<?> cl) {
            this.cl = cl;
        }

        @Override
        public Class<?> serialClass() {
            return cl;
        }

        @Override
        public long arrayLength() {
            return 0;
        }

        @Override
        public long depth() {
            return 0;
        }

        @Override
        public long references() {
            return 0;
        }

        @Override
        public long streamBytes() {
            return 0;
        }
    }

    @Test
    void testBlackListedClassIsRejected() {
        var filter = new WhitelistObjectInputFilter();
        assertEquals(Status.REJECTED, filter.checkInput(new DummyFilterInfo(Double.class)));
    }

    @Test
    void testWhiteListedClassIsAllowed() {
        var filter = new WhitelistObjectInputFilter(Integer.class);
        assertEquals(Status.ALLOWED, filter.checkInput(new DummyFilterInfo(Integer.class)));
    }

    @Test
    void testNullClassIsAllowed() {
        var filter = new WhitelistObjectInputFilter();
        assertEquals(Status.ALLOWED, filter.checkInput(new DummyFilterInfo(null)));
    }
}
