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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.history;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a container for {@link History}-related utility methods.
 */
public class HistoryUtil {

    /**
     * Initialize an instance as the union of a sequence and closes the
     * sequence to affirm a zero exit value.
     * @param sequence a defined sequence
     * @throws HistoryException if the sequence fails to enumerate or the
     * subprocess returns non-zero
     */
    public static History union(HistoryEnumeration sequence) throws HistoryException {

        List<HistoryEntry> entries = new ArrayList<>();
        List<String> renamedFiles = new ArrayList<>();

        while (sequence.hasMoreElements()) {
            History that = sequence.nextElement();
            entries.addAll(that.getHistoryEntries());
            renamedFiles.addAll(that.getRenamedFiles());
        }

        try {
            sequence.close();
        } catch (IOException e) {
            throw new HistoryException("Error closing sequence", e);
        }

        if (sequence.exitValue() != 0) {
            throw new HistoryException("HistoryEnumeration exit value=" + sequence.exitValue());
        }

        return new History(entries, renamedFiles);
    }

    /* private to enforce static */
    private HistoryUtil() {
    }
}
