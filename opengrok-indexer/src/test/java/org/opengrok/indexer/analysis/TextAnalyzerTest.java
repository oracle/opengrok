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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.lucene.document.Document;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.plain.PlainAnalyzerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextAnalyzerTest {

    private String encoding;
    private String contents;

    private static StreamSource getStreamSource(final byte[] bytes) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Test
    void resetsStreamOnShortInput() throws IOException {
        new TestableTextAnalyzer().analyze(new Document(),
                getStreamSource("hi".getBytes()), null);

        assertEquals("hi", contents);
    }

    @Test
    void utf8WithBOM() throws IOException {
        byte[] buffer = new byte[]{(byte) 239, (byte) 187, (byte) 191, 'h', 'e', 'l', 'l', 'o'};
        new TestableTextAnalyzer().analyze(new Document(),
                getStreamSource(buffer), null);

        assertEquals("hello", contents);
        assertEquals("UTF8", encoding);
    }

    @Test
    void utf16WithBOM() throws IOException {
        final ByteBuffer utf16str = StandardCharsets.UTF_16.encode("hello");
        byte[] bytes = new byte[utf16str.remaining()];
        utf16str.get(bytes, 0, bytes.length);

        new TestableTextAnalyzer().analyze(new Document(),
                getStreamSource(bytes), null);

        assertEquals("UTF-16", encoding);

        assertEquals("hello", contents);
    }

    @Test
    void utf16WithBOMAlternate() throws IOException {
        final ByteBuffer utf16str = StandardCharsets.UTF_16.encode("hello");
        byte[] bytes = new byte[utf16str.remaining()];
        utf16str.get(bytes, 0, bytes.length);

        for (int i = 0; i < bytes.length; i += 2) {
            byte b = bytes[i];
            bytes[i] = bytes[i + 1];
            bytes[i + 1] = b;
        }

        new TestableTextAnalyzer().analyze(new Document(),
                getStreamSource(bytes), null);

        assertEquals("UTF-16", encoding);

        assertEquals("hello", contents);
    }

    class TestableTextAnalyzer extends TextAnalyzer {

        TestableTextAnalyzer() {
            // Using PlainAnalyzerFactory.DEFAULT_INSTANCE is OK for this test.
            super(PlainAnalyzerFactory.DEFAULT_INSTANCE);
        }

        @Override
        public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
            try (Reader r = getReader(src.getStream())) {
                // Gross and fragile but testing of encoding is needed.
                if (r instanceof ZeroReader) {
                    encoding = ((ZeroReader) r).getUnderlyingEncoding();
                } else {
                    encoding = ((InputStreamReader) r).getEncoding();
                }

                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = r.read()) != -1) {
                    sb.append((char) c);
                }

                contents = sb.toString();
            }
        }

        @Override
        protected JFlexXref newXref(Reader reader) {
            throw new UnsupportedOperationException("Not needed by test.");
        }
    }
}
