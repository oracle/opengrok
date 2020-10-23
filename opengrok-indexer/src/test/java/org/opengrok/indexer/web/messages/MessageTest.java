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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.opengrok.indexer.web.messages.JSONUtils.getTopLevelJSONFields;

public class MessageTest {

    @Test(expected = IllegalArgumentException.class)
    public void createBadMessageTest() {
        new Message(null, null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadMessageTest2() {
        new Message("", null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadMessageTest3() {
        new Message("test", null, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadMessageTest4() {
        new Message("test", Collections.emptySet(), null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadMessageTest5() {
        new Message("test", Collections.singleton("test"), null, Duration.ofMinutes(-1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadMessageTest6() {
        new Message("test", Collections.emptySet(), null, Duration.ofMinutes(1));
    }

    @Test
    public void messageToJSON() throws IOException {
        Message m = new Message("test",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(1));
        String jsonString = m.toJSON();
        assertEquals(new HashSet<>(Arrays.asList("messageLevel", "duration", "text", "tags")),
                getTopLevelJSONFields(jsonString));
    }
}
