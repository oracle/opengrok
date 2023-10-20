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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.web.messages.JSONUtils.getTopLevelJSONFields;

class MessagesContainerTest {

    private MessagesContainer container;

    @BeforeEach
    void setUp() {
        container = new MessagesContainer();
        container.startExpirationTimer();
    }

    @AfterEach
    void tearDown() {
        container.stopExpirationTimer();
    }

    @Test
    void addAndGetTest() {
        Message m = new Message("test",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(10));

        container.addMessage(m);

        assertEquals(m, container.getMessages("test").first().getMessage());
    }

    @Test
    void addNullTest() {
        assertThrows(IllegalArgumentException.class, () -> container.addMessage(null));
    }

    @Test
    void removeNullTest() {
        // the call should not throw an exception
        assertDoesNotThrow( () -> container.removeAnyMessage(null));
    }

    @Test
    void parallelAddTest() throws Exception {
        container.setMessageLimit(5000);

        parallelAddMessages();

        assertEquals(1000, container.getMessages("test").size());
        assertEquals(1000, getMessagesInTheSystem());
    }

    private void parallelAddMessages() throws Exception {
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int index = i;
            Thread t = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    Message m = new Message("test" + index + j,
                            Collections.singleton("test"),
                            Message.MessageLevel.INFO,
                            Duration.ofMinutes(10));
                    container.addMessage(m);
                }
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    private int getMessagesInTheSystem() throws Exception {
        Field f = MessagesContainer.class.getDeclaredField("messagesInTheSystem");
        f.setAccessible(true);
        return (int) f.get(container);
    }

    @Test
    void parallelAddLimitTest() throws Exception {
        container.setMessageLimit(10);

        parallelAddMessages();

        assertEquals(10, container.getMessages("test").size());
        assertEquals(10, getMessagesInTheSystem());
    }

    @Test
    void expirationTest() {
        Message m = new Message("test",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMillis(10));

        container.addMessage(m);

        await().atMost(2, TimeUnit.SECONDS).until(() -> container.getMessages(Message.MessageLevel.INFO.toString()).isEmpty());
    }

    @Test
    void removeTest() {
        Message m = new Message("test",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMillis(10));

        container.addMessage(m);

        container.removeAnyMessage(Collections.singleton("test"));

        assertTrue(container.getMessages("test").isEmpty());
    }

    @Test
    void getMessagesNullTest() {
        assertThrows(IllegalArgumentException.class, () -> container.getMessages(null));
    }

    /**
     * tests serialization of MessagesContainer.AcceptedMessage.
     */
    @Test
    void testJSON() throws IOException {
        Message m = new Message("testJSON",
                Collections.singleton("testJSON"),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(10));

        container.addMessage(m);

        MessagesContainer.AcceptedMessage am = container.getMessages("testJSON").first();
        String jsonString = am.toJSON();
        assertEquals(new HashSet<>(Arrays.asList("tags", "expired", "created", "expiration", "messageLevel", "text")),
                getTopLevelJSONFields(jsonString));
    }
}
