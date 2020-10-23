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
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.hcl;

import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

/**
 * Represents a container for tests of {@link HCLSymbolTokenizer}.
 */
public class HCLSymbolTokenizerTest {

    /**
     * Test sample.hcl v. samplesymbols.txt
     */
    @Test
    public void testTerraformSymbolStream() throws Exception {
        testSymbols("analysis/hcl/sample.hcl", "analysis/hcl/samplesymbols.txt");
    }

    private void testSymbols(String codeResource, String symbolsResource) throws Exception {
        InputStream hclRes = getClass().getClassLoader().getResourceAsStream(codeResource);
        assertNotNull("Should get resource " + codeResource, hclRes);
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(symbolsResource);
        assertNotNull("Should get resource " + symbolsResource, symRes);

        List<String> expectedSymbols = readSampleSymbols(symRes);
        assertSymbolStream(HCLSymbolTokenizer.class, hclRes, expectedSymbols);
    }
}
