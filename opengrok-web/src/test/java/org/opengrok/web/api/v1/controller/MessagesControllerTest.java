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
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.spi.TestContainer;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagesControllerTest extends OGKJerseyTest {

    private static final GenericType<List<AcceptedMessageModel>> messagesType =
            new GenericType<List<AcceptedMessageModel>>() {
            };

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static class AcceptedMessageModel {
        public String created;
        public String expiration;
        public boolean expired;
        public String text;
        public String messageLevel;
        public Set<String> tags;
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(MessagesController.class);
    }

    // Allow entity body for DELETE method on the client side.
    @Override
    protected void configureClient(ClientConfig config) {
        config.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
    }

    // Allow entity body for DELETE method on the server side.
    public static class CustomGrizzlyTestContainerFactory implements TestContainerFactory {
        public CustomGrizzlyTestContainerFactory() {
        }

        public TestContainer create(URI baseUri, DeploymentContext context) {
            return new GrizzlyTestContainer(baseUri, context);
        }

        private static class GrizzlyTestContainer implements TestContainer {
            private URI baseUri;
            private final HttpServer server;

            private GrizzlyTestContainer(URI baseUri, DeploymentContext context) {
                this.baseUri = UriBuilder.fromUri(baseUri).path(context.getContextPath()).build();
                this.server = GrizzlyHttpServerFactory.createHttpServer(this.baseUri, context.getResourceConfig(), false);
                this.server.getServerConfiguration().setAllowPayloadForUndefinedHttpMethods(true);
            }

            public ClientConfig getClientConfig() {
                return null;
            }

            public URI getBaseUri() {
                return this.baseUri;
            }

            public void start() {
                if (this.server.isStarted()) {
                    return;
                }

                try {
                    this.server.start();
                    if (this.baseUri.getPort() == 0) {
                        this.baseUri = UriBuilder.fromUri(this.baseUri).port(this.server.getListener("grizzly").getPort()).build(new Object[0]);
                    }
                } catch (IOException e) {
                    throw new TestContainerException(e);
                }
            }

            public void stop() {
                if (this.server.isStarted()) {
                    this.server.shutdownNow();
                }
            }
        }
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new CustomGrizzlyTestContainerFactory();
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

        assertEquals("test&nbsp;message", msg.getMessage().getText());
    }

    @Test
    public void addMessageWithInvalidLevel() throws JsonProcessingException {
        // Construct correct Message object first.
        Message msg = new Message(
                "message with broken message level",
                Collections.singleton(MessagesContainer.MESSAGES_MAIN_PAGE_TAG),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(10));

        // Convert it to JSON string and replace the messageLevel value.
        ObjectMapper objectMapper = new ObjectMapper();
        final String invalidMessageLevel = "invalid";
        String msgAsString = objectMapper.writeValueAsString(msg);
        msgAsString = msgAsString.replaceAll(Message.MessageLevel.INFO.toString(), invalidMessageLevel);
        assertTrue(msgAsString.contains(invalidMessageLevel));

        // Finally, send the request as JSON string.
        Response r = target("messages")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(msgAsString));

        assertEquals(0,
                env.getMessages().stream().filter(m -> m.getMessageLevel().equals(invalidMessageLevel)).count());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), r.getStatus());
    }

    private void addMessage(String text, String... tags) {
        if (tags == null || tags.length == 0) {
            tags = new String[] {MessagesContainer.MESSAGES_MAIN_PAGE_TAG};
        }

        Message m = new Message(
                text,
                new HashSet<>(Arrays.asList(tags)),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(10));

        target("messages")
                .request()
                .post(Entity.json(m));
    }

    @Test
    public void removeMessageTest() {
        env.addMessage(new Message(
                "test",
                Collections.singleton(MessagesContainer.MESSAGES_MAIN_PAGE_TAG),
                Message.MessageLevel.INFO,
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

    private void removeMessages(final String tag, final String text) {
        Entity<String> requestEntity = Entity.entity(text, MediaType.TEXT_PLAIN);
        target("messages")
                .queryParam("tag", tag)
                .request()
                .build("DELETE", requestEntity).
                invoke();
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
    public void addAndRemoveWithTextTest() {
        final String tag = "foo";
        final String text = "text";

        addMessage(text, tag);
        assertEquals(1, env.getMessages(tag).size());

        removeMessages(tag + "bar", text);
        assertEquals(1, env.getMessages(tag).size());

        removeMessages(tag, text + "bar");
        assertEquals(1, env.getMessages(tag).size());

        removeMessages(tag, text);
        assertTrue(env.getMessages(tag).isEmpty());
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
        Message m = new Message("text",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(1));
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
        Message m = new Message("text",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(1));
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
        assertEquals("text", messages.get(0).text);

        assertThat(messages.get(0).tags, contains("info"));
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
