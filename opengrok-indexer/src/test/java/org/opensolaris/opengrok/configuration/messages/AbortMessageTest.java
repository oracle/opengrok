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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

public class AbortMessageTest {

    RuntimeEnvironment env;

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
    public void testValidate() {
        Message m = new AbortMessage();
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setText("text");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setClassName(null);
        Assert.assertTrue(MessageTest.assertValid(m));
        Assert.assertNull(m.getClassName());
        m.setTags(new TreeSet<>());
        Assert.assertTrue(MessageTest.assertValid(m));
        Assert.assertTrue(m.hasTag(RuntimeEnvironment.MESSAGES_MAIN_PAGE_TAG));
    }

    @Test
    public void testApplyNoTag() throws Exception {
        Message m = new AbortMessage();

        Assert.assertEquals(0, env.getMessagesInTheSystem());
        m.apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplyNoTagEmpty() throws Exception {
        Message m = new AbortMessage();
        m.addTag("main");

        Assert.assertEquals(0, env.getMessagesInTheSystem());
        m.apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplyNoTagFull() throws Exception {
        Message m = new NormalMessage().addTag("main");
        m.setText("text");
        m.apply(env);
        Assert.assertEquals(1, env.getMessagesInTheSystem());
        new AbortMessage().apply(env);
        // the main tag is added by default if no tag is present
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplySingle() throws Exception {
        Message m = new NormalMessage().addTag("main");
        m.setText("text");
        m.apply(env);
        Assert.assertEquals(1, env.getMessagesInTheSystem());
        new AbortMessage().addTag("main").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplySingleWrongTag() throws Exception {
        Message m = new NormalMessage().addTag("main");
        m.setText("text");
        m.apply(env);
        Assert.assertEquals(1, env.getMessagesInTheSystem());
        new AbortMessage().addTag("other").apply(env);
        Assert.assertEquals(1, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplyReverse() throws Exception {
        new AbortMessage().addTag("main").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        Message m = new NormalMessage().addTag("main");
        m.setText("text");
        m.apply(env);
        Assert.assertEquals(1, env.getMessagesInTheSystem());
    }

    @Test
    public void testApplyMultiple() throws Exception {
        Message m = new NormalMessage().addTag("main");
        m.setText("text");
        m.apply(env);
        m = new NormalMessage().addTag("project");
        m.setText("text");
        m.apply(env);
        m = new NormalMessage().addTag("pull");
        m.setText("text");
        m.apply(env);
        Assert.assertEquals(3, env.getMessagesInTheSystem());

        new AbortMessage().addTag("other").apply(env);
        Assert.assertEquals(3, env.getMessagesInTheSystem());
        new AbortMessage().addTag("other pro").apply(env);
        Assert.assertEquals(3, env.getMessagesInTheSystem());
        new AbortMessage().addTag("main").apply(env);
        Assert.assertEquals(2, env.getMessagesInTheSystem());
        new AbortMessage().addTag("main").apply(env);
        Assert.assertEquals(2, env.getMessagesInTheSystem());
        new AbortMessage().addTag("main").addTag("other").apply(env);
        Assert.assertEquals(2, env.getMessagesInTheSystem());
        new AbortMessage().addTag("pull").addTag("project").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        new AbortMessage().addTag("pull").addTag("project").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        new AbortMessage().addTag("main").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        new AbortMessage().addTag("pull").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        new AbortMessage().addTag("project").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
        new AbortMessage().addTag("other").apply(env);
        Assert.assertEquals(0, env.getMessagesInTheSystem());
    }
}
