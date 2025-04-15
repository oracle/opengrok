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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2025, Yelisey Romanov <progoramur@gmail.com>.
 */
package org.opengrok.indexer.analysis.ocaml;

import org.opengrok.indexer.analysis.AbstractAnalyzer.Genre;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents a factory to create {@link OCamlAnalyzer} instances.
 */
public class OCamlAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "OCaml";

    private static final String[] SUFFIXES = {"ML", "MLI"};

    /**
     * Initializes a factory instance to associate a file extensions ".ml",
     * ".mli" with {@link OCamlAnalyzer}.
     */
    public OCamlAnalyzerFactory() {
        super(null, null, SUFFIXES, null, null, "text/plain", Genre.PLAIN,
                NAME, true);
    }

    /**
     * Creates a new {@link OCamlAnalyzer} instance.
     * @return a defined instance
     */
    @Override
    protected FileAnalyzer newAnalyzer() {
        return new OCamlAnalyzer(this);
    }
}
