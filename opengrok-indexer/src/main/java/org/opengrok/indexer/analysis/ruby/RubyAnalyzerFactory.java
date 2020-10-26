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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.ruby;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents an implementation of {@link FileAnalyzerFactory} to produce
 * {@link RubyAnalyzer} instances.
 */
public class RubyAnalyzerFactory extends FileAnalyzerFactory {

    private static final String name = "Ruby";
    
    private static final String[] SUFFIXES = {
        "RB",
        "RUBY"
    };
    private static final String[] MAGICS = {
        "#!/usr/bin/env ruby",
        "#!/usr/bin/ruby",
        "#!/usr/local/bin/ruby",
        "#!/bin/ruby",
        "#!ruby",
    };

    /**
     * Creates a new instance of {@link RubyAnalyzerFactory}.
     */
    public RubyAnalyzerFactory() {
        super(null, null, SUFFIXES, MAGICS, null, "text/plain", AbstractAnalyzer.Genre.PLAIN,
            name);
    }

    /**
     * Creates a new instance of {@link RubyAnalyzer}.
     * @return the new instance
     */
    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new RubyAnalyzer(this);
    }
}
