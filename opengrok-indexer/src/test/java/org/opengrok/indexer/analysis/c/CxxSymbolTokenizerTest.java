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
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.c;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import org.junit.Test;
import java.io.InputStream;
import java.util.List;

/**
 * Tests the {@link CxxSymbolTokenizer} class.
 */
public class CxxSymbolTokenizerTest {

    /**
     * Test sample.cc v. samplesymbols.txt
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testCxxSymbolStream() throws Exception {
        InputStream ccres = getClass().getClassLoader().getResourceAsStream(
            "analysis/c/sample.cc");
        assertNotNull("despite sample.cc as resource,", ccres);
        InputStream symres = getClass().getClassLoader().getResourceAsStream(
            "analysis/c/samplesymbols_cc.txt");
        assertNotNull("despite samplesymbols_cc.txt as resource,", symres);

        List<String> expectedSymbols = readSampleSymbols(symres);
        assertSymbolStream(CxxSymbolTokenizer.class, ccres, expectedSymbols);
    }
}
