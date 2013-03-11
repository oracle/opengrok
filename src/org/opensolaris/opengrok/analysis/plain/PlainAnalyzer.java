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
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.plain;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.Hash2Tokenizer;
import org.opensolaris.opengrok.analysis.TextAnalyzer;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 * Analyzer for plain text files Created on September 21, 2005
 *
 * @author Chandan
 */
public class PlainAnalyzer extends TextAnalyzer {

    private char[] content;
    private int len;
    protected PlainXref xref = new PlainXref((Reader) null);
    protected Definitions defs;

    /**
     * Creates a new instance of PlainAnalyzer
     */
    protected PlainAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        content = new char[64 * 1024];
        len = 0;
    }

    @Override
    public void analyze(Document doc, Reader in) throws IOException {
        Reader inReader =
                ExpandTabsReader.wrap(in, project);

        len = 0;
        do {
            int rbytes = inReader.read(content, len, content.length - len);
            if (rbytes >= 0) {
                if (rbytes == (content.length - len)) {
                    content = Arrays.copyOf(content, content.length * 2);
                }
                len += rbytes;
            } else {
                break;
            }
        } while (true);

        doc.add(new TextField("full", getContentReader()));
        String fullpath = doc.get("fullpath");
        if (fullpath != null && ctags != null) {
            defs = ctags.doCtags(fullpath + "\n");
            if (defs != null && defs.numberOfSymbols() > 0) {
                doc.add(new TextField("defs", new Hash2Tokenizer(defs.getSymbols())));
                doc.add(new TextField("refs", getContentReader()));
                byte[] tags = defs.serialize();
                doc.add(new StoredField("tags", tags));
            }
        }
    }

    @Override
    public TokenStreamComponents createComponents(String fieldName, Reader reader) {
        if ("refs".equals(fieldName)) {
            return new TokenStreamComponents(new PlainSymbolTokenizer(reader));
        }
        return super.createComponents(fieldName, reader);
    }

    /**
     * Get a reader that reads from the {@link #content} array.
     */
    protected Reader getContentReader() {
        return new CharArrayReader(content, 0, len);
    }

    /**
     * Write a cross referenced HTML file.
     *
     * @param out Writer to write HTML cross-reference
     */
    @Override
    public void writeXref(Writer out) throws IOException {
        xref.reInit(getContentReader());
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
    static void writeXref(Reader in, Writer out, Definitions defs, Annotation annotation, Project project) throws IOException {
        PlainXref xref = new PlainXref(in);
        xref.annotation = annotation;
        xref.project = project;
        xref.setDefs(defs);
        xref.write(out);
    }
}
