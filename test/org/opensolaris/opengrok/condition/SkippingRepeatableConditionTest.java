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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.condition;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@ConditionalRun(condition = RunningRepeatableConditionTest.TrueRunCondition.class)
@ConditionalRun(condition = RunningRepeatableConditionTest.FalseRunCondition.class)
@ConditionalRun(condition = RunningRepeatableConditionTest.TrueRunCondition.class)
public class SkippingRepeatableConditionTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @Test
    public void testSkippedTest() throws NoSuchMethodException {
        Assert.assertTrue("This test must be skipped", false);
    }
}
