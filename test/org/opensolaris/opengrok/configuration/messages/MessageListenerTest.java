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
package org.opensolaris.opengrok.configuration.messages;

import org.junit.Assert;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import java.time.Instant;

public class MessageListenerTest {

    @Test
    public void testCanAcceptMessage() throws Exception {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        MessageListener listener = MessageTestUtils.initMessageListener(instance);
        listener.removeAllMessages();

        Message.Builder<NormalMessage> mb = new Message.Builder<>(NormalMessage.class)
                .addTag("main")
                .setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 3000));

        Assert.assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 2000));
        Assert.assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 1000));
        Assert.assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 1));
        Assert.assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 50));
        Assert.assertTrue(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 100));
        Assert.assertTrue(listener.canAcceptMessage(mb.build()));

        Assert.assertEquals(0, listener.getMessagesInTheSystem());

        long now = System.currentTimeMillis();
        for (int i = 0; i < listener.getMessageLimit(); i++) {
            Message m2 = new Message.Builder<>(NormalMessage.class)
                    .addTag("main")
                    .setText("text")
                    .setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 5000))
                    .build();

            MessageTestUtils.setCreated(m2, Instant.ofEpochMilli(now + i));

            Assert.assertTrue(listener.canAcceptMessage(m2));

            MessageTestUtils.processMessage(listener, m2);

            Assert.assertEquals(i + 1, listener.getMessagesInTheSystem());
        }
        Assert.assertEquals(listener.getMessageLimit(), listener.getMessagesInTheSystem());

        for (int i = 0; i < listener.getMessageLimit() * 2; i++) {
            Message m2 = new Message.Builder<>(NormalMessage.class)
                    .addTag("main")
                    .setText("text")
                    .setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 5000)).build();

            Assert.assertFalse(listener.canAcceptMessage(m2));
            MessageTestUtils.processMessage(listener, m2);
            Assert.assertEquals(listener.getMessageLimit(), listener.getMessagesInTheSystem());
        }

        listener.removeAllMessages();
        listener.stopConfigurationListenerThread();
    }

}
