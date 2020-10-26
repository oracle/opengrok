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
package org.opengrok.suggest.popular.impl.chronicle;

import net.openhft.chronicle.map.ChronicleMap;
import org.apache.lucene.util.BytesRef;
import org.opengrok.suggest.popular.PopularityMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;

/**
 * Adapter for {@link ChronicleMap} to expose only the necessary operations needed for most popular completion.
 */
public class ChronicleMapAdapter implements PopularityMap {

    private ChronicleMap<BytesRef, Integer> map;

    private final File chronicleMapFile;

    public ChronicleMapAdapter(final String name, final double averageKeySize, final int entries, final File file)
            throws IOException {
        map = ChronicleMap.of(BytesRef.class, Integer.class)
                .name(name)
                .averageKeySize(averageKeySize)
                .keyReaderAndDataAccess(BytesRefSizedReader.INSTANCE, new BytesRefDataAccess())
                .entries(entries)
                .createOrRecoverPersistedTo(file);
        this.chronicleMapFile = file;
    }

    /** {@inheritDoc} */
    @Override
    public int get(final BytesRef key) {
        return map.getOrDefault(key, 0);
    }

    /** {@inheritDoc} */
    @Override
    public void increment(final BytesRef key, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot increment by negative value " + value);
        }
        map.merge(key, value, Integer::sum);
    }

    /** {@inheritDoc} */
    @Override
    public List<Entry<BytesRef, Integer>> getPopularityData(final int page, final int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("Cannot retrieve popularity data for negative page: " + page);
        }
        if (pageSize < 0) {
            throw new IllegalArgumentException("Cannot retrieve negative number of results: " + pageSize);
        }

        List<Entry<BytesRef, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort(Entry.<BytesRef, Integer>comparingByValue().reversed());

        int startIndex = page * pageSize;
        if (startIndex >= list.size()) {
            return Collections.emptyList();
        }
        int endIndex = startIndex + pageSize;
        if (endIndex > list.size()) {
            endIndex = list.size();
        }

        return list.subList(startIndex, endIndex);
    }

    /**
     * Removes the entries with key that meets the predicate.
     * @param predicate predicate which tests which entries should be removed
     */
    public void removeIf(final Predicate<BytesRef> predicate) {
        map.entrySet().removeIf(e -> predicate.test(e.getKey()));
    }

    /**
     * Resizes the underlying {@link ChronicleMap}.
     * @param newMapSize new entries count
     * @param newMapAvgKey new average key size
     * @throws IOException if some error occurred
     */
    public void resize(final int newMapSize, final double newMapAvgKey) throws IOException {
        if (newMapSize < 0) {
            throw new IllegalArgumentException("Cannot resize chronicle map to negative size");
        }
        if (newMapAvgKey < 0) {
            throw new IllegalArgumentException("Cannot resize chronicle map to map with negative key size");
        }

        Path tempFile = Files.createTempFile("opengrok", "chronicle");

        try {
            map.getAll(tempFile.toFile());

            String field = map.name();

            map.close();

            Files.delete(chronicleMapFile.toPath());

            ChronicleMap<BytesRef, Integer> m = ChronicleMap.of(BytesRef.class, Integer.class)
                    .name(field)
                    .averageKeySize(newMapAvgKey)
                    .entries(newMapSize)
                    .keyReaderAndDataAccess(BytesRefSizedReader.INSTANCE, new BytesRefDataAccess())
                    .createOrRecoverPersistedTo(chronicleMapFile);
            m.putAll(tempFile.toFile());
            map = m;
        } finally {
            Files.delete(tempFile);
        }
    }

    /**
     * Closes the opened {@link ChronicleMap}.
     */
    @Override
    public void close() {
        map.close();
    }

}
