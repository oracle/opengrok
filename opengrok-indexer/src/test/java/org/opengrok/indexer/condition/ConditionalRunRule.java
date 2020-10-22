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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.condition;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This rule can be added to a Junit test and will look for the annotation
 * {@link ConditionalRun} on either the test class or method. The test is then
 * skipped through Junit's {@link Assume} capabilities if the
 * {@link RunCondition} provided in the annotation is not satisfied.
 *
 * Cobbled together from:
 * http://www.codeaffine.com/2013/11/18/a-junit-rule-to-conditionally-ignore-tests/
 * https://gist.github.com/yinzara/9980184
 * http://cwd.dhemery.com/2010/12/junit-rules/
 * http://stackoverflow.com/questions/28145735/androidjunit4-class-org-junit-assume-assumetrue-assumptionviolatedexception/
 * https://docs.oracle.com/javase/tutorial/java/annotations/repeating.html
 */
public class ConditionalRunRule implements TestRule {

    @Override
    public Statement apply(Statement aStatement, Description aDescription) {
        if (hasConditionalIgnoreAnnotationOnMethod(aDescription)) {
            RunCondition condition = getIgnoreConditionOnMethod(aDescription);
            if (!condition.isForcedOrSatisfied()) {
                return new IgnoreStatement(condition);
            }
        }

        if (hasConditionalIgnoreAnnotationOnClass(aDescription)) {
            RunCondition condition = getIgnoreConditionOnClass(aDescription);
            if (!condition.isForcedOrSatisfied()) {
                return new IgnoreStatement(condition);
            }
        }

        return aStatement;
    }

    private static boolean hasConditionalIgnoreAnnotationOnClass(Description aDescription) {
        return aDescription.getTestClass().getAnnotationsByType(ConditionalRun.class).length > 0;
    }

    private static RunCondition getIgnoreConditionOnClass(Description aDescription) {
        ConditionalRun[] annotations = aDescription.getTestClass().getAnnotationsByType(ConditionalRun.class);
        return new IgnoreConditionCreator(aDescription.getTestClass(), annotations).create();
    }

    private static boolean hasConditionalIgnoreAnnotationOnMethod(Description aDescription) {
        if (aDescription.getMethodName() == null) { // if @ClassRule is used
            return false;
        }
        try {
            // this is possible because test methods must not have any argument
            Method testMethod = aDescription.getTestClass().getMethod(aDescription.getMethodName());
            return testMethod.getAnnotationsByType(ConditionalRun.class).length > 0;
        } catch (NoSuchMethodException | SecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static RunCondition getIgnoreConditionOnMethod(Description aDescription) {
        try {
            // this is possible because test methods must not have any argument
            ConditionalRun[] annotations = aDescription.getTestClass().getMethod(aDescription.getMethodName())
                    .getAnnotationsByType(ConditionalRun.class);
            return new IgnoreConditionCreator(aDescription.getTestClass(), annotations).create();
        } catch (NoSuchMethodException | SecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Container for several conditions joined by an AND operator.
     */
    protected static class CompositeCondition implements RunCondition {

        List<RunCondition> conditions = new LinkedList<>();

        public boolean add(RunCondition e) {
            return conditions.add(e);
        }

        @Override
        public boolean isSatisfied() {
            for (RunCondition condition : conditions) {
                if (!condition.isSatisfied()) {
                    return false;
                }
            }
            return true;
        }
    }

    protected static class IgnoreConditionCreator {

        private final Class<?> mTestClass;
        private final List<Class<? extends RunCondition>> conditionTypes;

        public IgnoreConditionCreator(Class<?> aTestClass, ConditionalRun[] annotation) {
            this.mTestClass = aTestClass;
            this.conditionTypes = new ArrayList<>(annotation.length);
            for (int i = 0; i < annotation.length; i++) {
                this.conditionTypes.add(i, annotation[i].value());
            }
        }

        public RunCondition create() {
            checkConditionType();
            try {
                return createCondition();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private RunCondition createCondition() throws Exception {
            CompositeCondition result = null;
            /**
             * Run through the list of classes implementing RunCondition and
             * create a new class from it.
             */
            for (Class<? extends RunCondition> clazz : conditionTypes) {
                if (result == null) {
                    result = new CompositeCondition();
                }
                if (isConditionTypeStandalone(clazz)) {
                    result.add(clazz.getDeclaredConstructor().newInstance());
                } else {
                    result.add(clazz.getDeclaredConstructor(mTestClass).newInstance(mTestClass));
                }
            }
            return result;
        }

        private void checkConditionType() {
            for (Class<? extends RunCondition> clazz : conditionTypes) {
                if (!isConditionTypeStandalone(clazz) && !isConditionTypeDeclaredInTarget(clazz)) {
                    String msg
                            = "Conditional class '%s' is a member class "
                            + "but was not declared inside the test case using it.\n"
                            + "Either make this class a static class, "
                            + "standalone class (by declaring it in it's own file) "
                            + "or move it inside the test case using it";
                    throw new IllegalArgumentException(String.format(msg, clazz.getName()));
                }
            }
        }

        private boolean isConditionTypeStandalone(Class<? extends RunCondition> clazz) {
            return !clazz.isMemberClass()
                    || Modifier.isStatic(clazz.getModifiers());
        }

        private boolean isConditionTypeDeclaredInTarget(Class<? extends RunCondition> clazz) {
            return mTestClass.getClass().isAssignableFrom(clazz.getDeclaringClass());
        }
    }

    protected static class IgnoreStatement extends Statement {

        private final RunCondition condition;

        IgnoreStatement(RunCondition condition) {
            this.condition = condition;
        }

        @Override
        public void evaluate() {
            Assume.assumeTrue("Ignored by " + condition.getClass().getSimpleName(), false);
        }
    }
}
