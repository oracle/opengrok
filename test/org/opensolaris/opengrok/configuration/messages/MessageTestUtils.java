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
package org.opensolaris.opengrok.configuration.messages;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.MessageListener.AcceptedMessage;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

public class MessageTestUtils {

    private MessageTestUtils() {
    }

    public static void expire(final AcceptedMessage message) throws NoSuchFieldException, IllegalAccessException {
        expire(message.getMessage());
    }

    public static void expire(final Message message) throws NoSuchFieldException, IllegalAccessException {
        Field f = Message.class.getDeclaredField("duration");
        f.setAccessible(true);
        f.set(message, Duration.ofMillis(-1));
    }

    public static MessageListener initMessageListener(final RuntimeEnvironment env) throws Exception {
        MessageListener listener = new MessageListener();
        listener.setMessageLimit(env.getConfiguration().getMessageLimit());

        Field f = RuntimeEnvironment.class.getDeclaredField("messageListener");
        f.setAccessible(true);
        f.set(env, listener);

        Method m = RuntimeEnvironment.class.getDeclaredMethod("addDefaultMessageHandlers");
        m.setAccessible(true);
        m.invoke(env);

        return listener;
    }

    public static MessageListener getMessageListener(final RuntimeEnvironment env) throws Exception {
        Field f = RuntimeEnvironment.class.getDeclaredField("messageListener");
        f.setAccessible(true);
        return (MessageListener) f.get(env);
    }

    public static Response processMessage(final MessageListener listener, final Message message)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = MessageListener.class.getDeclaredMethod("processMessage", Message.class);
        m.setAccessible(true);
        return (Response) m.invoke(listener, message);
    }

    public static Message getTestProjectMessage() {
        return new Message.Builder<>(ProjectMessage.class)
                .setText("add")
                .addTag("project1")
                .addTag("project2")
                .build();
    }

    public static Message getTestConfigMessage() {
        return new Message.Builder<>(ConfigMessage.class)
                .addTag("getconf")
                .build();
    }

}
