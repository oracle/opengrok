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
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.javascript;

import java.io.InputStream;
import java.io.Reader;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.OGKTextField;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.StreamUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.util.CustomAssertions.assertSymbolStream;
import static org.opengrok.indexer.util.StreamUtils.readSampleSymbols;

/**
 * Tests the {@link JavaScriptSymbolTokenizer} class.
 */
class JavaScriptSymbolTokenizerTest {

    /**
     * Test sample.js v. samplesymbols.txt
     *
     * @throws java.lang.Exception thrown on error
     */
    @Test
    void testJavaScriptSymbolStream() throws Exception {
        testSymbols("analysis/javascript/sample.js", "analysis/javascript/samplesymbols.txt");
    }

    @Test
    void testRegexpWithModifiersSymbols() throws Exception {
        testSymbols("analysis/javascript/regexp_modifiers.js", "analysis/javascript/regexp_modifiers_symbols.txt");
    }

    @Test
    void testRegexpSymbols() throws Exception {
        testSymbols("analysis/javascript/regexp_plain.js", "analysis/javascript/regexp_plain_symbols.txt");
    }

    private void testSymbols(String codeResource, String symbolsResource) throws Exception {
        InputStream jsres = getClass().getClassLoader().getResourceAsStream(
                codeResource);
        assertNotNull(jsres, String.format("Unable to find %s as a resource", codeResource));
        InputStream symres = getClass().getClassLoader().getResourceAsStream(
                symbolsResource);
        assertNotNull(symres, String.format("Unable to find %s as a resource", symbolsResource));

        List<String> expectedSymbols = readSampleSymbols(symres);
        assertSymbolStream(JavaScriptSymbolTokenizer.class, jsres, expectedSymbols);
    }

    @Test
    void largeJavaScriptFixtureTruncatesRefsButKeepsFullField() throws Exception {
        int tokenLimit = 1;
        LimitedJavaScriptAnalyzer analyzer = new LimitedJavaScriptAnalyzer(tokenLimit);
        Document doc = new Document();

        analyzer.analyze(doc, StreamUtils.sourceFromEmbedded("sources/javascript/testlong.js"), null);

        OGKTextField refsField = (OGKTextField) doc.getField(QueryBuilder.REFS);
        assertNotNull(refsField);
        assertEquals(tokenLimit, countTokens(refsField.tokenStreamValue()));

        OGKTextField fullField = (OGKTextField) doc.getField(QueryBuilder.FULL);
        assertNotNull(fullField);
        try (TokenStream fullStream = analyzer.tokenStream(
                QueryBuilder.FULL, (Reader) fullField.readerValue())) {
            CharTermAttribute term = fullStream.addAttribute(CharTermAttribute.class);
            fullStream.reset();
            assertTrue(fullStream.incrementToken());
            assertEquals("beforelongline", term.toString());
            assertTrue(countAtLeast(fullStream, tokenLimit + 1));
            fullStream.end();
        }
    }

    private static int countTokens(TokenStream tokenStream) throws Exception {
        int count = 0;
        try (TokenStream stream = tokenStream) {
            stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                count++;
            }
            stream.end();
        }
        return count;
    }

    private static boolean countAtLeast(TokenStream tokenStream, int minimumTokenCount) throws Exception {
        int count = 1;
        while (count < minimumTokenCount && tokenStream.incrementToken()) {
            count++;
        }
        return count >= minimumTokenCount;
    }

    private static final class LimitedJavaScriptAnalyzer extends JavaScriptAnalyzer {
        private final int refsTokenLimit;

        private LimitedJavaScriptAnalyzer(int refsTokenLimit) {
            super(new JavaScriptAnalyzerFactory());
            this.refsTokenLimit = refsTokenLimit;
        }

        @Override
        protected int getRefsTokenLimit() {
            return refsTokenLimit;
        }
    }
}
