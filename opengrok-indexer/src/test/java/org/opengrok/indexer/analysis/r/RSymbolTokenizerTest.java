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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.r;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;

import org.junit.Test;
import org.opengrok.indexer.util.StreamUtils;

import java.io.InputStream;
import java.util.List;

/**
 * Represents a container for tests of {@link RSymbolTokenizer}.
 */
public class RSymbolTokenizerTest {

    /**
     * Test sample.r v. samplesymbols.txt
     */
    @Test
    public void testPerlSymbolStream() throws Exception {
        InputStream rlangRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/r/sample.r");
        assertNotNull("should read sample.r as resource,", rlangRes);
        InputStream symbolsRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/r/samplesymbols.txt");
        assertNotNull("should read samplesymbols.txt as resource,", symbolsRes);

        List<String> expectedSymbols = StreamUtils.readSampleSymbols(symbolsRes);
        assertSymbolStream(RSymbolTokenizer.class, rlangRes, expectedSymbols);
    }
}
