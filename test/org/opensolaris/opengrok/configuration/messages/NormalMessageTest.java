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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.util.Arrays;
import java.util.Date;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

public class NormalMessageTest {

    RuntimeEnvironment env;

    private Message[] makeArray(Message... messages) {
        return messages;
    }

    @Before
    public void setUp() {
        env = RuntimeEnvironment.getInstance();
        env.removeAllMessages();
    }

    @After
    public void tearDown() {
        env.removeAllMessages();
    }

    @Test
    public void testApplyNoTag() {
        Message m = new NormalMessage();

        Assert.assertEquals(0, env.getMessagesInTheSystem());
        m.apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplySingle() {
        Message m = new NormalMessage().addTag("main");
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        m.apply(env);
        Assert.assertEquals(1, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplyMultiple() {
        Message[] m = makeArray(new NormalMessage(), new NormalMessage(), new NormalMessage());

        for (int i = 0; i < m.length; i++) {
            m[i].addTag("main");
            m[i].addTag("project");
            m[i].addTag("pull");
            m[i].setCreated(new Date(System.currentTimeMillis() + i * 1000));
        }

        Assert.assertEquals(0, env.getMessagesInTheSystem());

        for (int i = 0; i < m.length; i++) {
            m[i].apply(env);
        }

        // 3 * 3 - each message for each tag
        Assert.assertEquals(3 * 3, env.getMessagesInTheSystem());
        Assert.assertNotNull(env.getMessages());
        Assert.assertEquals(3, env.getMessages().size());
        Assert.assertNotNull(env.getMessages("main"));
        Assert.assertEquals(3, env.getMessages("main").size());
        Assert.assertEquals(new TreeSet<Message>(Arrays.asList(m)), env.getMessages("main"));

        Assert.assertNotNull(env.getMessages("project"));
        Assert.assertEquals(3, env.getMessages("project").size());
        Assert.assertEquals(new TreeSet<Message>(Arrays.asList(m)), env.getMessages("project"));
        Assert.assertNotNull(env.getMessages("pull"));
        Assert.assertEquals(3, env.getMessages("pull").size());
        Assert.assertEquals(new TreeSet<Message>(Arrays.asList(m)), env.getMessages("pull"));
    }

    @Test
    public void testApplyMultipleUnique() {
        Message[] m = makeArray(new NormalMessage(), new NormalMessage(), new NormalMessage());

        for (int i = 0; i < m.length; i++) {
            m[i].addTag("main");
        }

        Assert.assertEquals(0, env.getMessagesInTheSystem());

        for (int i = 0; i < m.length; i++) {
            m[i].apply(env);
        }

        Assert.assertEquals(1, env.getMessagesInTheSystem());
        Assert.assertNotNull(env.getMessages());
        Assert.assertEquals(1, env.getMessages().size());
        Assert.assertNotNull(env.getMessages("main"));
        Assert.assertEquals(1, env.getMessages("main").size());
    }

}
