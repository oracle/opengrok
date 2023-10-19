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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuggesterConfigTest {

    @Test
    void testRebuildParallelismNegative() {
        SuggesterConfig sconfig = new SuggesterConfig();
        assertThrows(IllegalArgumentException.class, () -> sconfig.setRebuildThreadPoolSizeInNcpuPercent(-1));
    }

    @Test
    void testRebuildParallelismPositive() {
        SuggesterConfig sconfig = new SuggesterConfig();
        sconfig.setRebuildThreadPoolSizeInNcpuPercent(70);
        assertEquals(70, sconfig.getRebuildThreadPoolSizeInNcpuPercent());
    }
}
