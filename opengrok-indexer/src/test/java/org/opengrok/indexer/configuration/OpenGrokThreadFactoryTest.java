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
 * Portions Copyright (c) 2024, Heewon Lee <heewon.lee@kaist.ac.kr>.
 */
package org.opengrok.indexer.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenGrokThreadFactoryTest {
    @Test
    void testNullRunnable() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            OpenGrokThreadFactory factory = new OpenGrokThreadFactory("");
            factory.newThread(null);
        });
    }
}