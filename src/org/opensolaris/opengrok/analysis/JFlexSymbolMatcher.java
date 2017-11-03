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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents an abstract base class for subclasses of
 * {@link JFlexStateStacker} that can publish as {@link ScanningSymbolMatcher}.
 */
public abstract class JFlexSymbolMatcher extends JFlexStateStacker
    implements ScanningSymbolMatcher {

    private final CopyOnWriteArrayList<SymbolMatchedListener> listeners =
        new CopyOnWriteArrayList<>();

    @Override
    public void addSymbolMatchedListener(SymbolMatchedListener l) {
        listeners.add(l);
    }

    @Override
    public void removeSymbolMatchedListener(SymbolMatchedListener l) {
        listeners.remove(l);
    }

    /**
     * Raises
     * {@link SymbolMatchedListener#symbolMatched(org.opensolaris.opengrok.analysis.SymbolMatchedEvent)}
     * for all subscribed listeners in turn.
     * @param str the symbol string
     * @param start the symbol start position
     * @param end the symbol end position
     */
    protected void onSymbolMatched(String str, int start, int end) {
        SymbolMatchedEvent evt = new SymbolMatchedEvent(this, str, start, end);

        listeners.forEach((l) -> {
            l.symbolMatched(evt);
        });
    }
}
