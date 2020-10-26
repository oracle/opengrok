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
package org.opengrok.indexer.analysis.vb;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import org.junit.Test;
import java.io.InputStream;
import java.util.List;

/**
 * Tests the {@link VBSymbolTokenizer} class.
 */
public class VBSymbolTokenizerTest {

    /**
     * Test sample.cls v. samplesymbols.txt
     * @throws java.lang.Exception thrown on error
     */
    @Test
    public void testClojureSymbolStream() throws Exception {
        InputStream clsres = getClass().getClassLoader().getResourceAsStream(
            "analysis/vb/sample.cls");
        assertNotNull("despite sample.cls as resource,", clsres);
        InputStream symres = getClass().getClassLoader().getResourceAsStream(
            "analysis/vb/samplesymbols.txt");
        assertNotNull("despite samplesymbols.txt as resource,", symres);

        List<String> expectedSymbols = readSampleSymbols(symres);
        assertSymbolStream(VBSymbolTokenizer.class, clsres, expectedSymbols);
    }
}
