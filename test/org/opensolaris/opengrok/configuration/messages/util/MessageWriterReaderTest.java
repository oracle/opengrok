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

import org.junit.Test;
import org.opensolaris.opengrok.configuration.messages.MessageTestUtils;
import org.opensolaris.opengrok.configuration.messages.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class MessageWriterReaderTest {

    @Test
    public void testReadAndWrite() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MessageWriter writer = new MessageWriter(baos);

        Message msg = MessageTestUtils.getTestProjectMessage();
        writer.writeMessage(msg);

        MessageReader reader = new MessageReader(new ByteArrayInputStream(baos.toByteArray()));
        Message readMsg = reader.readMessage();

        assertEquals(msg, readMsg);
    }

    @Test
    public void testReadAndWriteMultiple() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MessageWriter writer = new MessageWriter(baos);

        Message msg1 = MessageTestUtils.getTestProjectMessage();
        Message msg2 = MessageTestUtils.getTestConfigMessage();
        writer.writeMessage(msg1);
        writer.writeMessage(msg2);

        MessageReader reader = new MessageReader(new ByteArrayInputStream(baos.toByteArray()));
        Message readMsg1 = reader.readMessage();
        Message readMsg2 = reader.readMessage();

        assertEquals(msg1, readMsg1);
        assertEquals(msg2, readMsg2);
    }

}
