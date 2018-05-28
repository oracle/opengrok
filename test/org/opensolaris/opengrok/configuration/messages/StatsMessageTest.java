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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.stats.report.JsonStatisticsReporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Krystof Tulinger
 */
public class StatsMessageTest {

    private RuntimeEnvironment env;

    @Before
    public void setUp() {
        env = RuntimeEnvironment.getInstance();
    }

    @Test
    public void testValidate() {
        Message m = new StatsMessage();
        assertFalse(MessageTest.assertValid(m));
        m.setText("text");
        assertFalse(MessageTest.assertValid(m));
        m.setText("get");
        assertTrue(MessageTest.assertValid(m));
        m.setText("clean");
        assertTrue(MessageTest.assertValid(m));
        m.setText("reload");
        assertTrue(MessageTest.assertValid(m));
        m.setClassName(null);
        assertTrue(MessageTest.assertValid(m));
        assertNull(m.getClassName());
        m.setTags(new TreeSet<>());
        assertTrue(MessageTest.assertValid(m));
        assertEquals(new TreeSet<>(), m.getTags());
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
        assertNotNull(out);
        assertTrue(out.length > 0);
        assertEquals("{}", new String(out));
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
        assertNotNull(out);
        assertTrue(out.length > 0);
        assertEquals("{}", new String(out));
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
        assertNotNull(out);
        assertTrue(out.length > 0);
        assertNotEquals("{}", new String(out));
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

        String output = new String(out);
        assertEquals(new JsonStatisticsReporter().report(RuntimeEnvironment.getInstance().getStatistics()), output);
    }

    @Test(expected = Exception.class)
    public void testInvalidReload() throws Exception {
        Message m = new StatsMessage();
        m.setText("reload");
        env.getConfiguration().setStatisticsFilePath("/file/that/doesnot/exists");

        m.apply(env);
    }
}
