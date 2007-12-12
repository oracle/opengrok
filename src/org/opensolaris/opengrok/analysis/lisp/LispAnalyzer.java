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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis.lisp;

import org.apache.lucene.document.*;
import org.apache.lucene.analysis.*;
import java.io.*;
import org.opensolaris.opengrok.analysis.*;
import org.opensolaris.opengrok.analysis.plain.*;
import org.opensolaris.opengrok.history.Annotation;

public class LispAnalyzer extends PlainAnalyzer {

    LispSymbolTokenizer cref;
    LispXref xref;
    Reader dummy = new StringReader("");

    public static String[] suffixes = {
        "LISP",
        "LSP",
        "EL",
        "SCM",
    };

    public LispAnalyzer() {
        super();
        cref = new LispSymbolTokenizer(dummy);
        xref = new LispXref(dummy);
    }

    public void analyze(Document doc, InputStream in) {
        super.analyze(doc, in);
        doc.add(new Field("refs", dummy));
    }

    public TokenStream tokenStream(String fieldName, Reader reader) {
        if("refs".equals(fieldName)) {
            cref.reInit(super.content, super.len);
            return cref;
        }
        return super.tokenStream(fieldName, reader);
    }

    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        xref.setDefs(defs);
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    public static void writeXref(InputStream in, Writer out,
                                 Annotation annotation) throws IOException {
        LispXref xref = new LispXref(in);
        xref.annotation = annotation;
        xref.write(out);
    }
}
