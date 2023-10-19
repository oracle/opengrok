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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.javascript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import java.io.InputStream;
import java.util.List;

/**
 * Tests the {@link JavaScriptSymbolTokenizer} class.
 */
class JavaScriptSymbolTokenizerTest {

    /**
     * Test sample.js v. samplesymbols.txt
     *
     * @throws java.lang.Exception thrown on error
     */
    @Test
    void testJavaScriptSymbolStream() throws Exception {
        testSymbols("analysis/javascript/sample.js", "analysis/javascript/samplesymbols.txt");
    }

    @Test
    void testRegexpWithModifiersSymbols() throws Exception {
        testSymbols("analysis/javascript/regexp_modifiers.js", "analysis/javascript/regexp_modifiers_symbols.txt");
    }

    @Test
    void testRegexpSymbols() throws Exception {
        testSymbols("analysis/javascript/regexp_plain.js", "analysis/javascript/regexp_plain_symbols.txt");
    }

    private void testSymbols(String codeResource, String symbolsResource) throws Exception {
        InputStream jsres = getClass().getClassLoader().getResourceAsStream(
                codeResource);
        assertNotNull(jsres, String.format("Unable to find %s as a resource", codeResource));
        InputStream symres = getClass().getClassLoader().getResourceAsStream(
                symbolsResource);
        assertNotNull(symres, String.format("Unable to find %s as a resource", symbolsResource));

        List<String> expectedSymbols = readSampleSymbols(symres);
        assertSymbolStream(JavaScriptSymbolTokenizer.class, jsres, expectedSymbols);
    }
}
