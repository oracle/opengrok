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
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.web.Statistics;

import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.initMessageListener;
import static org.opensolaris.opengrok.configuration.messages.MessageTestUtils.processMessage;

/**
 *
 * @author Krystof Tulinger
 */
public class StatsMessageTest {

    private RuntimeEnvironment env;

    private MessageListener listener;

    @Before
    public void setUp() throws Exception {
        env = RuntimeEnvironment.getInstance();
        listener = initMessageListener(env);
    }

    @Test
    public void testValidate() {
        Message.Builder<StatsMessage> builder = new Message.Builder<>(StatsMessage.class);
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.setText("text");
        Assert.assertFalse(MessageTest.assertValid(builder.build()));
        builder.setText("get");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setText("clean");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setText("reload");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setCssClass(null);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertNull(builder.build().getCssClass());
        builder.clearTags();
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertEquals(new TreeSet<>(), builder.build().getTags());
    }

    @Test
    public void testClean() throws Exception {
        Message m = new Message.Builder<>(StatsMessage.class)
                .setText("clean")
                .build();
        String out = processMessage(listener, m).getData().get(0);
        Assert.assertNotNull(out);
        Assert.assertTrue(out.length() > 0);
        Assert.assertEquals("{}", out);
    }

    @Test
    public void testGetClean() throws Exception {
        testClean();
        Message m = new Message.Builder<>(StatsMessage.class)
                .setText("get")
                .build();
        String out = processMessage(listener, m).getData().get(0);

        Assert.assertNotNull(out);
        Assert.assertTrue(out.length() > 0);
        Assert.assertEquals("{}", out);
    }

    @Test
    public void testGet() throws Exception {
        testClean();
        env.getStatistics().addRequest();
        Message m = new Message.Builder<>(StatsMessage.class)
                .setText("get")
                .build();
        String out = processMessage(listener, m).getData().get(0);

        Assert.assertNotNull(out);
        Assert.assertTrue(out.length() > 0);
        Assert.assertNotEquals("{}", out);
    }

    @Test
    public void testGetValidJson() throws Exception {
        testGet();

        Message m = new Message.Builder<>(StatsMessage.class)
                .setText("get")
                .build();

        String out = processMessage(listener, m).getData().get(0);

        JSONParser p = new JSONParser();
        Object o = p.parse(out);

        Assert.assertNotNull(o);

        Statistics stat = Statistics.from((JSONObject) o);

        Assert.assertNotNull(stat);
        Assert.assertEquals(1, stat.getRequests());
        Assert.assertEquals(1, stat.getMinutes());
        Assert.assertEquals(0, stat.getRequestCategories().size());
        Assert.assertEquals(0, stat.getTiming().size());
    }

}
