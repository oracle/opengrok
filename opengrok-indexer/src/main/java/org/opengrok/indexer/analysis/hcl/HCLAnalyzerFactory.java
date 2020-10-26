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
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.hcl;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents an extension of {@link FileAnalyzerFactory} to produce
 * {@link HCLAnalyzer} instances.
 */
public class HCLAnalyzerFactory extends FileAnalyzerFactory {

    private static final String name = "HCL";

    private static final String[] SUFFIXES = {"HCL"};

    /**
     * Creates a new instance of {@link HCLAnalyzerFactory}.
     */
    public HCLAnalyzerFactory() {
        super(null, null, SUFFIXES, null, null, "text/plain", AbstractAnalyzer.Genre.PLAIN, name);
    }

    /**
     * Creates a new instance of {@link HCLAnalyzer}.
     * @return the new instance
     */
    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new HCLAnalyzer(this);
    }
}
