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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web.messages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opengrok.indexer.web.messages.JSONUtils.getTopLevelJSONFields;

class MessageTest {

    @ParameterizedTest
    @MethodSource("messageParams")
    void createBadMessageTest(
            final String text,
            final Set<String> tags,
            final Message.MessageLevel messageLevel,
            final Duration duration) {
        assertThrows(IllegalArgumentException.class, () -> new Message(text, tags, messageLevel, duration));
    }

    @Test
    void messageToJSON() throws IOException {
        Message m = new Message("test",
                Collections.singleton("test"),
                Message.MessageLevel.INFO,
                Duration.ofMinutes(1));
        String jsonString = m.toJSON();
        assertEquals(Set.of("messageLevel", "duration", "text", "tags"), getTopLevelJSONFields(jsonString));
    }


    private static Stream<Arguments> messageParams() {
        return Stream.of(
                Arguments.of(null, null, null, null),
                Arguments.of("", null, null, null),
                Arguments.of("test", null, null, null),
                Arguments.of("test", Collections.emptySet(), null, null),
                Arguments.of("test", Collections.singleton("test"), null, Duration.ofMinutes(-1)),
                Arguments.of("test", Collections.emptySet(), null, Duration.ofMinutes(1))
        );

    }
}
