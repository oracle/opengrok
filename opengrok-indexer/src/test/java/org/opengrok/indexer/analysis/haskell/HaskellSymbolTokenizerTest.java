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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.haskell;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * Tests the {@link HaskellSymbolTokenizer} class.
 *
 * @author Harry Pan
 */
public class HaskellSymbolTokenizerTest {

    private final AbstractAnalyzer analyzer;

    public HaskellSymbolTokenizerTest() {
        this.analyzer = new HaskellAnalyzerFactory().getAnalyzer();
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
    public void sampleTest() throws IOException {
        try (InputStream res = getClass().getClassLoader().getResourceAsStream("analysis/haskell/sample.hs");
             InputStreamReader r = new InputStreamReader(res, StandardCharsets.UTF_8)) {
            String[] termsFor = getTermsFor(r);
            assertArrayEquals(
                    new String[] {
                            "qsort", // line 2
                            "qsort", "x", "xs", "qsort", "x'", "x'", "xs", "x'", "x", "x", "qsort", "x'", "x'", "xs", "x'", "x", //line 3
                            "x'y'", "f'", "g'h", "f'", "g'h" // line 6
                    },
                    termsFor);
        }
    }

    /**
     * Test sample2.hs v. sample2symbols.txt
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testHaskellSymbolStream() throws Exception {
        InputStream pyres = getClass().getClassLoader().getResourceAsStream(
            "analysis/haskell/sample2.hs");
        assertNotNull("despite sample.py as resource,", pyres);
        InputStream symres = getClass().getClassLoader().getResourceAsStream(
            "analysis/haskell/sample2symbols.txt");
        assertNotNull("despite samplesymbols.txt as resource,", symres);

        List<String> expectedSymbols = readSampleSymbols(symres);
        assertSymbolStream(HaskellSymbolTokenizer.class, pyres, expectedSymbols);
    }
}
