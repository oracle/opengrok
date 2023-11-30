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
package org.opengrok.indexer.index;

import org.opengrok.indexer.analysis.AccumulatedNumLinesLOC;
import org.opengrok.indexer.analysis.NumLinesLOC;
import org.opengrok.indexer.web.Util;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents an accumulator of net-deltas of #Lines and LOC for directories.
 */
public class NumLinesLOCAggregator {
    private final Object syncRoot = new Object();

    private final HashMap<String, DeltaData> registeredDeltas = new HashMap<>();

    /**
     * Gets an iterator over all registered data, explicit and derived, and not
     * ordered.
     * @return a defined instance
     */
    public Iterator<AccumulatedNumLinesLOC> iterator() {
        return new AccumulationsIterator(registeredDeltas.entrySet().iterator());
    }

    /**
     * Registers the specified counts. Values should be negative when deleting a
     * file or when updating a file's analysis to reverse former values before
     * re-registering.
     * @param counts {@link NumLinesLOC} instance
     */
    public void register(NumLinesLOC counts) {
        File file = new File(counts.getPath());
        File directory = file.getParentFile();
        if (directory != null) {
            synchronized (syncRoot) {
                do {
                    String dirPath = Util.fixPathIfWindows(directory.getPath());
                    var extantDelta = registeredDeltas.computeIfAbsent(dirPath,
                            key -> new DeltaData());
                    extantDelta.numLines += counts.getNumLines();
                    extantDelta.loc += counts.getLOC();
                } while ((directory = directory.getParentFile()) != null &&
                        !directory.getPath().isEmpty());
            }
        }
    }

    private static class DeltaData {
        long numLines;
        long loc;
    }

    private static class AccumulationsIterator implements Iterator<AccumulatedNumLinesLOC> {
        private final Iterator<Map.Entry<String, DeltaData>> underlying;

        AccumulationsIterator(Iterator<Map.Entry<String, DeltaData>> underlying) {
            this.underlying = underlying;
        }

        @Override
        public boolean hasNext() {
            return underlying.hasNext();
        }

        @Override
        public AccumulatedNumLinesLOC next() {
            Map.Entry<String, DeltaData> underlyingNext = underlying.next();
            String path = underlyingNext.getKey();
            DeltaData values = underlyingNext.getValue();
            return new AccumulatedNumLinesLOCImpl(path, values.numLines, values.loc);
        }
    }

    private static class AccumulatedNumLinesLOCImpl implements AccumulatedNumLinesLOC {

        private final String path;
        private final long numLines;
        private final long loc;

        AccumulatedNumLinesLOCImpl(String path, long numLines, long loc) {
            this.path = path;
            this.numLines = numLines;
            this.loc = loc;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public long getNumLines() {
            return numLines;
        }

        @Override
        public long getLOC() {
            return loc;
        }
    }
}
