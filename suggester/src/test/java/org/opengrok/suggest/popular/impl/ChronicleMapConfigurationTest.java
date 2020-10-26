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
package org.opengrok.suggest.popular.impl;

import org.junit.Test;
import org.opengrok.suggest.popular.impl.chronicle.ChronicleMapConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class ChronicleMapConfigurationTest {

    @Test
    public void saveLoadTest() throws IOException {
        ChronicleMapConfiguration conf = new ChronicleMapConfiguration(20, 10);

        Path dir = Files.createTempDirectory("opengrok");

        conf.save(dir, "test");

        conf = ChronicleMapConfiguration.load(dir, "test");

        assertEquals(20, conf.getEntries());
        assertEquals(10, conf.getAverageKeySize(), 0.01);
    }

}
