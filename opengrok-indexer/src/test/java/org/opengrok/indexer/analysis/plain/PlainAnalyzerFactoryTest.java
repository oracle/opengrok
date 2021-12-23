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
 * Copyright (c) 2021, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Represents a container for tests of {@link PlainAnalyzerFactory}.
 */
class PlainAnalyzerFactoryTest {

    @Test
    void shouldMatchStrictASCII() throws IOException {
        byte[] fileBytes = "The contents of this file are subject to the terms of the".
                getBytes(StandardCharsets.US_ASCII);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertTrue(isMatch, "should match strict ASCII content");
    }

    @Test
    void shouldMatchShortStrictASCII() throws IOException {
        byte[] fileBytes = "The".getBytes(StandardCharsets.US_ASCII);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertTrue(isMatch, "should match strict ASCII short content");
    }

    @Test
    void shouldNotMatchASCIIWithNonWhitespaceControl() throws IOException {
        byte[] fileBytes = "The\u0001contents of this file are subject to the terms of the".
                getBytes(StandardCharsets.US_ASCII);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertFalse(isMatch, "should not match ASCII with non-whitespace control character");
    }

    @Test
    void shouldMatchNonASCIIUTF8WithoutBOM() throws IOException {
        byte[] fileBytes = "ゲーム盤の生成(h:縦，w:横，m:爆弾の数)".getBytes(StandardCharsets.UTF_8);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertTrue(isMatch, "should match non-ASCII UTF-8 without BOM");
    }

    @Test
    void shouldMatchNonASCIIUTF8WithBOM() throws IOException {
        byte[] fileBytes = enc(IOUtils.utf8Bom(), "ゲーム盤の生成(h:縦，w:横，m:爆弾の数)",
                StandardCharsets.UTF_8);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertTrue(isMatch, "should match non-ASCII UTF-8 with BOM");
    }

    @Test
    void shouldMatchUTF16BEWithBOM() throws IOException {
        byte[] fileBytes = enc(IOUtils.utf16BeBom(), "The contents of this file are subject to",
                StandardCharsets.UTF_16BE);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertTrue(isMatch, "should match UTF-16BE content with BOM");
    }

    @Test
    void shouldNotMatchUTF16BEWithoutBOM() throws IOException {
        byte[] fileBytes = "The contents of this file are subject to".getBytes(
                StandardCharsets.UTF_16BE);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertFalse(isMatch, "should not match UTF-16BE content without BOM");
    }

    @Test
    void shouldMatchUTF16LEWithBOM() throws IOException {
        byte[] fileBytes = enc(IOUtils.utf16LeBom(), "The contents of this file are subject to",
                StandardCharsets.UTF_16LE);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertTrue(isMatch, "should match UTF-16LE content");
    }

    @Test
    void shouldNotMatchUTF16LEWithoutBOM() throws IOException {
        byte[] fileBytes = "The contents of this file are subject to".getBytes(
                StandardCharsets.UTF_16LE);
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertFalse(isMatch, "should not match UTF-16LE content without BOM");
    }

    @Test
    void shouldNotMatchUtfEbcdic() throws IOException {
        /*
         * 4-byte UTF-EBCDIC BOM plus 2-byte UTF-EBCDIC 'H' 'i'. EBCDIC 'H' 'i'
         * on its own would be mis-identified as extended ASCII plain text.
         */
        byte[] fileBytes = new byte[]{(byte) 0xDD, (byte) 0x73, (byte) 0x66, (byte) 0x73,
                (byte) 0xC8, (byte) 0x89};
        boolean isMatch = checkIsPlainMatch(fileBytes);
        assertFalse(isMatch, "should not match UTF-EBCDIC content");
    }

    private static boolean checkIsPlainMatch(byte[] fileBytes) throws IOException {
        byte[] leadingContent = Arrays.copyOf(fileBytes, Math.max(8, fileBytes.length));
        ByteArrayInputStream bin = new ByteArrayInputStream(fileBytes);

        return (PlainAnalyzerFactory.MATCHER.isMagic(leadingContent, bin) != null);
    }

    private static byte[] enc(byte[] bom, String value, Charset charset) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(bom);
        bout.write(value.getBytes(charset));
        return bout.toByteArray();
    }
}
