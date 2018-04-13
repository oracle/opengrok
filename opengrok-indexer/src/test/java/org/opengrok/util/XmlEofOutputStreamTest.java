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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Krystof Tulinger
 */
public class XmlEofOutputStreamTest {

    private byte[][] tests = {
        {10, 127, 0x10, 0x7F},
        {20, 32, 40, 60, 127, 100},
        {30, 0, 0, 0},
        {40, 1, 3, 4, 6, 7, 5, 46, 6, 4, 6, 7},
        {0x50},
        {},
        {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 5, 4, 5, 65, 6, 8, 7}};

    /**
     * Test of write method, of class XmlEofOutputStream.
     */
    @Test
    public void testWriteArray() {
        for (byte[] test : tests) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            OutputStream stream = new XmlEofOutputStream(output);
            try {
                stream.write(test);
                stream.close();
            } catch (IOException ex) {
                Assert.fail("The test should not throw an exception now");
            }
            byte[] expected = Arrays.copyOfRange(test, 0, test.length + 1);
            Assert.assertArrayEquals(expected, output.toByteArray());
        }
    }

    /**
     * Test of write method, of class XmlEofOutputStream.
     */
    @Test(expected = IndexOutOfBoundsException.class)
    public void testWriteArrayOffset() {
        int[] offsets = {1, 2, 3, 4, 5, 3, 5};
        Assert.assertEquals(tests.length, offsets.length);

        for (int i = 0; i < tests.length; i++) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            OutputStream stream = new XmlEofOutputStream(output);
            try {
                stream.write(tests[i], offsets[i], tests[i].length - offsets[i]);
                stream.close();
            } catch (IOException ex) {
                Assert.fail("The test should not throw an exception now");
            }
            /* IndexOutOfBoundsException was thrown if the offset was incorrect according to the length */

            byte[] expected = Arrays.copyOfRange(tests[i], offsets[i], tests[i].length + 1);

            Assert.assertArrayEquals(expected, output.toByteArray());
        }
    }

    /**
     * Test of write method, of class XmlEofOutputStream.
     */
    @Test
    public void testWriteInt() {
        for (byte[] test : tests) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            OutputStream stream = new XmlEofOutputStream(output);
            try {
                for (int j = 0; j < test.length; j++) {
                    stream.write(test[j]);
                }
                stream.close();
            } catch (IOException ex) {
                Assert.fail("The test should not throw an exception now");
            }
            byte[] expected = Arrays.copyOfRange(test, 0, test.length + 1);
            Assert.assertArrayEquals(expected, output.toByteArray());
        }
    }

    /**
     * Test of close method, of class XmlEofOutputStream.
     */
    @Test
    public void testClose() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStream stream = new XmlEofOutputStream(output);
        try {
            stream.write(10);
            stream.write(30);
            stream.close();
            stream.close();
        } catch (IOException ex) {
            Assert.fail("The test should not throw an exception now");
        }

        Assert.assertEquals(0, output.toByteArray()[output.toByteArray().length - 1]);

        try {
            stream.write(10);
            stream.write(50);
            // throws an exception
            Assert.fail("Writing after the stream has been closed is not allowed");
        } catch (IOException ex) {
        }
    }
}
