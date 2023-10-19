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
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.terraform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

import java.io.InputStream;
import java.util.List;

/**
 * Represents a container for tests of {@link TerraformSymbolTokenizer}.
 */
class TerraformSymbolTokenizerTest {

    /**
     * Test sample.tf v. samplesymbols.txt
     */
    @Test
    void testTerraformSymbolStream() throws Exception {
        testSymbols("analysis/terraform/sample.tf", "analysis/terraform/samplesymbols.txt");
    }

    private void testSymbols(String codeResource, String symbolsResource) throws Exception {
        InputStream tfRes = getClass().getClassLoader().getResourceAsStream(codeResource);
        assertNotNull(tfRes, "Should get resource " + codeResource);
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(symbolsResource);
        assertNotNull(symRes, "Should get resource " + symbolsResource);

        List<String> expectedSymbols = readSampleSymbols(symRes);
        assertSymbolStream(TerraformSymbolTokenizer.class, tfRes, expectedSymbols);
    }
}
