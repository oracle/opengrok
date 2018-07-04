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
package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.messages.Message;
import org.opengrok.indexer.web.messages.MessagesContainer;
import org.opengrok.indexer.web.messages.MessagesContainer.AcceptedMessage;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagesControllerTest extends JerseyTest {

    private static final GenericType<List<AcceptedMessageModel>> messagesType =
            new GenericType<List<AcceptedMessageModel>>() {};

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static class AcceptedMessageModel {
        public String acceptedTime;
        public String expirationTime;
        public boolean expired;
        public Message message;
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(MessagesController.class);
    }

    @Before
    public void setupMessageListener() throws Exception {
        setMessageContainer(env, new MessagesContainer());
        env.startExpirationTimer();
    }

    @After
    public void tearDownMessageListener() {
        env.stopExpirationTimer();
    }

    private void setMessageContainer(RuntimeEnvironment env, MessagesContainer container) throws Exception {
        Field f = RuntimeEnvironment.class.getDeclaredField("messagesContainer");
        f.setAccessible(true);
        f.set(env, container);
    }

    @Test
    public void addMessageTest() {
        addMessage("test message");

        assertFalse(env.getMessages().isEmpty());

        AcceptedMessage msg = env.getMessages().first();

        assertEquals("test message", msg.getMessage().getText());
    }

    private void addMessage(String text, String... tags) {
        if (tags == null || tags.length == 0) {
            tags = new String[] {MessagesContainer.MESSAGES_MAIN_PAGE_TAG};
        }

        Message m = new Message(text, new HashSet<>(Arrays.asList(tags)), "cssClass", Duration.ofMinutes(10));

        target("messages")
                .request()
                .post(Entity.json(m));
    }

    @Test
    public void removeMessageTest() {
        env.addMessage(new Message(
                "test",
                Collections.singleton(MessagesContainer.MESSAGES_MAIN_PAGE_TAG),
                "test",
                Duration.ofMinutes(10)
        ));

        assertFalse(env.getMessages().isEmpty());

        removeMessages(MessagesContainer.MESSAGES_MAIN_PAGE_TAG);

        assertTrue(env.getMessages().isEmpty());
    }

    private void removeMessages(final String tag) {
        target("messages")
                .queryParam("tag", tag)
                .request()
                .delete();
    }

    @Test
    public void addAndRemoveTest() {
        addMessage("test", "test");
        addMessage("test", "test");

        assertEquals(2, env.getMessages("test").size());

        removeMessages("test");

        assertTrue(env.getMessages("test").isEmpty());
    }

    @Test
    public void addAndRemoveDifferentTagsTest() {
        addMessage("test", "tag1");
        addMessage("test", "tag2");


        assertEquals(1, env.getMessages("tag1").size());
        assertEquals(1, env.getMessages("tag2").size());

        removeMessages("tag1");

        assertEquals(0, env.getMessages("tag1").size());
        assertEquals(1, env.getMessages("tag2").size());

        removeMessages("tag2");

        assertTrue(env.getMessages("tag2").isEmpty());
    }

    @Test
    public void addMessageNegativeDurationTest() throws Exception {
        Message m = new Message("text", Collections.singleton("test"), "cssClass", Duration.ofMinutes(1));
        setDuration(m, Duration.ofMinutes(-10));

        Response r = target("messages")
                .request()
                .post(Entity.json(m));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    private void setDuration(final Message m, final Duration duration) throws Exception {
        Field f = Message.class.getDeclaredField("duration");
        f.setAccessible(true);
        f.set(m, duration);
    }

    @Test
    public void addEmptyMessageTest() throws Exception {
        Message m = new Message("text", Collections.singleton("test"), "cssClass", Duration.ofMinutes(1));
        setText(m, "");

        Response r = target("messages")
                .request()
                .post(Entity.json(m));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    private void setText(final Message m, final String text) throws Exception {
        Field f = Message.class.getDeclaredField("text");
        f.setAccessible(true);
        f.set(m, text);
    }

    @Test
    public void getAllMessagesTest() {
        addMessage("text1", "info");
        addMessage("text2", "main");

        List<AcceptedMessageModel> allMessages = target("messages")
                .request()
                .get(messagesType);

        assertEquals(2, allMessages.size());
    }

    @Test
    public void getSpecificMessageTest() {
        addMessage("text", "info");

        List<AcceptedMessageModel> messages = target("messages")
                .queryParam("tag", "info")
                .request()
                .get(messagesType);

        assertEquals(1, messages.size());
        assertEquals("text", messages.get(0).message.getText());

        assertThat(messages.get(0).message.getTags(), contains("info"));
    }

    @Test
    public void multipleTagsTest() {
        addMessage("test", "info", "main");

        List<AcceptedMessageModel> allMessages = target("messages")
                .request()
                .get(messagesType);

        assertEquals(1, allMessages.size());
    }

    @Test
    public void multipleMessageAndTagsTest() {
        addMessage("test1", "tag1", "tag2");
        addMessage("test2", "tag3", "tag4");

        List<AcceptedMessageModel> allMessages = target("messages")
                .queryParam("tag", "tag3")
                .request()
                .get(messagesType);

        assertEquals(1, allMessages.size());
    }

}
