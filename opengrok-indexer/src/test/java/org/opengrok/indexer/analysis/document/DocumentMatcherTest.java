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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AnalyzerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Represents a container for tests of {@link DocumentMatcher} subclasses.
 */
class DocumentMatcherTest {

    /**
     * Tests a mdoc(5)-style document.
     * @throws IOException I/O exception
     */
    @Test
    void testMdocDocument() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/document/sync.1m");
        assertNotNull(res, "despite inclusion locally,");

        byte[] buf = readSignature(res);

        AnalyzerFactory fac;

        // assert that it is troff-like
        fac = TroffAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull(fac, "though sync.1m is mdoc(5),");
        assertSame(TroffAnalyzerFactory.DEFAULT_INSTANCE, fac, "though sync.1m is troff-like mdoc(5)");

        // assert that it is not mandoc
        fac = MandocAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNull(fac, "though sync.1m is troff-like mdoc(5),");
    }

    /**
     * Tests a mandoc(5)-style document.
     * @throws IOException I/O exception
     */
    @Test
    void testMandocDocument() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/document/catman.1m");
        assertNotNull(res, "despite inclusion locally,");

        byte[] buf = readSignature(res);

        AnalyzerFactory fac;

        // assert that it is mandoc-like
        fac = MandocAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull(fac, "though catman.1m is mandoc(5),");
        assertSame(MandocAnalyzerFactory.DEFAULT_INSTANCE, fac, "though catman.1m is mandoc(5)");

        // assert that it is also troff-like (though mandoc will win in the
        // AnalyzerGuru)
        fac = TroffAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull(fac, "though catman.1m is mandoc(5),");
        assertSame(TroffAnalyzerFactory.DEFAULT_INSTANCE, fac, "though catman.1m is mandoc(5)");
    }

    /**
     * Tests a fake UTF-16LE mandoc(5)-style document to affirm that encoding
     * determination is valid.
     * @throws IOException I/O exception
     */
    @Test
    void testMandocBOMDocument() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/document/utf16le.1m");
        assertNotNull(res, "despite inclusion locally,");

        byte[] buf = readSignature(res);

        AnalyzerFactory fac;

        // assert that it is mandoc-like
        fac = MandocAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull(fac, "though utf16le.1m is mandoc(5),");
        assertSame(MandocAnalyzerFactory.DEFAULT_INSTANCE, fac, "though utf16le.1m is mandoc(5)");
    }

    private static byte[] readSignature(InputStream in) throws IOException {
        byte[] buf = new byte[8];
        int numRead;
        in.mark(8);
        if ((numRead = in.read(buf)) < buf.length) {
            buf = Arrays.copyOf(buf, numRead);
        }
        in.reset();
        return buf;
    }
}
