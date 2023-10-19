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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.JFlexTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests the {@link JavaSymbolTokenizer} class.
 * @author Lubos Kosco
 */
class JavaSymbolTokenizerTest {

    private final AbstractAnalyzer analyzer;

    JavaSymbolTokenizerTest() {
        this.analyzer = new JavaAnalyzerFactory().getAnalyzer();
    }

    private String[] getTermsFor(Reader r) {
        List<String> l = new LinkedList<>();
        JFlexTokenizer ts = (JFlexTokenizer) this.analyzer.tokenStream("refs", r);
        ts.setReader(r);
        CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
        try {
            ts.reset();
            while (ts.incrementToken()) {
                l.add(term.toString());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return l.toArray(new String[0]);
    }

    @Test
    void sampleTest() throws IOException {
        try (InputStream res = getClass().getClassLoader().getResourceAsStream("analysis/java/Sample.jav");
             InputStreamReader r = new InputStreamReader(res, StandardCharsets.UTF_8)) {
            String[] termsFor = getTermsFor(r);
            assertArrayEquals(
                    new String[] {
                            "org", "opensolaris", "opengrok", "analysis", "java", "Sample",
                            "String", "MY_MEMBER", "Sample", "Method", "arg", "res", "res",
                            "arg", "InnerClass", "i", "InnerClass", "i", "InnerMethod",
                            "length", "res", "AbstractMethod", "test", "InnerClass",
                            "String", "InnerMethod", "System", "out", "print", "main",
                            "String", "args", "num1", "num2", "num1", "num2", "num1",
                            "System", "out", "println", "ArithmeticException", "e",
                            "System", "out", "println", "System", "out", "println"
                    },
                    termsFor);
        }
    }
}
