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

package org.opengrok.indexer.history;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * Represents a container for tests of {@link History}.
 */
public class HistoryTest {

    @Test
    public void testEncode() {
        final History hist = new History(
                new ArrayList<>(Arrays.asList(
                        new HistoryEntry(
                                "2",
                                new Date(1554648411000L),
                                "fred",
                                "b",
                                "second commit",
                                true),
                        new HistoryEntry(
                                "1",
                                new Date(1554648401000L),
                                "barney",
                                "a, b",
                                "first commit",
                                true))),
                new ArrayList<>(Arrays.asList("a/b", "b/c")));

        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        hist.encodeObject(bytesOut);
        String xml = new String(bytesOut.toByteArray());

        assertNotNull("String from encodeObject()", xml);
        assertTrue("has Date #2", xml.contains("<long>1554648411000</long>"));
        assertTrue("has Date #1", xml.contains("<long>1554648401000</long>"));
    }
}
