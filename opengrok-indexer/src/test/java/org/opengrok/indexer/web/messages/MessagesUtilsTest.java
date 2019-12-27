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
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.*;

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


    /**
     * Test MessagesContainer.getHighestCssClassLevel() - used in the UI.
     */
    @Test
    public void testMessageCssClass() {
        // Reverse the order of values() first to better test the behavior of getHighestCssClassLevel().
        List<Message.CssClassType> cssClasses = Arrays.asList(Message.CssClassType.values());
        Collections.reverse(cssClasses);

        // Test the behavior with no messages.
        assertEquals(0, container.getAllMessages().size());
        assertNull(MessagesUtils.getHighestCssClassLevel(container.getAllMessages()));

        // Add one message for each cssClass.
        for (Message.CssClassType val : cssClasses) {
            Message m = new Message("test " + val,
                    Collections.singleton("test" + val),
                    val.toString(),
                    Duration.ofMinutes(10));
            container.addMessage(m);
        }

        assertEquals(Message.CssClassType.values().length, container.getAllMessages().size());
        assertEquals(Message.CssClassType.ERROR.toString(),
                MessagesUtils.getHighestCssClassLevel(container.getAllMessages()));
    }
}
