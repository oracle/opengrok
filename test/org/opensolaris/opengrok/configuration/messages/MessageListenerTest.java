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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;

public class MessageListenerTest {

    private static class BooleanWrapper {
        private boolean value = false;
    }

    @Test
    public void addHandlerTest() throws Exception {
        BooleanWrapper bw = new BooleanWrapper();

        MessageListener listener = new MessageListener();
        listener.addMessageHandler(NormalMessage.class, m -> {
            bw.value = true;
            return Response.empty();
        });

        processMessage(listener, new Message.Builder<>(NormalMessage.class).build());

        assertTrue(bw.value);
    }

    @Test
    public void removeHandlerTest() throws Exception {
        BooleanWrapper bw = new BooleanWrapper();

        MessageHandler handler = m -> {
            bw.value = true;
            return Response.empty();
        };
        MessageListener listener = new MessageListener();
        listener.addMessageHandler(NormalMessage.class, handler);
        listener.removeMessageHandler(NormalMessage.class, handler);

        processMessage(listener, new Message.Builder<>(NormalMessage.class).build());

        assertFalse(bw.value);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addHandlerNullTest() {
        new MessageListener().addMessageHandler(NormalMessage.class, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addHandlerNullTest2() {
        new MessageListener().addMessageHandler(null, m -> Response.empty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addHandlerNullTest3() {
        new MessageListener().addMessageHandler(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void removeHandlerNullTest() {
        new MessageListener().removeMessageHandler(NormalMessage.class, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void removeHandlerNullTest2() {
        new MessageListener().removeMessageHandler(null, m -> Response.empty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void removeHandlerNullTest3() {
        new MessageListener().removeMessageHandler(null, null);
    }

    @Test
    public void testCanAcceptMessage() throws Exception {
        RuntimeEnvironment instance = RuntimeEnvironment.getInstance();
        MessageListener listener = MessageTestUtils.initMessageListener(instance);
        listener.removeAllMessages();

        Message.Builder<NormalMessage> mb = new Message.Builder<>(NormalMessage.class)
                .addTag("main")
                .setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 3000));

        assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 2000));
        assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 1000));
        assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() - 1));
        assertFalse(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 50));
        assertTrue(listener.canAcceptMessage(mb.build()));
        mb.setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 100));
        assertTrue(listener.canAcceptMessage(mb.build()));

        Assert.assertEquals(0, listener.getMessagesInTheSystem());

        long now = System.currentTimeMillis();
        for (int i = 0; i < listener.getMessageLimit(); i++) {
            Message m2 = new Message.Builder<>(NormalMessage.class)
                    .addTag("main")
                    .setText("text")
                    .setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 5000))
                    .build();

            MessageTestUtils.setCreated(m2, Instant.ofEpochMilli(now + i));

            assertTrue(listener.canAcceptMessage(m2));

            processMessage(listener, m2);

            Assert.assertEquals(i + 1, listener.getMessagesInTheSystem());
        }
        Assert.assertEquals(listener.getMessageLimit(), listener.getMessagesInTheSystem());

        for (int i = 0; i < listener.getMessageLimit() * 2; i++) {
            Message m2 = new Message.Builder<>(NormalMessage.class)
                    .addTag("main")
                    .setText("text")
                    .setExpiration(Instant.ofEpochMilli(System.currentTimeMillis() + 5000)).build();

            assertFalse(listener.canAcceptMessage(m2));
            processMessage(listener, m2);
            Assert.assertEquals(listener.getMessageLimit(), listener.getMessagesInTheSystem());
        }

        listener.removeAllMessages();
    }

}
