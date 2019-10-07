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
 * Copyright (c) 2010, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.typescript;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link TypeScriptSymbolTokenizer} class.
 */
public class TypeScriptSymbolTokenizerTest {

    /**
     * Test sample.ts v. samplesymbols.txt
     */
    @Test
    public void testTypeScriptSymbolStream() throws Exception {
        testSymbols("analysis/typescript/sample.ts", "analysis/typescript/samplesymbols.txt");
    }

    private void testSymbols(String codeResource, String symbolsResource) throws Exception {
        InputStream tsRes = getClass().getClassLoader().getResourceAsStream(codeResource);
        assertNotNull(String.format("Unable to find %s as a resource", codeResource), tsRes);
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(symbolsResource);
        assertNotNull(String.format("Unable to find %s as a resource", symbolsResource), symRes);

        List<String> expectedSymbols = new ArrayList<>();
        try (BufferedReader wordsReader = new BufferedReader(
                new InputStreamReader(symRes, StandardCharsets.UTF_8))) {
            String line;
            while ((line = wordsReader.readLine()) != null) {
                int hashOffset = line.indexOf('#');
                if (hashOffset != -1) {
                    line = line.substring(0, hashOffset);
                }
                expectedSymbols.add(line.trim());
            }
        }

        assertSymbolStream(TypeScriptSymbolTokenizer.class, tsRes, expectedSymbols);
    }
}
