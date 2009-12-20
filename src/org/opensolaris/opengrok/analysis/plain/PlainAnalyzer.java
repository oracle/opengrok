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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.Hash2TokenStream;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

/**
 * Analyzer for plain text files
 * Created on September 21, 2005
 *
 * @author Chandan
 */
public class PlainAnalyzer extends FileAnalyzer {

    protected char[] content;
    protected int len;
    private final PlainFullTokenizer plainfull;
    private final PlainSymbolTokenizer plainref;
    private final PlainXref xref;
    private static final Reader dummy = new StringReader(" ");
    protected Definitions defs;

    /** Creates a new instance of PlainAnalyzer */
    protected PlainAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        content = new char[64 * 1024];
        len = 0;
        plainfull = new PlainFullTokenizer(dummy);
        plainref = new PlainSymbolTokenizer(dummy);
        xref = new PlainXref((Reader) null);
    }

    @Override
    public void analyze(Document doc, InputStream in) throws IOException {
        Reader inReader =
                ExpandTabsReader.wrap(new InputStreamReader(in), project);
        len = 0;
        do {
            int rbytes = inReader.read(content, len, content.length - len);
            if (rbytes > 0) {
                if (rbytes == (content.length - len)) {
                    char[] content2 = new char[content.length * 2];
                    System.arraycopy(content, 0, content2, 0, content.length);
                    content = content2;
                }
                len += rbytes;
            } else {
                break;
            }
        } while (true);

        doc.add(new Field("full", dummy));
        String fullpath;
        if ((fullpath = doc.get("fullpath")) != null && ctags != null) {
            defs = ctags.doCtags(fullpath + "\n");
            if (defs != null && defs.numberOfSymbols() > 0) {
                doc.add(new Field("defs", dummy));
                doc.add(new Field("refs", dummy)); //@FIXME adding a refs field only if it has defs?
                byte[] tags = defs.serialize();
                doc.add(new Field("tags", tags, Field.Store.YES));
            }
        }
    }

    public TokenStream tokenStream(String fieldName, Reader reader) {
        if ("full".equals(fieldName)) {
            plainfull.reInit(content, len);
            return plainfull;
        } else if ("refs".equals(fieldName)) {
            plainref.reInit(content, len);
            return plainref;
        } else if ("defs".equals(fieldName)) {
            return new Hash2TokenStream(defs.getSymbols());
        }
        return super.tokenStream(fieldName, reader);
    }

    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        xref.project = project;
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     * @param in Input source
     * @param out Output xref writer
     * @param annotation annotation for the file (could be null)
     */
    static void writeXref(Reader in, Writer out, Annotation annotation, Project project) throws IOException {
        PlainXref xref = new PlainXref(in);
        xref.annotation = annotation;
        xref.project = project;
        xref.write(out);
    }
}
