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
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.Statistics;

/**
 *
 * @author Krystof Tulinger
 */
public class StatsMessageTest {

    RuntimeEnvironment env;

    @Before
    public void setUp() {
        env = RuntimeEnvironment.getInstance();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testValidate() {
        Message m = new StatsMessage();
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText("text");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText("get");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setText("clean");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setText("reload");
        Assert.assertTrue(MessageTest.assertValid(m));
        m.setClassName(null);
        Assert.assertTrue(MessageTest.assertValid(m));
        Assert.assertNull(m.getClassName());
        m.setTags(new TreeSet<>());
        Assert.assertTrue(MessageTest.assertValid(m));
        Assert.assertEquals(new TreeSet<>(), m.getTags());
    }

    @Test
    public void testClean() {
        Message m = new StatsMessage();
        m.setText("clean");
        byte[] out = null;
        try {
            out = m.apply(env);
        } catch (Exception ex) {
            Assert.fail("Should not throw any exception");
        }
        Assert.assertNotNull(out);
        Assert.assertTrue(out.length > 0);
        Assert.assertEquals("{}", new String(out));
    }

    @Test
    public void testGetClean() {
        testClean();
        Message m = new StatsMessage();
        m.setText("get");
        byte[] out = null;
        try {
            out = m.apply(env);
        } catch (Exception ex) {
            Assert.fail("Should not throw any exception");
        }
        Assert.assertNotNull(out);
        Assert.assertTrue(out.length > 0);
        Assert.assertEquals("{}", new String(out));
    }

    @Test
    public void testGet() {
        testClean();
        env.getStatistics().addRequest();
        Message m = new StatsMessage();
        m.setText("get");
        byte[] out = null;
        try {
            out = m.apply(env);
        } catch (Exception ex) {
            Assert.fail("Should not throw any exception");
        }
        Assert.assertNotNull(out);
        Assert.assertTrue(out.length > 0);
        Assert.assertNotEquals("{}", new String(out));
    }

    @Test
    public void testGetValidJson() {
        testGet();

        Message m = new StatsMessage();
        m.setText("get");

        byte[] out = null;
        try {
            out = m.apply(env);
        } catch (Exception ex) {
            Assert.fail("Should not throw any exception");
        }

        JSONParser p = new JSONParser();
        Object o = null;
        try {
            o = p.parse(new String(out));
        } catch (ParseException ex) {
            Assert.fail("Should not throw any exception");
        }
        Assert.assertNotNull(o);

        Statistics stat = Statistics.from((JSONObject) o);

        Assert.assertTrue(stat instanceof Statistics);
        Assert.assertEquals(1, stat.getRequests());
        Assert.assertEquals(1, stat.getMinutes());
        Assert.assertEquals(0, stat.getRequestCategories().size());
        Assert.assertEquals(0, stat.getTiming().size());
    }

    @Test
    public void testInvalidReload() {
        Message m = new StatsMessage();
        m.setText("reload");
        env.getConfiguration().setStatisticsFilePath("/file/that/doesnot/exists");

        try {
            m.apply(env);
            Assert.fail("Should throw an exception");
        } catch (Exception ex) {
        }
    }
}
