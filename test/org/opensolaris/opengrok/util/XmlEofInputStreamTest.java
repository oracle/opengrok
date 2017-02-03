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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Krystof Tulinger
 */
public class XmlEofInputStreamTest {

    private final byte[][] tests = {
        {10, 20, 30, 40, 50, 0},
        {10, 20, 30, 40, 0, 50},
        {10, 20, 30, 0, 40, 50},
        {10, 20, 0, 30, 40, 50},
        {10, 0, 20, 30, 40, 50},
        {0, 10, 20, 30, 40, 50},
        {},
        {1, 2},
        {0x01, 0x02, 0x7F, 0x12},
        {0x01, 0x02, 0x0, 0x12, 1, 2, 3, 4},
        {1, 2, 0, 3, 4, 0, 5},
        {0},
        {1},
        {0, 0}
    };

    protected int countValidChars(byte[] array) {
        int j = 0;
        while (j < array.length && array[j] != 0) {
            j++;
        }
        return j;
    }

    /**
     * Test of read method, of class XmlEofInputStream.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testReadArray() throws IOException {
        for (byte[] test : tests) {
            ByteArrayInputStream input = new ByteArrayInputStream(test);
            InputStream stream = new XmlEofInputStream(input);
            byte[] buffer = new byte[test.length];
            int validChars = countValidChars(test);
            if (validChars == 0) {
                // eof from the underlying buffer
                Assert.assertEquals(-1, stream.read(buffer));
            } else {
                Assert.assertEquals(validChars, stream.read(buffer));
            }
            Assert.assertArrayEquals(Arrays.copyOfRange(test, 0, validChars), Arrays.copyOfRange(buffer, 0, validChars));
            if (validChars != test.length) {
                // there was an eof in the test data   
                Assert.assertEquals(-1, stream.read(buffer));
                Assert.assertEquals(-1, stream.read(buffer));
            }
        }
    }

    /**
     * Test of read method, of class XmlEofInputStream.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testReadArrayOffset() throws IOException {
        int[] offsets = {1, 2, 3, 4, 5, 1, 2, 3, 4, 2, 3, 0, 1, 20};

        Assert.assertEquals(tests.length, offsets.length);

        for (int i = 0; i < tests.length; i++) {
            ByteArrayInputStream input = new ByteArrayInputStream(tests[i]);
            InputStream stream = new XmlEofInputStream(input);
            byte[] buffer = new byte[tests[i].length];

            if (tests[i].length - offsets[i] <= 0) {
                try {
                    Assert.assertEquals(
                            tests[i].length - offsets[i] == 0 ? 0 : -1,
                            stream.read(buffer, offsets[i], buffer.length - offsets[i]));
                } catch (IndexOutOfBoundsException ex) {
                }
                continue;
            }

            byte[] data = Arrays.copyOfRange(tests[i], 0, tests[i].length - offsets[i]);
            int validChars = countValidChars(data);

            if (validChars == 0) {
                // eof from the underlying buffer
                Assert.assertEquals(-1,
                        stream.read(buffer, offsets[i], buffer.length - offsets[i]));
            } else {
                Assert.assertEquals(validChars,
                        stream.read(buffer, offsets[i], buffer.length - offsets[i]));
            }
            Assert.assertArrayEquals(
                    Arrays.copyOfRange(data, 0, validChars),
                    Arrays.copyOfRange(buffer, offsets[i], offsets[i] + validChars));

            if (validChars != data.length) {
                // there was an eof in the test data 
                Assert.assertEquals(-1, stream.read(buffer, offsets[i], buffer.length - offsets[i]));
                Assert.assertEquals(-1, stream.read(buffer, offsets[i], buffer.length - offsets[i]));
            }
        }
    }

    /**
     * Test of read method, of class XmlEofInputStream.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testReadOne() throws IOException {
        for (byte[] test : tests) {
            ByteArrayInputStream input = new ByteArrayInputStream(test);
            InputStream stream = new XmlEofInputStream(input);
            boolean eof = false;
            for (int j = 0; j < test.length; j++) {
                int read = stream.read();
                if (read == -1) {
                    eof = true;
                }
                Assert.assertEquals(eof ? -1 : test[j], read);
            }
        }
    }

    /**
     * Test of close method, of class XmlEofInputStream.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testClose() throws IOException {
        byte[] buf = {10, 30, 50, 10, 50};
        ByteArrayInputStream input = new ByteArrayInputStream(buf);
        InputStream stream = new XmlEofInputStream(input);
        Assert.assertEquals(10, stream.read());
        Assert.assertEquals(30, stream.read());
        stream.close();
        Assert.assertEquals(-1, stream.read());
        stream.close();
        Assert.assertEquals(-1, stream.read());
        Assert.assertEquals(-1, stream.read());
    }
}
