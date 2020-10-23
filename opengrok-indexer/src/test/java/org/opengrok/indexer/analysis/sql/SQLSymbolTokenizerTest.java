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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.sql;

import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

/**
 * Tests the {@link SQLSymbolTokenizer} class.
 */
public class SQLSymbolTokenizerTest {

    /**
     * Test sample.sql v. samplesqlsymbols.txt
     */
    @Test
    public void testSqlSymbolStream() throws Exception {
        InputStream sqlRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/sql/sample.sql");
        assertNotNull("sample.sql should be an available resource", sqlRes);
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/sql/samplesqlsymbols.txt");
        assertNotNull("samplesqlsymbols.txt should be an available resource", symRes);

        List<String> expectedSymbols = readSampleSymbols(symRes);
        assertSymbolStream(SQLSymbolTokenizer.class, sqlRes, expectedSymbols);
    }
}
