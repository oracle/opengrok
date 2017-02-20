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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.beans.XMLEncoder;
import java.io.ByteArrayOutputStream;
import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
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
}
