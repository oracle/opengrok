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
import java.util.NoSuchElementException;

/**
 * Represents a filtering of a history sequence up to but not including a
 * specified revision identifier. For {@link Repository} implementations that
 * do not yet support a partial history, then a simple, but expensive,
 * filtering of the full history is done.
 * <p>N.b. the underlying sequence is fully exhausted during the filtering.
 */
class FilteredHistoryEnumeration implements HistoryEnumeration {

    private final HistoryEnumeration underlying;
    private final String sinceRevision;
    private boolean didMatchRevision;

    /**
     * Initializes an instance from the specified sequence.
     * @param historySequence a defined sequence ordered from most recent to
     * earlier between each element and within each element
     * @param sinceRevision the revision at which to stop (non-inclusive)
     * returning data, or {@code null} to return the full sequence
     */
    FilteredHistoryEnumeration(HistoryEnumeration historySequence, String sinceRevision) {
        this.underlying = historySequence;
        this.sinceRevision = sinceRevision;
    }

    @Override
    public void close() throws IOException {
        underlying.close();
    }

    /**
     * Tests if this enumeration contains more elements.
     * @return {@code true} if and only if a defined {@code sinceRevision} has
     * not yet matched and if the underlying sequence contains at least one
     * more element to provide; {@code false} otherwise.
     */
    @Override
    public boolean hasMoreElements() {
        if (didMatchRevision) {
            return false;
        } else {
            return underlying.hasMoreElements();
        }
    }

    /**
     * Returns the next element of this enumeration if a defined
     * {@code sinceRevision} has not yet matched and if the underlying sequence
     * has at least one more element to provide.
     * @return the next element of this enumeration.
     * @throws NoSuchElementException if a defined {@code sinceRevision} has
     * matched or if no more elements exist.
     */
    @Override
    public History nextElement() {
        if (didMatchRevision) {
            throw new NoSuchElementException();
        }

        History next = underlying.nextElement();

        if (sinceRevision != null) {
            // Iterate to try to match sinceRevision within the element.
            int i = 0;
            for (; i < next.count(); ++i) {
                HistoryEntry historyEntry = next.getHistoryEntry(i);
                if (sinceRevision.equals(historyEntry.getRevision())) {
                    break;
                }
            }

            if (i < next.count()) {
                didMatchRevision = true;

                // non-intuitive order BTW
                List<HistoryEntry> filtered = new ArrayList<>(next.getHistoryEntries(i, 0));
                next = new History(filtered, next.getRenamedFiles());

                // Exhaust the underlying sequence
                while (underlying.hasMoreElements()) {
                    underlying.nextElement();
                }
            }
        }

        return next;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int exitValue() {
        return underlying.exitValue();
    }
}
