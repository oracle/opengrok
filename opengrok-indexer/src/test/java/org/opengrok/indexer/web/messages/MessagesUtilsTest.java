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
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MessagesUtilsTest {
    RuntimeEnvironment env;

    private MessagesContainer container;

    @After
    public void tearDown() {
        container.stopExpirationTimer();
    }

    @Before
    public void setUp() {
        env = RuntimeEnvironment.getInstance();

        container = new MessagesContainer();
        container.startExpirationTimer();
    }

    @Test
    public void testGetMessagesForNonExistentProject() {
        String projectName = "nonexistent";
        Project foo = new Project(projectName, "/doesnotexist");
        HashMap<String, Project> projects = new HashMap<>();
        projects.put(projectName, foo);
        env.setProjectsEnabled(true);
        env.setProjects(projects);

        String jsonString = MessagesUtils.messagesToJson(projectName);
        assertNotNull(jsonString);
        assertEquals("", jsonString);
    }

    @Test
    public void testGetHighestMessageLevel() {
        // Reverse the order of values() first to better test the behavior of getHighestCssClassLevel().
        List<Message.MessageLevel> levels = Arrays.asList(Message.MessageLevel.values());
        Collections.reverse(levels);

        // Test the behavior with no messages.
        assertEquals(0, container.getAllMessages().size());
        assertNull(MessagesUtils.getHighestMessageLevel(container.getAllMessages()));

        // Add one message for each level.
        for (Message.MessageLevel val : levels) {
            Message m = new Message("test " + val,
                    Collections.singleton("test" + val),
                    val,
                    Duration.ofMinutes(10));
            container.addMessage(m);
        }

        assertEquals(Message.MessageLevel.values().length, container.getAllMessages().size());
        assertEquals(Message.MessageLevel.ERROR.toString(),
                MessagesUtils.getHighestMessageLevel(container.getAllMessages()));
    }

    @Test
    public void testGetMessageLevel() {
        HashMap<String, Message.MessageLevel> tagLevels = new HashMap<>();
        tagLevels.put("foo", Message.MessageLevel.INFO);
        tagLevels.put("bar", Message.MessageLevel.ERROR);

        for (String tag : tagLevels.keySet()) {
            Message m = new Message(
                    "text",
                    Collections.singleton(tag),
                    tagLevels.get(tag),
                    Duration.ofMinutes(10));
            env.addMessage(m);
        }

        assertEquals(Message.MessageLevel.ERROR.toString(),
                MessagesUtils.getMessageLevel(tagLevels.keySet().toArray(new String[0])));
    }
}
