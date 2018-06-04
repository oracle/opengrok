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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.messages.MessageTestUtils;
import org.opensolaris.opengrok.configuration.messages.Message;

import static org.junit.Assert.assertEquals;

public class MessageWriterTest {

    @Test
    public void testSingleMessage() throws IOException {
        Message msg = MessageTestUtils.getTestProjectMessage();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        MessageWriter writer = new MessageWriter(baos);

        writer.writeMessage(msg);

        assertEquals(msg.getEncoded() + Message.DELIMITER, baos.toString());
    }

    @Test
    public void testMultipleMessages() throws IOException {
        Message firstMsg = MessageTestUtils.getTestProjectMessage();
        Message secondMsg = MessageTestUtils.getTestConfigMessage();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        MessageWriter writer = new MessageWriter(baos);

        writer.writeMessage(firstMsg);
        writer.writeMessage(secondMsg);

        assertEquals(firstMsg.getEncoded() + Message.DELIMITER + secondMsg.getEncoded() + Message.DELIMITER,
                baos.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullParam() {
        new MessageWriter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullMessage() throws IOException {
        new MessageWriter(new ByteArrayOutputStream()).writeMessage(null);
    }

}
