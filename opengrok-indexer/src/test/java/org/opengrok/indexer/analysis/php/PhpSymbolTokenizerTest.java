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
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.php;

import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.JFlexTokenizer;

/**
 * Tests the {@link PhpSymbolTokenizer} class.
 *
 * @author Gustavo Lopes
 */
public class PhpSymbolTokenizerTest {    

    private final AbstractAnalyzer analyzer;

    public PhpSymbolTokenizerTest() {
        PhpAnalyzerFactory analFact = new PhpAnalyzerFactory();
        this.analyzer = analFact.getAnalyzer();
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

        return l.toArray(new String[l.size()]);
    }

    @Test
    public void basicTest() {
        String s = "<?php foobar eval $eval 0sdf _ds˙d";
        String[] termsFor = getTermsFor(new StringReader(s));
        assertArrayEquals(
                new String[]{"foobar", "eval", "sdf", "_ds˙d"},
                termsFor);
    }

    @Test
    public void sampleTest() throws UnsupportedEncodingException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/php/sample.php");
        InputStreamReader r = new InputStreamReader(res, StandardCharsets.UTF_8);
        String[] termsFor = getTermsFor(r);
        assertArrayEquals(
                new String[]{
                    "a", //line 3
                    "foo", "bar", //line 5
                    "g", "a", "c", //line 6
                    "b", "c", "a", "a", //line 7
                    "doo", //line 9
                    "a", //line 10
                    "foo", "bar", //line 12
                    "name", //line 13
                    "foo", "bar", //line 14
                    "foo", //line 15
                    "ff", //line 20
                    "foo", //line 21
                    "FooException", //line 28
                    "used",
                    "Foo", "Bar", //line 30
                    "Foo", "Foo", "param", //line 31
                    "gata", //line 37
                    "gata", //line 38
                    "foo", "_SERVER", "_SERVER", "_SERVER", //line 39
                    "foo", "bar", "foo", "bar", "foo", "a", //line 40
                },
                termsFor);
    }
}
