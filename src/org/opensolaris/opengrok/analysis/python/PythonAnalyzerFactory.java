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
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.python;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 *
 * @author Lubos Kosco
 */

public class PythonAnalyzerFactory extends FileAnalyzerFactory {
    //TODO note that .PM below is kind of wrong, since perl already has this and is registered before python analyzer
    // unfortunately we miss code that would be able to share extensions between analyzers
    private static final String[] SUFFIXES = {
        "PY"
    };
        //"PM"
    private static final String[] MAGICS = {
        "#!/usr/bin/env python",
        "#!/usr/bin/python",
        "#!/bin/python"
    };

    public PythonAnalyzerFactory() {
        super(null, SUFFIXES, MAGICS, null, "text/plain", Genre.PLAIN);
    }

    @Override
    protected FileAnalyzer newAnalyzer() {
        return new PythonAnalyzer(this);
    }

    @Override
    public void writeXref(Reader in, Writer out, Definitions defs, Annotation annotation, Project project)
        throws IOException {
        PythonAnalyzer.writeXref(in, out, defs, annotation, project);
    }
}
