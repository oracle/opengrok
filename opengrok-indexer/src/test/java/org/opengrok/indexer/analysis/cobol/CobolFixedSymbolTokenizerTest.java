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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.cobol;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

/**
 * Tests {@link CobolFixedSymbolTokenizer} against the sample-fixed.cbl golden.
 */
class CobolFixedSymbolTokenizerTest {

    @Test
    void testCobolFixedSymbolStream() throws Exception {
        InputStream cblRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/cobol/sample-fixed.cbl");
        assertNotNull(cblRes, "sample-fixed.cbl as resource");
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/cobol/samplesymbols-fixed.txt");
        assertNotNull(symRes, "samplesymbols-fixed.txt as resource");

        List<String> expectedSymbols = readSampleSymbols(symRes);
        assertSymbolStream(CobolFixedSymbolTokenizer.class, cblRes, expectedSymbols);
    }
}
