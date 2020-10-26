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
 * Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

// "How do I make a Class extend Observable when it has extended another class too?"
// Answered by adamski, https://stackoverflow.com/users/127479/adamski,
// https://stackoverflow.com/a/1658735/933163,
// https://stackoverflow.com/questions/1658702/how-do-i-make-a-class-extend-observable-when-it-has-extended-another-class-too

/**
 * Represents an API for a publisher for {@link SymbolMatchedEvent}s.
 */
public interface SymbolMatchedPublisher {
    /**
     * Sets a listener for the publisher.
     * @param l the listener
     */
    void setSymbolMatchedListener(SymbolMatchedListener l);

    /**
     * Clears any listener from the publisher.
     */
    void clearSymbolMatchedListener();

    /**
     * Sets a listener for the publisher.
     * @param l the listener
     */
    void setNonSymbolMatchedListener(NonSymbolMatchedListener l);

    /**
     * Clears any listener from the publisher.
     */
    void clearNonSymbolMatchedListener();
}
