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
package org.opengrok.indexer.web.api.constraints;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PositiveDurationValidatorTest {

    private PositiveDurationValidator validator = new PositiveDurationValidator();

    @Test
    public void testNull() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    public void testNegative() {
        assertFalse(validator.isValid(Duration.ofMinutes(-10), null));
    }

    @Test
    public void testZero() {
        assertFalse(validator.isValid(Duration.ofMinutes(0), null));
    }

    @Test
    public void testValid() {
        assertTrue(validator.isValid(Duration.ofMinutes(5), null));
    }

}
