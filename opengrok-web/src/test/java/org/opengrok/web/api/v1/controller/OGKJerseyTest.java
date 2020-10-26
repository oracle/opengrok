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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Before;

import java.util.Random;

/**
 * Represents a subclass of {@link JerseyTest} customized for OpenGrok.
 */
public abstract class OGKJerseyTest extends JerseyTest {

    private static final int BASE_DYNAMIC_OR_PRIVATE_PORT = 49152;

    /** Random.nextInt() will be at most one less than this -- but OK. */
    private static final int DYNAMIC_OR_PRIVATE_PORT_RANGE = 16383;

    private static final int MAX_PORT_TRIES = 20;

    private final Random rand = new Random();

    /**
     * Marshal a random high port through {@link TestProperties#CONTAINER_PORT}
     * for use by {@link #getPort()}.
     */
    @Before
    public void setUp() throws Exception {

        int triesCount = 0;
        while (true) {
            int jerseyPort = BASE_DYNAMIC_OR_PRIVATE_PORT +
                    rand.nextInt(DYNAMIC_OR_PRIVATE_PORT_RANGE);
            if (PortChecker.available(jerseyPort)) {
                forceSet(TestProperties.CONTAINER_PORT, String.valueOf(jerseyPort));
                break;
            }
            if (++triesCount > MAX_PORT_TRIES) {
                throw new RuntimeException("Could not find an available port after " +
                        MAX_PORT_TRIES + " tries");
            }
        }

        super.setUp();
    }
}
