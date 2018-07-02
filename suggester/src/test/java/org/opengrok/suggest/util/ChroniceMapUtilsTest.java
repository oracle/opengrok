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
package org.opengrok.suggest.util;

import net.openhft.chronicle.map.ChronicleMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class ChroniceMapUtilsTest {

    private static final String FIELD = "test";

    private ChronicleMap<String, Integer> map;

    private Path tempFile;

    @Before
    public void setUp() throws IOException {
        tempFile = Files.createTempFile("opengrok", "test");

        map = ChronicleMap.of(String.class, Integer.class)
                .name(FIELD)
                .averageKey("avg")
                .entries(10)
                .createOrRecoverPersistedTo(tempFile.toFile());
    }

    @After
    public void tearDown() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    public void dataNotLostAfterResizeTest() throws IOException {
        fillData(0, 10, map);
        ChronicleMap<String, Integer> newMap = ChronicleMapUtils.resize(tempFile.toFile(), map, 20, 20);
        checkData(10, newMap);
    }

    private void fillData(int start, int end, ChronicleMap<String, Integer> map) {
        for (int i = start; i < end; i++) {
            map.put("" + i, i);
        }
    }

    private void checkData(int count, ChronicleMap<String, Integer> map) {
        for (int i = 0; i < count; i++) {
            assertEquals(Integer.valueOf(i), map.get("" + i));
        }
    }

    @Test
    public void testResize() throws IOException {
        fillData(0, 10, map);
        ChronicleMap<String, Integer> newMap = ChronicleMapUtils.resize(tempFile.toFile(), map, 500, 20);

        fillData(10, 500, newMap);

        checkData(500, newMap);
    }

}
