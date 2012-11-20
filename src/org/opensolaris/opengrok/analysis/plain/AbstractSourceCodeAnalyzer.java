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
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.plain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 *
 * @author Lubos Kosco
 */
public abstract class AbstractSourceCodeAnalyzer extends PlainAnalyzer {

    JFlexTokenizer symbolTokenizer;
    JFlexXref xref;

    /**
     * Creates a new instance of abstract analyzer
     */
    protected AbstractSourceCodeAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    /**
     * sets the tokenizer and lexer
     */
    protected void setAnalyzers(JFlexTokenizer psymbolTokenizer, JFlexXref pxref) {
        symbolTokenizer = psymbolTokenizer;
        xref = pxref;
    }

    @Override
    public void analyze(Document doc, Reader in) throws IOException {
        super.analyze(doc, in);
        doc.add(new Field("refs", AnalyzerGuru.dummyS, TextField.TYPE_STORED));
    }

    @Override
    public Analyzer.TokenStreamComponents createComponents(String fieldName, Reader reader) {
        if ("refs".equals(fieldName)) {
            symbolTokenizer.reInit(content, len);
            TokenStreamComponents tc = new TokenStreamComponents(symbolTokenizer) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    symbolTokenizer.reInit(content, len);
                    super.setReader(reader);
                }
            };
            return tc;
        }
        return super.createComponents(fieldName, reader);
    }

    /**
     * Write a cross referenced HTML file.
     *
     * @param out Writer to write HTML cross-reference
     */
    @Override
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        xref.setDefs(defs);
        xref.project = project;
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     *
     * @param in Input source
     * @param out Output xref writer
     * @param defs definitions for the file (could be null)
     * @param annotation annotation for the file (could be null)
     */
    static protected void writeXref(JFlexXref lxref, Reader in, Writer out, Definitions defs, Annotation annotation, Project project) throws IOException {
        if (lxref != null) {
            lxref.reInit(in);
            lxref.annotation = annotation;
            lxref.project = project;
            lxref.setDefs(defs);
            lxref.write(out);
        }
    }
}
