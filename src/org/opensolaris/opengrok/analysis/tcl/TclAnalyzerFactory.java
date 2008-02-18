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

package org.opensolaris.opengrok.analysis.tcl;

import java.io.InputStream;
import java.io.IOException;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.history.Annotation;

public class TclAnalyzerFactory extends FileAnalyzerFactory {
    private static final String[] SUFFIXES = {
        "TCL",
        "TM",
        "TK",
        "WISH",
        "EXP",
        "TCLX",
        "ITCL",
        "ITK",
    };

    public TclAnalyzerFactory() {
        super(SUFFIXES, null, null, "text/plain", Genre.PLAIN);
    }

    @Override
    protected FileAnalyzer newAnalyzer() {
        return new TclAnalyzer(this);
    }

    @Override
    public void writeXref(InputStream in, Writer out, Annotation annotation)
        throws IOException {
        TclAnalyzer.writeXref(in, out, annotation);
    }
}
