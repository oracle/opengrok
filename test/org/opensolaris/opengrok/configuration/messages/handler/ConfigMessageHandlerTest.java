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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages.handler;

import org.junit.Before;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.ConfigMessage;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.configuration.messages.MessageHandler;

import static org.junit.Assert.assertTrue;

public class ConfigMessageHandlerTest {

    private RuntimeEnvironment env;

    private ConfigMessageHandler handler;

    @Before
    public void setup() {
        env = RuntimeEnvironment.getInstance();
        handler = new ConfigMessageHandler(env);
    }

    @Test(expected = MessageHandler.HandleException.class)
    public void testApplySetOptionInvalidInteger() throws Exception {
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("hitsPerPage = abcd")
                .addTag("set")
                .build();
        handler.handle(m);
    }

    @Test(expected = MessageHandler.HandleException.class)
    public void testApplySetInvalidMethod() throws Exception {
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("noMethodExists = 1000")
                .addTag("set")
                .build();
        handler.handle(m);
    }

    @Test(expected = MessageHandler.HandleException.class)
    public void testApplyGetInvalidMethod() throws Exception {
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("FooBar")
                .addTag("get")
                .build();
        handler.handle(m);
    }

    @Test(expected = MessageHandler.HandleException.class)
    public void testApplySetInvalidMethodParameter() throws Exception {
        Message m = new Message.Builder<>(ConfigMessage.class)
                .setText("setDefaultProjects = 1000")
                .addTag("set")
                .build();
        handler.handle(m);
    }

    @Test(expected = MessageHandler.HandleException.class)
    public void testApplySetOptionInvalidBoolean1() throws Exception {
        env.setChattyStatusPage(true);
        assertTrue(env.isChattyStatusPage());
        Message m = new Message.Builder<>(ConfigMessage.class)
                .addTag("set")
                .setText("chattyStatusPage = 1000") // only 1 is accepted as true
                .build();
        handler.handle(m);
    }

    @Test(expected = MessageHandler.HandleException.class)
    public void testApplySetOptionInvalidBoolean2() throws Exception {
        env.setChattyStatusPage(true);
        assertTrue(env.isChattyStatusPage());
        Message m = new Message.Builder<>(ConfigMessage.class)
                .addTag("set")
                .setText("chattyStatusPage = anything") // fallback to false
                .build();
        handler.handle(m);
    }

}
