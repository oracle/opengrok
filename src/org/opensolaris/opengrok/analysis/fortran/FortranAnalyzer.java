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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.fortran;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzer;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 * An Analyzer for Fortran type of files
 */
public class FortranAnalyzer extends PlainAnalyzer {

    FortranSymbolTokenizer fref;
    FortranXref xref;
    Reader dummy = new StringReader("");

    FortranAnalyzer(FortranAnalyzerFactory factory) {
        super(factory);
        fref = new FortranSymbolTokenizer(dummy);
        xref = new FortranXref(dummy);
    }

    @Override
    public void analyze(Document doc, InputStream in) throws IOException {
        super.analyze(doc, in);
        doc.add(new Field("refs", dummy));
    }

    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if ("refs".equals(fieldName)) {
            fref.reInit(super.content, super.len);
            return fref;
        }
        return super.tokenStream(fieldName, reader);
    }

    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    @Override
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        xref.project = project;
        xref.setDefs(defs);
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(Reader in, Writer out, Annotation annotation, Project project) throws IOException {
        FortranXref xref = new FortranXref(in);
        xref.annotation = annotation;
        xref.project = project;
        xref.write(out);
    }
}
