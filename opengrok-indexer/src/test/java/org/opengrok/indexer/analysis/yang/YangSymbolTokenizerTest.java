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
 * Copyright (c) 2026, nishank.soni <soninishank8@gmail.com>.
 */
package org.opengrok.indexer.analysis.yang;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

/**
 * Tests for {@link YangSymbolTokenizer}.
 */
class YangSymbolTokenizerTest {

    @Test
    void testYangSymbolStream() throws Exception {
        String source = "analysis/yang/sample.yang";
        String symbols = "analysis/yang/samplesymbols.txt";
        InputStream sourceStream = getClass().getClassLoader().getResourceAsStream(source);
        assertNotNull(sourceStream, "Should get resource " + source);
        InputStream symbolStream = getClass().getClassLoader().getResourceAsStream(symbols);
        assertNotNull(symbolStream, "Should get resource " + symbols);

        List<String> expectedSymbols = readSampleSymbols(symbolStream);
        assertSymbolStream(YangSymbolTokenizer.class, sourceStream, expectedSymbols);
    }
}
