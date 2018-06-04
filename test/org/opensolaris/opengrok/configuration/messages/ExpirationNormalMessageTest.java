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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.MessageListener.AcceptedMessage;

import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.expire;

public class ExpirationNormalMessageTest {

    private RuntimeEnvironment env;

    private MessageListener listener;

    @Before
    public void setUp() throws Exception {
        env = RuntimeEnvironment.getInstance();
        listener = MessageTestUtils.initMessageListener(env);
        listener.removeAllMessages();
    }

    @After
    public void tearDown() {
        listener.removeAllMessages();
        listener.stopListenerThread();
    }

    @Test
    public void testExpirationSingle() throws Exception {
        runSingle();
    }

    @Test
    public void testExpirationMultiple() throws Exception {
        runMultiple();
    }

    /**
     * This doesn't make sense since we're testing the behaviour of the timer
     * thread.
     * @throws Exception exception
     */
    @Test
    public void testExpirationConcurrent() throws Exception {
        for (int i = 0; i < 10; i++) {
            runConcurrentModification();
        }
    }

    @Test
    public void testExpirationConcurrentTimer() throws Exception {
        env.startExpirationTimer();
        for (int i = 0; i < 10; i++) {
            runConcurrentModification();
        }
        listener.stopExpirationTimer();
    }

    protected void runSingle() throws Exception {
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        Message m1 = new Message.Builder<>(NormalMessage.class)
                .addTag("main")
                .setDuration(Duration.ofMillis(2000000))
                .setText("text")
                .build();
        listener.addMessage(m1);
        Assert.assertEquals(1, listener.getMessagesInTheSystem());

        for (int i = 0; i < 50; i++) {
            Assert.assertEquals(1, listener.getMessagesInTheSystem());
            Assert.assertNotNull(env.getMessages());
            Assert.assertEquals(Collections.singleton(m1),
                    env.getMessages().stream().map(AcceptedMessage::getMessage).collect(Collectors.toSet()));
        }
        expire(m1);
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    protected void runMultiple() throws Exception {
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        Message m1 = new Message.Builder<>(NormalMessage.class)
                .addTag("main")
                .setDuration(Duration.ofMillis(2000000))
                .setText("text")
                .build();
        listener.addMessage(m1);

        Message m2 = new Message.Builder<>(NormalMessage.class)
                .addTag("main")
                .setDuration(Duration.ofMillis(2000000))
                .setText("other text")
                .build();
        listener.addMessage(m2);

        Assert.assertEquals(2, listener.getMessagesInTheSystem());
        Assert.assertNotNull(env.getMessages());
        Assert.assertEquals(new TreeSet<>(Arrays.asList(m1, m2)),
                env.getMessages().stream().map(AcceptedMessage::getMessage).collect(Collectors.toSet()));

        for (int i = 0; i < 30; i++) {
            Assert.assertEquals(2, listener.getMessagesInTheSystem());
            Assert.assertNotNull(env.getMessages());
            Assert.assertEquals(new TreeSet<>(Arrays.asList(m1, m2)),
                    env.getMessages().stream().map(AcceptedMessage::getMessage).collect(Collectors.toSet()));
        }
        expire(m1);
        for (int i = 0; i < 30; i++) {
            Assert.assertEquals(1, listener.getMessagesInTheSystem());
            Assert.assertNotNull(env.getMessages());
            Assert.assertEquals(Collections.singleton(m2),
                    env.getMessages().stream().map(AcceptedMessage::getMessage).collect(Collectors.toSet()));
        }
        expire(m2);
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    protected void runConcurrentModification() throws Exception {
        Message.Builder builder = new Message.Builder<>(NormalMessage.class);
        builder.addTag("main");
        builder.setText("text");
        for (int i = 0; i < 500; i++) {
            builder.setDuration(Duration.ofMillis(2000000 + i));
            Message m = builder.build();
            processMessage(listener, m);
        }

        Assert.assertEquals(500, listener.getMessagesInTheSystem());
        Assert.assertEquals(500, env.getMessages("main").size());

        Thread.UncaughtExceptionHandler h = (th, ex) -> {
            if (ex instanceof ConcurrentModificationException) {
                Assert.fail("The messages shouldn't throw an concurrent modification exception");
            } else {
                Assert.fail("The messages shouldn't throw any other exception, too");
            }
        };

        Thread t = new Thread(this::invokeExpireMessages);
        t.setUncaughtExceptionHandler(h);

        // expire all
        for (AcceptedMessage m : env.getMessages("main")) {
            expire(m);
        }

        for (int i = 0; i < 500; i++) {
            if (i == 100) {
                t.start();
            }
            try {
                for (AcceptedMessage m : env.getMessages("main")) {
                    // just iterate
                }
            } catch (ConcurrentModificationException ex) {
                Assert.fail("The messages shouldn't throw an concurrent modification exception");
            } catch (Throwable ex) {
                Assert.fail("The messages shouldn't throw any other exception, too");
            }
        }
        try {
            t.join();
        } catch (InterruptedException ex) {
        }
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        Assert.assertEquals(0, env.getMessages("main").size());
    }

    private void invokeExpireMessages() {
        try {
            Method method = MessageListener.class.getDeclaredMethod("expireMessages");
            method.setAccessible(true);
            method.invoke(listener);
        } catch (Exception ex) {
            Assert.fail("invokeRemoveAll should not throw an exception");
        }
    }

}
