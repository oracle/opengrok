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
 * Tests {@link CobolFreeSymbolTokenizer} against the sample-free.cbl golden.
 */
class CobolFreeSymbolTokenizerTest {

    @Test
    void testCobolFreeSymbolStream() throws Exception {
        InputStream cblRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/cobol/sample-free.cbl");
        assertNotNull(cblRes, "sample-free.cbl as resource");
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/cobol/samplesymbols-free.txt");
        assertNotNull(symRes, "samplesymbols-free.txt as resource");

        List<String> expectedSymbols = readSampleSymbols(symRes);
        assertSymbolStream(CobolFreeSymbolTokenizer.class, cblRes, expectedSymbols);
    }
}
