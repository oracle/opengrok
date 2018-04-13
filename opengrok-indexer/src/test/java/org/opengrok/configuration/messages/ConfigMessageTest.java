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

import java.io.IOException;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Test config message verification and handling.
 * 
 * @author Vladimir Kotal
 */
public class ConfigMessageTest {

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
        Message m = new ConfigMessage();
        Assert.assertFalse(MessageTest.assertValid(m));
        m.addTag("getconf");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setText("text");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText(null);
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("setconf");
        m.setText("text");
        m.setClassName(null);
        Assert.assertTrue(MessageTest.assertValid(m));
        Assert.assertNull(m.getClassName());
        m.setTags(new TreeSet<>());
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("foo");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("getconf");
        m.addTag("setconf");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("reindex");
        m.addTag("setconf");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.setText(null);
        m.addTag("set");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("set");
        m.setText("opt = 10");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("set");
        m.addTag("setconf");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("set");
        m.addTag("getconf");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setTags(new TreeSet<>());
        m.addTag("get");
        m.setText("sourceRoot");
        Assert.assertTrue(MessageTest.assertValid(m));
    }

    @Test
    public void testApplySetAndGetBasicConfig() throws Exception {
        Message m = new ConfigMessage();
        Configuration config = new Configuration();
        String srcRoot = "/foo";
        config.setSourceRoot(srcRoot);

        // Set the config.
        m.addTag("setconf");
        String configStr = config.getXMLRepresentationAsString();
        m.setText(configStr);
        m.apply(env);
        Assert.assertEquals(env.getSourceRootPath(), srcRoot);

        // Get the config.
        m.setTags(new TreeSet<>());
        m.setText(null);
        m.addTag("getconf");
        byte[] out = m.apply(env);
        String newconfStr = new String(out);
        Assert.assertEquals(configStr, newconfStr);
    }

    @Test(expected = IOException.class)
    public void testApplySetInvalidMethod() throws Exception {
        Message m = new ConfigMessage();
        m.setText("noMethodExists = 1000");
        m.addTag("set");
        m.apply(env);
    }

    @Test(expected = IOException.class)
    public void testApplyGetInvalidMethod() throws Exception {
        Message m = new ConfigMessage();
        m.setText("FooBar");
        m.addTag("get");
        m.apply(env);
    }
    
    @Test(expected = IOException.class)
    public void testApplySetInvalidMethodParameter() throws Exception {
        Message m = new ConfigMessage();
        m.setText("setDefaultProjects = 1000"); // expecting Set
        m.addTag("set");
        m.apply(env);
    }

    @Test
    public void testApplySetOptionInteger() throws Exception {
        Message m = new ConfigMessage();
        m.setText("hitsPerPage = 1000");
        m.addTag("set");
        Assert.assertEquals(25, env.getHitsPerPage());
        m.apply(env);
        Assert.assertEquals(1000, env.getHitsPerPage());
        env.setHitsPerPage(25);
    }

    @Test(expected = IOException.class)
    public void testApplySetOptionInvalidInteger() throws Exception {
        Message m = new ConfigMessage();
        m.setText("hitsPerPage = abcd");
        m.addTag("set");
        Assert.assertEquals(25, env.getHitsPerPage());
        m.apply(env);
        Assert.assertEquals(1000, env.getHitsPerPage());
        env.setHitsPerPage(25);
    }

    @Test
    public void testApplySetOptionBooleanTrue() throws Exception {
        Message m = new ConfigMessage();
        m.setText("chattyStatusPage = true");
        m.addTag("set");
        env.setChattyStatusPage(false);
        Assert.assertEquals(false, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(true, env.isChattyStatusPage());
        env.setChattyStatusPage(false);

        m.setText("chattyStatusPage = on");
        env.setChattyStatusPage(false);
        Assert.assertEquals(false, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(true, env.isChattyStatusPage());
        env.setChattyStatusPage(false);

        m.setText("chattyStatusPage = 1");
        env.setChattyStatusPage(false);
        Assert.assertEquals(false, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(true, env.isChattyStatusPage());
        env.setChattyStatusPage(false);
    }

    @Test
    public void testApplySetOptionBooleanFalse() throws Exception {
        Message m = new ConfigMessage();
        m.setText("chattyStatusPage = false");
        m.addTag("set");
        env.setChattyStatusPage(true);
        Assert.assertEquals(true, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(false, env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        m.setText("chattyStatusPage = off");
        env.setChattyStatusPage(true);
        Assert.assertEquals(true, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(false, env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        m.setText("chattyStatusPage = 0");
        env.setChattyStatusPage(true);
        Assert.assertEquals(true, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(false, env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        env.setChattyStatusPage(false);
    }

    @Test(expected = IOException.class)
    public void testApplySetOptionInvalidBoolean1() throws Exception {
        Message m = new ConfigMessage();
        m.addTag("set");

        m.setText("chattyStatusPage = 1000"); // only 1 is accepted as true
        env.setChattyStatusPage(true);
        Assert.assertEquals(true, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(false, env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        env.setChattyStatusPage(false);
    }

    @Test(expected = IOException.class)
    public void testApplySetOptionInvalidBoolean2() throws Exception {
        Message m = new ConfigMessage();
        m.addTag("set");

        m.setText("chattyStatusPage = anything"); // fallback to false
        env.setChattyStatusPage(true);
        Assert.assertEquals(true, env.isChattyStatusPage());
        m.apply(env);
        Assert.assertEquals(false, env.isChattyStatusPage());
        env.setChattyStatusPage(true);

        env.setChattyStatusPage(false);
    }

    @Test
    public void testApplySetOptionString() throws Exception {
        String old = env.getUserPage();
        Message m = new ConfigMessage();
        m.addTag("set");

        m.setText("userPage = http://users.portal.com?user=");
        m.apply(env);
        Assert.assertEquals("http://users.portal.com?user=", env.getUserPage());

        m.setText("userPage = some complicated \"string\" with &#~Đ`[đ\\ characters");
        m.apply(env);
        Assert.assertEquals("some complicated \"string\" with &#~Đ`[đ\\ characters", env.getUserPage());

        env.setUserPage(old);
    }

    @Test
    public void testApplyGetOptionString() throws Exception {
        env.setSourceRoot("/foo/bar");
        Message m = new ConfigMessage();
        m.setText("sourceRoot");
        m.addTag("get");
        String val = new String(m.apply(env));
        Assert.assertEquals(val, env.getConfiguration().getSourceRoot());
    }
    
    @Test
    public void testApplyGetOptionInteger() throws Exception {
        Message m = new ConfigMessage();
        m.setText("hitsPerPage");
        m.addTag("get");
        String val = new String(m.apply(env));
        Assert.assertEquals(val, Integer.toString(env.getHitsPerPage()));
    }
    
    @Test
    public void testApplyGetOptionBoolean() throws Exception {
        Message m = new ConfigMessage();
        m.setText("historyCache");
        m.addTag("get");
        String val = new String(m.apply(env));
        Assert.assertEquals(val, Boolean.toString(env.getConfiguration().isHistoryCache()));
    }
}
