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
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages;

import java.util.TreeSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 *
 * @author Vladimir Kotal
 */
public class RefreshMessageTest {

    private MessageListener listener;

    @Before
    public void setUp() throws Exception {
        listener = MessageTestUtils.initMessageListener(RuntimeEnvironment.getInstance());
        listener.removeAllMessages();
    }

    @After
    public void tearDown() {
        listener.removeAllMessages();
        listener.stopConfigurationListenerThread();
    }

    @Test
    public void testValidate() {
        Message.Builder<RefreshMessage> builder = new Message.Builder<>(RefreshMessage.class);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setText("text");
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        builder.setCssClass(null);
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertNull(builder.build().getCssClass());
        builder.clearTags();
        Assert.assertTrue(MessageTest.assertValid(builder.build()));
        Assert.assertEquals(new TreeSet<>(), builder.build().getTags());
    }
}
