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
package org.opengrok.indexer.web.api.constraints;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositiveDurationValidatorTest {

    private final PositiveDurationValidator validator = new PositiveDurationValidator();

    @Test
    void testNull() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void testNegative() {
        assertFalse(validator.isValid(Duration.ofMinutes(-10), null));
    }

    @Test
    void testZero() {
        assertFalse(validator.isValid(Duration.ofMinutes(0), null));
    }

    @Test
    void testValid() {
        assertTrue(validator.isValid(Duration.ofMinutes(5), null));
    }

}
