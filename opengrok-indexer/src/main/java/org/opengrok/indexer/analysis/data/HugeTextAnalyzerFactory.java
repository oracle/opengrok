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

package org.opengrok.indexer.analysis.data;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents a factory for creating {@link HugeTextAnalyzer} instances.
 */
public class HugeTextAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "Huge Text";

    /**
     * Gets a factory instance with no associated file extensions nor magic nor
     * any other mapping attribute.
     */
    public static final HugeTextAnalyzerFactory DEFAULT_INSTANCE = new HugeTextAnalyzerFactory();

    private HugeTextAnalyzerFactory() {
        super(null, null, null, null, null, null, AbstractAnalyzer.Genre.DATA, NAME);
    }

    /**
     * Creates a new {@link HugeTextAnalyzer} instance.
     * @return a defined instance
     */
    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new HugeTextAnalyzer(this);
    }
}
