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
package org.opengrok.indexer.analysis.python;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.opengrok.indexer.util.StringUtils;

/**
 * Represents a test class for {@link PythonUtils}.
 */
public class PythonUtilsTest {

    @Test
    public void shouldMatchLongstringApostrophe() {
        final String value = "1-2-3'''";
        int i = StringUtils.patindexOf(value, PythonUtils.LONGSTRING_APOS);
        assertEquals("long-string apostrophe", 5, i);
    }

    @Test
    public void shouldMatchInitialLongstringApostrophe() {
        final String value = "'''";
        int i = StringUtils.patindexOf(value, PythonUtils.LONGSTRING_APOS);
        assertEquals("initial long-string apostrophe", 0, i);
    }

    @Test
    public void shouldMatchLongstringApostropheAfterEscapedApostrophe() {
        // Copy-and-paste the following so Netbeans does the escaping:
        // value: \'1-2-3\''''
        final String value = "\\'1-2-3\\''''";
        int i = StringUtils.patindexOf(value, PythonUtils.LONGSTRING_APOS);
        assertEquals("long-string apostrophe after quoted apostrophe", 9, i);
    }

    @Test
    public void shouldMatchLongstringApostropheAfterEvenEscapes() {
        // Copy-and-paste the following so Netbeans does the escaping:
        // value: \\'''
        final String value = "\\\\'''";
        int i = StringUtils.patindexOf(value, PythonUtils.LONGSTRING_APOS);
        assertEquals("long-string apostrophe after backslashes", 2, i);
    }

    @Test
    public void shouldNotMatchLongstringApostropheAfterOddEscapes() {
        // Copy-and-paste the following so Netbeans does the escaping:
        // value: \\\'''
        final String value = "\\\\\\'''";
        int i = StringUtils.patindexOf(value, PythonUtils.LONGSTRING_APOS);
        assertEquals("three apostrophes after backslashes", -1, i);
    }
}
