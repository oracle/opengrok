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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;

/**
 * Test config message verification and handling.
 * 
 * @author Vladimir Kotal
 */
public class ConfigMessageTest {

    private RuntimeEnvironment env;

    private MessageListener listener;

    @Before
    public void setUp() throws Exception {
        env = RuntimeEnvironment.getInstance();
        listener = MessageTestUtils.initMessageListener(env);
        listener.removeAllMessages();
    }

    @After
    public void tearDown() {
        listener.removeAllMessages();
    }

    @Test
    public void testValidate() {
        Message.Builder<ConfigMessage> builder = new Message.Builder<>(ConfigMessage.class);
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.addTag("getconf");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setText("text");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.setText(null);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("setconf");
        builder.setText("text");
        builder.setCssClass(null);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertNull(builder.build().getCssClass());
        builder.clearTags();
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("foo");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("getconf");
        builder.addTag("setconf");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("reindex");
        builder.addTag("setconf");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.setText(null);
        builder.addTag("set");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("set");
        builder.setText("opt = 10");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("set");
        builder.addTag("setconf");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("set");
        builder.addTag("getconf");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.clearTags();
        builder.addTag("get");
        builder.setText("sourceRoot");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
    }

    @Test
    public void testApplySetAndGetBasicConfig() throws Exception {
        Configuration config = new Configuration();
        String srcRoot = "/foo";
        config.setSourceRoot(srcRoot);

        Message.Builder<ConfigMessage> builder = new Message.Builder<>(ConfigMessage.class);

        // Set the config.
        builder.addTag("setconf");
        String configStr = config.getXMLRepresentationAsString();
        builder.setText(configStr);
        processMessage(listener, builder.build());
        Assert.assertEquals(env.getSourceRootPath(), srcRoot);

        // Get the config.
        builder.clearTags();
        builder.setText(null);
        builder.addTag("getconf");

        Response response = processMessage(listener, builder.build());
        String newconfStr = response.getData().get(0);
        Assert.assertEquals(configStr, newconfStr);
    }

    @Test
    public void testApplySetOptionInteger() throws Exception {
        Assert.assertEquals(25, env.getHitsPerPage());

        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("hitsPerPage = 1000")
                .addTag("set")
                .build();

        processMessage(listener, m);

        Assert.assertEquals(1000, env.getHitsPerPage());
        env.setHitsPerPage(25);
    }

    @Test
    public void testApplySetOptionInvalidInteger() throws Exception {
        Assert.assertEquals(25, env.getHitsPerPage());

        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("hitsPerPage = abcd")
                .addTag("set")
                .build();
        processMessage(listener, m);

        Assert.assertEquals(25, env.getHitsPerPage());
    }

    @Test
    public void testApplySetOptionBooleanTrue() throws Exception {
        Message.Builder builder = new Message.Builder<>(ConfigMessage.class)
                .setText("chattyStatusPage = true")
                .addTag("set");

        env.setChattyStatusPage(false);
        Assert.assertFalse(env.isChattyStatusPage());
        processMessage(listener, builder.build());
        Assert.assertTrue(env.isChattyStatusPage());
        env.setChattyStatusPage(false);

        builder.setText("chattyStatusPage = on");
        env.setChattyStatusPage(false);
        Assert.assertFalse(env.isChattyStatusPage());
        processMessage(listener, builder.build());
        Assert.assertTrue(env.isChattyStatusPage());
        env.setChattyStatusPage(false);

        builder.setText("chattyStatusPage = 1");
        env.setChattyStatusPage(false);
        Assert.assertFalse(env.isChattyStatusPage());
        processMessage(listener, builder.build());
        Assert.assertTrue(env.isChattyStatusPage());
        env.setChattyStatusPage(false);
    }

    @Test
    public void testApplySetOptionBooleanFalse() throws Exception {
        Message.Builder builder = new Message.Builder<>(ConfigMessage.class)
                .setText("chattyStatusPage = false")
                .addTag("set");
        env.setChattyStatusPage(true);
        Assert.assertTrue(env.isChattyStatusPage());
        processMessage(listener, builder.build());
        Assert.assertFalse(env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        builder.setText("chattyStatusPage = off");
        env.setChattyStatusPage(true);
        Assert.assertTrue(env.isChattyStatusPage());
        processMessage(listener, builder.build());
        Assert.assertFalse(env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        builder.setText("chattyStatusPage = 0");
        env.setChattyStatusPage(true);
        Assert.assertTrue(env.isChattyStatusPage());
        processMessage(listener, builder.build());
        Assert.assertFalse(env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        env.setChattyStatusPage(false);
    }

    @Test
    public void testApplySetOptionString() throws Exception {
        String old = env.getUserPage();

        Message.Builder builder = new Message.Builder<>(ConfigMessage.class)
                .addTag("set")
                .setText("userPage = http://users.portal.com?user=");

        processMessage(listener, builder.build());
        Assert.assertEquals("http://users.portal.com?user=", env.getUserPage());

        builder.setText("userPage = some complicated \"string\" with &#~Đ`[đ\\ characters");
        processMessage(listener, builder.build());
        Assert.assertEquals("some complicated \"string\" with &#~Đ`[đ\\ characters", env.getUserPage());

        env.setUserPage(old);
    }

    @Test
    public void testApplyGetOptionString() throws Exception {
        env.setSourceRoot("/foo/bar");
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("sourceRoot")
                .addTag("get")
                .build();

        Response response = processMessage(listener, m);

        String val = response.getData().get(0);
        Assert.assertEquals(val, env.getConfiguration().getSourceRoot());
    }
    
    @Test
    public void testApplyGetOptionInteger() throws Exception {
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("hitsPerPage")
                .addTag("get")
                .build();

        Response response = processMessage(listener, m);

        String val = response.getData().get(0);
        Assert.assertEquals(val, Integer.toString(env.getHitsPerPage()));
    }
    
    @Test
    public void testApplyGetOptionBoolean() throws Exception {
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("historyCache")
                .addTag("get")
                .build();

        Response response = processMessage(listener, m);

        String val = response.getData().get(0);
        Assert.assertEquals(val, Boolean.toString(env.getConfiguration().isHistoryCache()));
    }
}
