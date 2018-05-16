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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MessageTest {

    /**
     * Test that a {@code Message} instance can be encoded and decoded without errors.
     */
    @Test
    public void testEncodeDecode() {
        Message m1 = new Message.Builder<>(NormalMessage.class).build();

        String encoded = m1.getEncoded();

        Message m2 = Message.decode(encoded);

        assertEquals(m1, m2);
    }

    public static boolean assertValid(Message m) {
        try {
            m.validate();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }
}
