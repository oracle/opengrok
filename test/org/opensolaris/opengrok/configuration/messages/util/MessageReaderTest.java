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

import java.io.ByteArrayInputStream;

import com.google.gson.JsonSyntaxException;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.messages.Message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.getTestConfigMessage;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.getTestProjectMessage;

public class MessageReaderTest {

    @Test
    public void oneMessageTest() {
        Message message = getTestProjectMessage();

        String testData = message.getEncoded();

        MessageReader is = getMessageReader(testData);

        assertEquals(message, is.readMessage());
    }

    @Test
    public void oneMessageTestWithDelimiterAtEnd() {
        Message message = getTestProjectMessage();

        String testData = message.getEncoded() + Message.DELIMITER;

        MessageReader is = getMessageReader(testData);

        assertEquals(message, is.readMessage());
    }

    @Test
    public void multipleMessagesTest() {
        Message firstMsg = getTestProjectMessage();
        Message secondMsg = getTestConfigMessage();

        String testData = firstMsg.getEncoded() + Message.DELIMITER + secondMsg.getEncoded();

        MessageReader is = getMessageReader(testData);

        assertEquals(firstMsg, is.readMessage());
        assertEquals(secondMsg, is.readMessage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullParam() {
        new MessageReader(null);
    }

    @Test(expected = JsonSyntaxException.class)
    public void testCutOffMessage() {
        Message message = getTestProjectMessage();

        String testData = message.getEncoded().substring(1);

        getMessageReader(testData).readMessage();
    }

    @Test
    public void testFirstCutOffMessage() {
        Message firstMsg = getTestProjectMessage();
        Message secondMsg = getTestConfigMessage();

        String testData = (firstMsg.getEncoded() + Message.DELIMITER + secondMsg.getEncoded()).substring(1);

        MessageReader is = getMessageReader(testData);

        try {
            is.readMessage();
            fail("Reading first message should throw JsonSyntaxException");
        } catch (JsonSyntaxException e) {
            // expected
        }
        assertEquals(secondMsg, is.readMessage());
    }

    private MessageReader getMessageReader(final String text) {
        return new MessageReader(new ByteArrayInputStream(text.getBytes()));
    }

}
