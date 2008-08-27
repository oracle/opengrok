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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.sh;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

public class ShAnalyzerFactory extends FileAnalyzerFactory {
    private static final String[] NAMES = {
        "MAKEFILE", "GNUMAKEFILE"
    };

    private static final String[] SUFFIXES = {
        "SH",
        "KSH",
        "CSH",
        "BASH",
        "PERL",
        "RUBY",
        "PM",
        "RB",
        "PY",
        "PL",
        "P5",
        "AWK",
        "GMK",
        "CONF",
        "COM",
        "SPEC",
        "FLG",
        "XCL",                  // message
    };

    private static final String[] MAGICS = {
        "#!",
        "##",
        "#\n#",
    };

    public ShAnalyzerFactory() {
        super(NAMES, SUFFIXES, MAGICS, null, "text/plain", Genre.PLAIN);
    }

    @Override
    protected FileAnalyzer newAnalyzer() {
        return new ShAnalyzer(this);
    }

    @Override
    public void writeXref(InputStream in, Writer out, Annotation annotation, Project project)
        throws IOException {
        ShAnalyzer.writeXref(in, out, annotation, project);
    }
}
