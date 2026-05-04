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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.cobol;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.StreamSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the format-detection rules in {@link CobolAnalyzer#sniffFormat}.
 */
class CobolAnalyzerSniffTest {

    @Test
    void sequenceAreaWithCodeAtCol8SniffsFixed() throws Exception {
        assertFixed(
                """
                        000100 IDENTIFICATION DIVISION.
                        000200 PROGRAM-ID. SAMPLE.
                        000300* a comment
                        000400 STOP RUN.
                        """);
    }

    @Test
    void letterAtCol1SniffsFree() throws Exception {
        assertFree(
                """
                        identification division.
                        program-id. sample.
                        stop run.
                        """);
    }

    @Test
    void inlineCommentMarkerSniffsFree() throws Exception {
        assertFree(
                """
                               *> free-format inline comment
                                identification division.
                        """);
    }

    @Test
    void lineLongerThanEightyCharsSniffsFree() throws Exception {
        // 90 chars total, leading spaces only
        // no other free signal.
        assertFree("        " + "x".repeat(90) + "\n");
    }

    @Test
    void sourceFreeDirectiveOverridesShape() throws Exception {
        assertFree(
                """
                              >>SOURCE FREE
                        000100 IDENTIFICATION DIVISION.
                        """);
    }

    @Test
    void sourceFixedDirectiveOverridesShape() throws Exception {
        // The first line has a letter at col 1 (would normally sniff free), but the
        // directive forces fixed.
        assertFixed(
                """
                              >>SOURCE FIXED
                        identification division.
                        """);
    }

    @Test
    void blankAndAmbiguousLinesDefaultToFixed() throws Exception {
        // Lines all start with digits
        // no strong free signal
        // defaults fixed.
        assertFixed(
                """
                        000100 IDENTIFICATION DIVISION.
                        000200 PROGRAM-ID. SAMPLE.
                        """);
    }

    @Test
    void emptyFileDefaultsToFixed() throws Exception {
        assertFixed("");
    }

    @Test
    void onlyBlankLinesDefaultToFixed() throws Exception {
        assertFixed("\n\n   \n\t\n\n");
    }

    @Test
    void lowercaseSourceFreeDirectiveSniffsFree() throws Exception {
        // Sniff uppercases each line before matching, so lowercase works too.
        assertFree(
                """
                              >>source free
                        000100 IDENTIFICATION DIVISION.
                        """);
    }

    @Test
    void lowercaseSourceFixedDirectiveSniffsFixed() throws Exception {
        assertFixed(
                """
                              >>source fixed
                        identification division.
                        """);
    }

    @Test
    void manyAmbiguousLinesDefaultToFixed() throws Exception {
        // Sniff caps at 50 non-blank lines. Confirm a 60-line file with no free
        // signal still resolves to fixed.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append(String.format("%06d FOO%n", i));
        }
        assertFixed(sb.toString());
    }

    @Test
    void freeSignalOnFirstLineWinsOverLaterDirective() throws Exception {
        // Sniff returns at the first strong signal; later >>SOURCE FIXED
        // never gets read. Documents short-circuit semantics.
        assertFree(
                """
                        identification division.
                              >>SOURCE FIXED
                        """);
    }

    private static void assertFixed(String content) throws Exception {
        assertTrue(sniff(content), "expected FIXED for content");
    }

    private static void assertFree(String content) throws Exception {
        assertFalse(sniff(content), "expected FREE for content");
    }

    private static boolean sniff(String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        StreamSource src = new StreamSource() {
            @Override
            public InputStream getStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
        CobolAnalyzer analyzer = (CobolAnalyzer) new CobolAnalyzerFactory().getAnalyzer();
        return analyzer.sniffFormat(src);
    }
}
