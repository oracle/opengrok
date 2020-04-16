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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * Represents a container for tests of {@link LimitedReader}.
 */
public class LimitedReaderTest {

    private static final String LIPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing " +
            "elit. Proin dignissim sollicitudin est vitae aliquam. Nam leo nisl, lobortis at " +
            "finibus nec, dignissim sed augue. Nullam commodo libero lectus, ac scelerisque ante " +
            "luctus ac. Praesent varius volutpat lacinia. Praesent nec vulputate eros.";

    @Test
    public void shouldReadToMax() throws IOException {
        String value = readToLimit(-1);
        assertEquals("should read to max", LIPSUM, value);
    }

    @Test
    public void shouldReadToTruncated() throws IOException {
        String value = readToLimit(10);
        assertEquals("should read to truncated", "Lorem ipsu", value);
    }

    @Test
    public void shouldReadNone() throws IOException {
        String value = readToLimit(0);
        assertEquals("should read nothing", "", value);
    }

    private static String readToLimit(int characterLimit) throws IOException {
        StringBuilder b = new StringBuilder();
        char[] buf = new char[37];
        try (LimitedReader reader = new LimitedReader(new StringReader(LIPSUM), characterLimit)) {
            int n;
            while ((n = reader.read(buf, 0, buf.length)) != -1) {
                b.append(buf, 0, n);
            }
        }
        return b.toString();
    }
}
