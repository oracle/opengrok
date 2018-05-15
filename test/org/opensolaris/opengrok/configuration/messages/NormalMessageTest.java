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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import static org.opensolaris.opengrok.configuration.messages.MessageListener.MESSAGES_MAIN_PAGE_TAG;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;

public class NormalMessageTest {

    private RuntimeEnvironment env;

    private MessageListener listener;

    @Before
    public void setUp() throws Exception {
        env = RuntimeEnvironment.getInstance();
        listener = MessageTestUtils.initMessageListener(env);
    }

    @After
    public void tearDown() {
        listener.removeAllMessages();
        listener.stopConfigurationListenerThread();
    }

    @Test
    public void testValidate() {
        Message.Builder<NormalMessage> builder = new Message.Builder<>(NormalMessage.class);
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.setText("text");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setCssClass(null);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertEquals("info", builder.build().getCssClass());
        builder.clearTags();
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertTrue(builder.build().hasTag(MESSAGES_MAIN_PAGE_TAG));
    }

    @Test
    public void testApplyNoTag() throws Exception {
        Message m = new Message.Builder<>(NormalMessage.class)
                .setText("text")
                .build();
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, m);
        // the main tag is added by default if no tag is present
        Assert.assertEquals(1, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplySingle() throws Exception {
        Message m = new Message.Builder<>(NormalMessage.class)
                .addTag(MESSAGES_MAIN_PAGE_TAG)
                .setText("text")
                .build();
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, m);
        Assert.assertEquals(1, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplyMultiple() throws Exception {
        List<Message> msgs = new ArrayList<>();

        Instant defaultExpiration = new NormalMessage().getExpiration();

        for (int i = 0; i < 3; i++) {
            Message m = new Message.Builder<>(NormalMessage.class)
                    .addTag(MESSAGES_MAIN_PAGE_TAG)
                    .addTag("project")
                    .addTag("pull")
                    .setText("text")
                    .setExpiration(defaultExpiration)
                    .build();

            MessageTestUtils.setCreated(m, Instant.ofEpochMilli(System.currentTimeMillis() + i * 1000));
            msgs.add(m);
        }

        Assert.assertEquals(0, listener.getMessagesInTheSystem());

        for (Message m : msgs) {
            processMessage(listener, m);
        }

        // 3 * 3 - each message for each tag
        Assert.assertEquals(3 * 3, listener.getMessagesInTheSystem());
        Assert.assertNotNull(env.getMessages());
        Assert.assertEquals(3, env.getMessages().size());
        Assert.assertNotNull(env.getMessages(MESSAGES_MAIN_PAGE_TAG));
        Assert.assertEquals(3, env.getMessages(MESSAGES_MAIN_PAGE_TAG).size());
        Assert.assertEquals(new TreeSet<>(msgs), env.getMessages(MESSAGES_MAIN_PAGE_TAG));

        Assert.assertNotNull(env.getMessages("project"));
        Assert.assertEquals(3, env.getMessages("project").size());
        Assert.assertEquals(new TreeSet<>(msgs), env.getMessages("project"));
        Assert.assertNotNull(env.getMessages("pull"));
        Assert.assertEquals(3, env.getMessages("pull").size());
        Assert.assertEquals(new TreeSet<>(msgs), env.getMessages("pull"));
    }

    @Test
    public void testApplyMultipleUnique() throws Exception {
        List<Message> msgs = new ArrayList<>();

        Instant instant = Instant.now();

        Instant defaultExpiration = new NormalMessage().getExpiration();

        for (int i = 0; i < 3; i++) {
            Message m = new Message.Builder<>(NormalMessage.class)
                    .addTag(MESSAGES_MAIN_PAGE_TAG)
                    .setText("text")
                    .setExpiration(defaultExpiration)
                    .build();

            MessageTestUtils.setCreated(m, instant);

            msgs.add(m);
        }

        Assert.assertEquals(0, listener.getMessagesInTheSystem());

        for (Message m : msgs) {
            processMessage(listener, m);
        }

        Assert.assertEquals(1, listener.getMessagesInTheSystem());
        Assert.assertNotNull(env.getMessages());
        Assert.assertEquals(1, env.getMessages().size());
        Assert.assertNotNull(env.getMessages(MESSAGES_MAIN_PAGE_TAG));
        Assert.assertEquals(1, env.getMessages(MESSAGES_MAIN_PAGE_TAG).size());
    }

}
