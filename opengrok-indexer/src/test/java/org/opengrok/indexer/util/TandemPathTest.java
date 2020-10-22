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

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TandemPathTest {

    @Test
    public void shouldNotNeedToHashShortNames() {
        File original = new File("dir", "file1");
        File expected = new File("dir", "file1.gz");
        String newName = TandemPath.join(original.toString(), ".gz");
        assertEquals(expected.toString(), newName);
    }

    @Test
    public void shouldHash255ASCIIChars() {
        final String extension = ".zip";
        char[] chars = new char[255 - extension.length()];
        Arrays.fill(chars, 'B');
        String filename = new String(chars);
        File original = new File("dir", filename);
        String newName = TandemPath.join(original.toString(), extension);

        final String aNewName = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "EOowLBTrxWMrXUnR2y818jr7LlP-DReUhteosu_8AoY=.zip";
        File expected = new File("dir", aNewName);
        assertEquals("255 ASCII characters", expected.toString(), newName);
    }

    @Test
    public void shouldNotNeedToHash254ASCIIChars() {
        final String extension = ".zip";
        char[] chars = new char[254 - extension.length()];
        Arrays.fill(chars, 'B');
        String filename = new String(chars);
        File original = new File("dir", filename);
        String newName = TandemPath.join(original.toString(), extension);

        final String aNewName = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" +
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB.zip";
        File expected = new File("dir", aNewName);
        assertEquals("254 ASCII characters", expected.toString(), newName);
    }
}
