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
import org.apache.lucene.util.BytesRef;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChronicleMapUtils {

    private ChronicleMapUtils() {
    }

    public static ChronicleMap<BytesRef, Integer> resize(
            final File oldMapFile,
            final ChronicleMap<BytesRef, Integer> oldMap,
            final int newMapSize,
            final double newMapAvgKey
    ) throws IOException {
        if (oldMapFile == null || !oldMapFile.exists()) {
            throw new IllegalArgumentException("Cannot resize chronicle map because of invalid old map file");
        }
        if (oldMap == null) {
            throw new IllegalArgumentException("Cannot resize null chronicle map");
        }
        if (newMapSize < 0) {
            throw new IllegalArgumentException("Cannot resize chronicle map to negative size");
        }
        if (newMapAvgKey < 0) {
            throw new IllegalArgumentException("Cannot resize chronicle map to map with negative key size");
        }

        Path tempFile = Files.createTempFile("opengrok", "chronicle");

        oldMap.getAll(tempFile.toFile());

        String field = oldMap.name();

        oldMap.close();

        Files.delete(oldMapFile.toPath());

        ChronicleMap<BytesRef, Integer> m = ChronicleMap.of(BytesRef.class, Integer.class)
                .name(field)
                .averageKeySize(newMapAvgKey)
                .entries(newMapSize)
                .keyReaderAndDataAccess(BytesRefSizedReader.INSTANCE, new BytesRefDataAccess())
                .createOrRecoverPersistedTo(oldMapFile);

        m.putAll(tempFile.toFile());

        Files.delete(tempFile);

        return m;
    }

}
