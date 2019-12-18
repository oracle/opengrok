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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;

/**
 * Tests the {@link PLSQLSymbolTokenizer} class.
 */
public class PLSQLSymbolTokenizerTest {

    /**
     * Test sample.pls v. sampleplssymbols.txt
     */
    @Test
    public void testSqlSymbolStream() throws Exception {
        InputStream sqlRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/sql/sample.pls");
        assertNotNull("sample.pls should be an available resource", sqlRes);
        InputStream symRes = getClass().getClassLoader().getResourceAsStream(
                "analysis/sql/sampleplssymbols.txt");
        assertNotNull("sampleplssymbols.txt should be an available resource", symRes);

        List<String> expectedSymbols = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                symRes, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int hashOffset = line.indexOf('#');
                if (hashOffset != -1) {
                    line = line.substring(0, hashOffset);
                }
                expectedSymbols.add(line.trim());
            }
        }

        assertSymbolStream(PLSQLSymbolTokenizer.class, sqlRes, expectedSymbols);
    }
}
