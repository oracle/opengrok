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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TandemFilenameTest {

    @Test
    public void shouldThrowIfFilenameIncludesPath() {
        IllegalArgumentException caughtException = null;
        try {
            TandemFilename.join("a/b/c", ".gz");
        } catch (IllegalArgumentException ex) {
            caughtException = ex;
        }
        assertNotNull("a/b/c is not a valid argument", caughtException);
    }

    @Test
    public void shouldNotNeedToHashShortNames() {
        String newName = TandemFilename.join("file1", ".gz");
        assertEquals("file1.gz", newName);
    }

    @Test
    public void shouldNotNeedToHash254ASCIIChars() {
        final String extension = ".gz";
        char[] chars = new char[254 - extension.length()];
        Arrays.fill(chars, 'A');
        String filename = new String(chars);
        String newName = TandemFilename.join(filename, extension);
        assertEquals("254 ASCII characters", filename + extension, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use 254 bytes", 254, newBytes.length);
    }

    @Test
    public void shouldHash255ASCIIChars() {
        final String extension = ".zip";
        char[] chars = new char[255 - extension.length()];
        Arrays.fill(chars, 'B');
        String filename = new String(chars);
        String newName = TandemFilename.join(filename, extension);

        final String expected = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "EOowLBTrxWMrXUnR2y818jr7LlP-DReUhteosu_8AoY=.zip";
        assertEquals("255 ASCII characters", expected, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use all 255 bytes", 255, newBytes.length);
    }

    @Test
    public void shouldHash255ASCIICharsWithShiftedExtension() {
        final String ext2 = ".gz";
        final String ext1 = ".cpp";
        char[] chars = new char[255 - ext1.length() - ext2.length()];
        Arrays.fill(chars, 'B');
        String filename = new String(chars) + ext1;
        String newName = TandemFilename.join(filename, ext2);

        final String expected = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "GfYhMFaQvXK2dbJeO9SXYHzWC2UFhNyDYXzWDP2a_5E=.cpp.gz";
        assertEquals("255 ASCII characters + 2 extensions", expected, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use all 255 bytes", 255, newBytes.length);
    }

    @Test
    public void shouldHash255ASCIICharsWithLongOriginalExtension() {
        final String extension = ".gz";
        char[] chars = new char[255 - extension.length()];
        Arrays.fill(chars, 'B');
        chars[32] = '.';
        String filename = new String(chars);
        String newName = TandemFilename.join(filename, extension);

        final String expected = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB.BBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "pVBqc9SOArO6qf3shKWndZ05harxS-tqnmQkev_mxmY=.gz";
        assertEquals("256 ASCII characters + 2 extensions", expected, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use all 255 bytes", 255, newBytes.length);
    }

    @Test
    public void shouldNotNeedToHashUnicodeOf254UTF8Bytes() {
        final String extension = ".gz";
        String filename = "Лоремипсумдолорситаметаппетерепатриояуеелояуентиа" +
                "меуяуиетомнисанималсцрипторемсеаутвидитсолутаусуЯуиеусусцип" +
                "итеррорибусприеро1";
        byte[] uFilename = filename.getBytes(StandardCharsets.UTF_8);
        assertEquals(filename + " as UTF-8, length", 251, uFilename.length);

        String newName = TandemFilename.join(filename, extension);
        assertEquals("Unicode concatenation", filename + extension, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use 254 bytes", 254, newBytes.length);
    }

    @Test
    public void shouldHashUnicodeOf255UTF8Bytes() {
        final String extension = ".zip";
        String filename = "Лоремипсумдолорситаметаппетерепатриояуеелояуентиа" +
                "меуяуиетомнисанималсцрипторемсеаутвидитсолутаусуЯуиеусусцип" +
                "итеррорибусприер.cs";
        byte[] uFilename = filename.getBytes(StandardCharsets.UTF_8);
        assertEquals(filename + " as UTF-8, length", 251, uFilename.length);

        String newName = TandemFilename.join(filename, extension);
        String expected = "Лоремипсумдолорситаметаппетерепатриояуеелояуентиа" +
                "меуяуиетомнисанималсцрипторемсеаутвидитсолутаусуЯуиеу" +
                "8Veko6G0h8wci2kX60EisTi4ReksNP1wdTLhbuB-5Vw=.cs.zip";
        assertEquals("Unicode + new extension", expected, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use all 255 bytes", 255, newBytes.length);
    }

    @Test
    public void shouldHashWithPaddingUnicodeOf256UTF8Bytes() {
        final String extension = ".zip";
        String filename = "Лоремипсумдолорситаметаппетерепатриояуеелояуентиа" +
                "меуяуиетомнисанималсцрипторемсеаутвидитсолутаусуЯуиеусусцип" +
                "итеррорибусприерер";
        byte[] uFilename = filename.getBytes(StandardCharsets.UTF_8);
        assertEquals(filename + " as UTF-8, length", 252, uFilename.length);

        String newName = TandemFilename.join(filename, extension);
        String expected = "Лоремипсумдолорситаметаппетерепатриояуеелояуентиа" +
                "меуяуиетомнисанималсцрипторемсеаутвидитсолутаусуЯуиеус" +
                "_exXOSa5o10Ll_Z1Ymf8pI1BDI9IH4io6weWi-PYPMj4=.zip";
        assertEquals("Unicode + padding + new extension", expected, newName);

        byte[] newBytes = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals("Should use all 255 bytes", 255, newBytes.length);
    }
}
