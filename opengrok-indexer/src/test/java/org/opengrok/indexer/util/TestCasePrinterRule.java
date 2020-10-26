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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;

import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

// taken from https://technicaltesting.wordpress.com/2012/10/23/junit-rule-for-printing-test-case-start-and-end-information/
public class TestCasePrinterRule implements TestRule {

    private OutputStream out;
    private final TestCasePrinter printer = new TestCasePrinter();

    private String beforeContent = null;
    private String afterContent = null;
    private long timeStart;

    public TestCasePrinterRule() {
        this(System.out);
    }

    public TestCasePrinterRule(OutputStream os) {
        out = os;
    }

    private class TestCasePrinter extends ExternalResource {
        @Override
        protected void before() throws Throwable {
            timeStart = System.currentTimeMillis();
            out.write(beforeContent.getBytes());
        }

        @Override
        protected void after() {
            try {
                long timeEnd = System.currentTimeMillis();
                double seconds = (timeEnd - timeStart) / 1000.0;
                out.write((afterContent + "Time elapsed: " + new DecimalFormat("0.000").format(seconds) + " sec\n").
                        getBytes());
            } catch (IOException ioe) { /* ignore */
            }
        }
    }

    private static String getClassBasename(String inputName) {
        int idx;
        if (((idx = inputName.lastIndexOf('.')) > 0) && (idx < inputName.length())) {
            return inputName.substring(idx + 1);
        } else {
            return inputName;
        }
    }

    public final Statement apply(Statement statement, Description description) {
        final String testDescription = getClassBasename(description.getClassName()) + "#" +  description.getMethodName();
        beforeContent = String.format("\n[TEST START: %s]\n", testDescription);
        afterContent =  String.format("[TEST ENDED: %s] ", testDescription);
        return printer.apply(statement, description);
    }
}