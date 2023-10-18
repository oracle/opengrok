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
package org.opengrok.suggest.query.customized;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class CustomExactPhraseScorerTest {

    @Test
    void simpleTestAfter() throws IOException {
        CustomSloppyPhraseScorerTest.test(0, 2, new String[] {"one", "two"}, new Integer[] {3});
    }

    @Test
    void simpleTestBefore() throws IOException {
        CustomSloppyPhraseScorerTest.test(0, -1, new String[] {"one", "two"}, new Integer[] {0});
    }

}
