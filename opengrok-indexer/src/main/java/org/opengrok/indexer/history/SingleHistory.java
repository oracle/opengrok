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

import java.util.NoSuchElementException;

/**
 * Represents a history sequence for a single item. This is used for older
 * repository implementations that read all history at once with no
 * sequencing.
 */
class SingleHistory implements HistoryEnumeration {
    private final History element;
    private boolean didYield;

    SingleHistory(History element) {
        if (element == null) {
            throw new IllegalArgumentException("element is null");
        }
        this.element = element;
    }

    /**
     * Does nothing.
     */
    @Override
    public void close() {
    }

    @Override
    public boolean hasMoreElements() {
        return !didYield;
    }

    @Override
    public History nextElement() {
        if (didYield) {
            throw new NoSuchElementException();
        }
        didYield = true;
        return element;
    }

    /**
     * @return 0 to indicate success
     */
    @Override
    public int exitValue() {
        return 0;
    }
}
