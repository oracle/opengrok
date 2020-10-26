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
package org.opengrok.indexer.analysis.python;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 *
 * @author Lubos Kosco
 */

public class PythonAnalyzerFactory extends FileAnalyzerFactory {

    private static final String name = "Python";

    //TODO note that .PM below is kind of wrong, since perl already has this and is registered before python analyzer
    // unfortunately we miss code that would be able to share extensions between analyzers
    private static final String[] SUFFIXES = {
        "PY"
    };
        //"PM"
    private static final String[] MAGICS = {
        "#!/usr/bin/env python",
        "#!/usr/bin/python",
        "#!/bin/python",
        "#!python"
    };

    public PythonAnalyzerFactory() {
        super(null, null, SUFFIXES, MAGICS, null, "text/plain", AbstractAnalyzer.Genre.PLAIN, name);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new PythonAnalyzer(this);
    }
}
