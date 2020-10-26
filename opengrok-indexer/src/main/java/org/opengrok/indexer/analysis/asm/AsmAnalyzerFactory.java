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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.asm;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents a factory to create {@link AsmAnalyzer} instances.
 */
public class AsmAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "Asm";

    private static final String[] SUFFIXES = {"ASM", "S"};

    /**
     * Initializes a factory instance to associate file extensions ".asm" and
     * ".s" with {@link AsmAnalyzer}.
     */
    public AsmAnalyzerFactory() {
        super(null, null, SUFFIXES, null, null, "text/plain", AbstractAnalyzer.Genre.PLAIN, NAME);
    }

    /**
     * Creates a new {@link AsmAnalyzer} instance.
     * @return a defined instance
     */
    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new AsmAnalyzer(this);
    }
}
