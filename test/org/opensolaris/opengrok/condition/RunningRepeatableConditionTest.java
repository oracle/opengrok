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

/**
 *
 * @author Krystof Tulinger
 */
@ConditionalRun(condition = RunningRepeatableConditionTest.TrueRunCondition.class)
@ConditionalRun(condition = RunningRepeatableConditionTest.TrueRunCondition.class)
@ConditionalRun(condition = RunningRepeatableConditionTest.TrueRunCondition.class)
public class RunningRepeatableConditionTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @ConditionalRun(condition = TrueRunCondition.class)
    @ConditionalRun(condition = TrueRunCondition.class)
    @ConditionalRun(condition = TrueRunCondition.class)
    @ConditionalRun(condition = TrueRunCondition.class)
    @ConditionalRun(condition = TrueRunCondition.class)
    @Test
    public void testRunningTest() {
        Assert.assertTrue("This test shall run", true);
    }

    @ConditionalRun(condition = TrueRunCondition.class)
    @ConditionalRun(condition = FalseRunCondition.class)
    @ConditionalRun(condition = TrueRunCondition.class)
    @Test
    public void testSkippedTest() throws NoSuchMethodException {
        Assert.assertTrue("This test must be skipped", false);
    }

    protected static class TrueRunCondition implements RunCondition {

        @Override
        public boolean isSatisfied() {
            return true;
        }
    }

    protected static class FalseRunCondition implements RunCondition {

        @Override
        public boolean isSatisfied() {
            return false;
        }
    }

}
