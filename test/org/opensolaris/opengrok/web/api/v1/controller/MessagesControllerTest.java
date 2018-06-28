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
package org.opensolaris.opengrok.web.api.v1.controller;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.messages.Message;
import org.opensolaris.opengrok.web.messages.MessagesContainer;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagesControllerTest extends JerseyTest {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    protected Application configure() {
        return new ResourceConfig(MessagesController.class);
    }

    @BeforeClass
    public static void setupMessageListener() {
        env.startExpirationTimer();
    }

    @AfterClass
    public static void tearDownMessageListener() {
        env.stopExpirationTimer();
    }

    @Test
    public void addMessageTest() {
        addMessage("test message");

        assertFalse(env.getMessages().isEmpty());

        MessagesContainer.AcceptedMessage msg = env.getMessages().first();

        assertEquals("test message", msg.getMessage().getText());

        env.removeAnyMessage(Collections.singleton(MessagesContainer.MESSAGES_MAIN_PAGE_TAG));

        assertTrue(env.getMessages().isEmpty());
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

    private void removeMessages(String... tags) {
        target("messages")
                .queryParam("tag", (Object[]) tags)
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

        assertTrue(env.getMessages().isEmpty());
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

        assertTrue(env.getMessages().isEmpty());
    }

    private void setText(final Message m, final String text) throws Exception {
        Field f = Message.class.getDeclaredField("text");
        f.setAccessible(true);
        f.set(m, text);
    }

}
