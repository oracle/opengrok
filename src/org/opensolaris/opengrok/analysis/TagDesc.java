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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis;

/**
 * Represents an immutable tuple of string-converted {@link Definitions.Tag}
 * data for source-context presentations.
 */
public class TagDesc {

    public final String symbol;
    public final String lineno;
    public final String type;
    public final String text;
    public final String scope;

    public TagDesc(String symbol, String lineno, String type, String text,
            String scope) {
        this.symbol = symbol;
        this.lineno = lineno;
        this.type = type;
        this.text = text;
        this.scope = scope;
    }
}
