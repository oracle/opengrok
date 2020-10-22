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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;
import org.opengrok.indexer.analysis.AnalyzerFactory;

/**
 * Represents a container for tests of {@link DocumentMatcher} subclasses.
 */
public class DocumentMatcherTest {

    /**
     * Tests a mdoc(5)-style document.
     * @throws IOException I/O exception
     */
    @Test
    public void testMdocDocument() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/document/sync.1m");
        assertNotNull("despite inclusion locally,", res);

        byte[] buf = readSignature(res);

        AnalyzerFactory fac;

        // assert that it is troff-like
        fac = TroffAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull("though sync.1m is mdoc(5),", fac);
        assertSame("though sync.1m is troff-like mdoc(5)",
            TroffAnalyzerFactory.DEFAULT_INSTANCE, fac);

        // assert that it is not mandoc
        fac = MandocAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNull("though sync.1m is troff-like mdoc(5),", fac);
    }

    /**
     * Tests a mandoc(5)-style document.
     * @throws IOException I/O exception
     */
    @Test
    public void testMandocDocument() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/document/catman.1m");
        assertNotNull("despite inclusion locally,", res);

        byte[] buf = readSignature(res);

        AnalyzerFactory fac;

        // assert that it is mandoc-like
        fac = MandocAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull("though catman.1m is mandoc(5),", fac);
        assertSame("though catman.1m is mandoc(5)",
            MandocAnalyzerFactory.DEFAULT_INSTANCE, fac);

        // assert that it is also troff-like (though mandoc will win in the
        // AnalyzerGuru)
        fac = TroffAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull("though catman.1m is mandoc(5),", fac);
        assertSame("though catman.1m is mandoc(5)",
            TroffAnalyzerFactory.DEFAULT_INSTANCE, fac);
    }

    /**
     * Tests a fake UTF-16LE mandoc(5)-style document to affirm that encoding
     * determination is valid.
     * @throws IOException I/O exception
     */
    @Test
    public void testMandocBOMDocument() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
                "analysis/document/utf16le.1m");
        assertNotNull("despite inclusion locally,", res);

        byte[] buf = readSignature(res);

        AnalyzerFactory fac;

        // assert that it is mandoc-like
        fac = MandocAnalyzerFactory.MATCHER.isMagic(buf, res);
        assertNotNull("though utf16le.1m is mandoc(5),", fac);
        assertSame("though utf16le.1m is mandoc(5)",
            MandocAnalyzerFactory.DEFAULT_INSTANCE, fac);
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
