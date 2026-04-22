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
 * Copyright (c) 2026, Oracle and/or its affiliates.
 */
package org.opengrok.indexer.analysis.yang;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Factory for YANG files.
 */
public class YangAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "Yang";

    private static final String[] SUFFIXES = {
        "YANG"
    };

    public YangAnalyzerFactory() {
        super(null, null, SUFFIXES, null, null, "text/plain",
                AbstractAnalyzer.Genre.PLAIN, NAME, true);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new YangAnalyzer(this);
    }
}
