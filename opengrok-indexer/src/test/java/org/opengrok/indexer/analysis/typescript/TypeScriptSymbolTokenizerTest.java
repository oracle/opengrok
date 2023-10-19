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
package org.opengrok.indexer.analysis.typescript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import java.io.InputStream;
import java.util.List;

/**
 * Tests the {@link TypeScriptSymbolTokenizer} class.
 */
class TypeScriptSymbolTokenizerTest {

    /**
     * Test sample.ts v. samplesymbols.txt
     */
    @Test
    void testTypeScriptSymbolStream() throws Exception {
        testSymbols("analysis/typescript/sample.ts", "analysis/typescript/samplesymbols.txt");
    }

    private void testSymbols(String codeResource, String symbolsResource) throws Exception {
        InputStream tsRes = getClass().getClassLoader().getResourceAsStream(codeResource);
        assertNotNull(tsRes, String.format("Unable to find %s as a resource", codeResource));
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(symbolsResource);
        assertNotNull(symRes, String.format("Unable to find %s as a resource", symbolsResource));

        List<String> expectedSymbols = readSampleSymbols(symRes);
        assertSymbolStream(TypeScriptSymbolTokenizer.class, tsRes, expectedSymbols);
    }
}
