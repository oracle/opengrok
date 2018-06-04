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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import static org.opensolaris.opengrok.configuration.messages.MessageListener.MESSAGES_MAIN_PAGE_TAG;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;

public class AbortMessageTest {

    private MessageListener listener;

    @Before
    public void setUp() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        listener = MessageTestUtils.initMessageListener(env);
        listener.removeAllMessages();
    }

    @After
    public void tearDown() {
        listener.removeAllMessages();
    }

    @Test
    public void testValidate() {
        Message.Builder builder = new Message.Builder<>(AbortMessage.class);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setText("text");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setCssClass(null);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertTrue(builder.build().hasTag(MESSAGES_MAIN_PAGE_TAG));
    }

    @Test
    public void testApplyNoTag() throws Exception {
        Message m = new AbortMessage();

        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, m);
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplyNoTagEmpty() throws Exception {
        Message m = new Message.Builder<>(AbortMessage.class)
                .addTag("main")
                .build();

        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, m);
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplyNoTagFull() throws Exception {
        deliverSimpleNormalMessage();

        Assert.assertEquals(1, listener.getMessagesInTheSystem());
        processMessage(listener, new Message.Builder<>(AbortMessage.class).build());
        // the main tag is added by default if no tag is present
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    private void deliverSimpleNormalMessage() throws Exception {
        Message m = new Message.Builder<>(NormalMessage.class)
                .addTag("main")
                .setText("text")
                .build();
        processMessage(listener, m);
    }

    @Test
    public void testApplySingle() throws Exception {
        deliverSimpleNormalMessage();

        Assert.assertEquals(1, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("main"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplySingleWrongTag() throws Exception {
        deliverSimpleNormalMessage();

        Assert.assertEquals(1, listener.getMessagesInTheSystem());

        processMessage(listener, getAbortMessageWithTags("other"));
        Assert.assertEquals(1, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplyReverse() throws Exception {
        processMessage(listener, getAbortMessageWithTags("main"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());

        deliverSimpleNormalMessage();
        Assert.assertEquals(1, listener.getMessagesInTheSystem());
    }

    @Test
    public void testApplyMultiple() throws Exception {
        deliverSimpleNormalMessage();

        Message m = new Message.Builder<>(NormalMessage.class).addTag("project").setText("text").build();
        processMessage(listener, m);
        m = new Message.Builder<>(NormalMessage.class).addTag("pull").setText("text").build();

        processMessage(listener, m);
        Assert.assertEquals(3, listener.getMessagesInTheSystem());

        processMessage(listener, getAbortMessageWithTags("other"));
        Assert.assertEquals(3, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("other pro"));
        Assert.assertEquals(3, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("main"));
        Assert.assertEquals(2, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("main"));
        Assert.assertEquals(2, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("main", "other"));
        Assert.assertEquals(2, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("pull", "project"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("pull", "project"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("main"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("pull"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("project"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
        processMessage(listener, getAbortMessageWithTags("other"));
        Assert.assertEquals(0, listener.getMessagesInTheSystem());
    }

    private AbortMessage getAbortMessageWithTags(final String... tags) {
        Message.Builder<AbortMessage> b = new Message.Builder<>(AbortMessage.class);
        for (String t : tags) {
            b.addTag(t);
        }
        return b.build();
    }
}
