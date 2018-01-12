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
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.plain;

import java.io.InputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import org.opensolaris.opengrok.analysis.TokenizerMode;
import static org.opensolaris.opengrok.util.CustomAssertions.assertSymbolStream2;
import static org.opensolaris.opengrok.util.StreamUtils.readExpectedSymbols2;

/**
 * Tests the {@link PlainFullTokenizer} class.
 */
public class PlainFullTokenizerTest {

    /**
     * Test sampleplain.txt v. sampleplainsymbols.txt for
     * {@link TokenizerMode#SYMBOLS_ONLY}.
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testPlainFullStreamSymbolsOnly() throws Exception {
        testPlainFullStream(TokenizerMode.SYMBOLS_ONLY,
            "org/opensolaris/opengrok/analysis/plain/samplefullsymbols.txt");
    }

    /**
     * Test sampleplain.txt v. sampleplainsymbols2.txt for
     * {@link TokenizerMode#SYMBOLS_AND_NON_WHITESPACE}.
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testPlainFullStreamBoth() throws Exception {
        testPlainFullStream(TokenizerMode.SYMBOLS_AND_NON_WHITESPACE,
            "org/opensolaris/opengrok/analysis/plain/samplefullsymbols2.txt");
    }

    /**
     * Test sampleplain.txt v. sampleplainsymbols3.txt for
     * {@link TokenizerMode#NON_WHITESPACE_ONLY}.
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testPlainFullStreamNonwhitespaceOnly() throws Exception {
        testPlainFullStream(TokenizerMode.NON_WHITESPACE_ONLY,
            "org/opensolaris/opengrok/analysis/plain/samplefullsymbols3.txt");
    }

    /**
     * Test sample.c v. samplesymbols_c2.txt for
     * {@link TokenizerMode#SYMBOLS_AND_NON_WHITESPACE}.
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testCPlainFullStreamBoth() throws Exception {
        testPlainFullStream(TokenizerMode.SYMBOLS_AND_NON_WHITESPACE,
            "org/opensolaris/opengrok/analysis/c/sample.c",
            "org/opensolaris/opengrok/analysis/c/samplesymbols_c2.txt");
    }

    private void testPlainFullStream(TokenizerMode mode, String symbolsFile)
            throws Exception {
        testPlainFullStream(mode,
            "org/opensolaris/opengrok/analysis/plain/sampleplain.txt",
            symbolsFile);
    }

    private void testPlainFullStream(TokenizerMode mode, String srcFile,
            String symbolsFile) throws Exception {
        InputStream txtres = getClass().getClassLoader().getResourceAsStream(
            srcFile);
        assertNotNull(srcFile + " as resource,", txtres);

        InputStream symres = getClass().getClassLoader().getResourceAsStream(
            symbolsFile);
        assertNotNull(symbolsFile + " as resource,", symres);
        List<SimpleEntry<String, Integer>> expectedsyms = new ArrayList<>();
        readExpectedSymbols2(expectedsyms, symres);

        assertSymbolStream2(PlainFullTokenizer.class, txtres, expectedsyms,
            true, mode);
    }
}
