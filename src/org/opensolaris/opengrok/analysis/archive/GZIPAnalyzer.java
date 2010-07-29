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
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.archive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;

/**
 * Analyzes GZip files
 * Created on September 22, 2005
 *
 * @author Chandan
 */
public class GZIPAnalyzer extends FileAnalyzer {
    private Genre g;
    @Override
    public Genre getGenre() {
        if (g != null) {
            return g;
        }
        return super.getGenre();
    }

    protected GZIPAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }
    
    private FileAnalyzer fa;
    
    @Override
    public void analyze(Document doc, InputStream in) throws IOException {
        BufferedInputStream gzis = new BufferedInputStream(new GZIPInputStream(in));
        String path = doc.get("path");
        if (path != null &&
                (path.endsWith(".gz") || path.endsWith(".GZ") || path.endsWith(".Gz"))) {
            String newname = path.substring(0, path.length() - 3);
            //System.err.println("GZIPPED OF = " + newname);
            fa = AnalyzerGuru.getAnalyzer(gzis, newname);
            if (fa == null) {
                this.g = Genre.DATA;
                OpenGrokLogger.getLogger().log(Level.WARNING, "Did not analyze {0}, detected as data.", newname);
            } else { // cant recurse!
                if (fa.getGenre() == Genre.PLAIN || fa.getGenre() == Genre.XREFABLE) {
                    this.g = Genre.XREFABLE;
                } else {
                    this.g = Genre.DATA;
                }
                fa.analyze(doc, gzis);
                if (doc.get("t") != null) {
                    doc.removeField("t");
                    if (g == Genre.XREFABLE) {
                        doc.add(new Field("t", "x", Field.Store.YES, Field.Index.NOT_ANALYZED));
                    }
                }
                return;
            }
        }
    }
    
    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if (fa != null) {
            return fa.tokenStream(fieldName, reader);
        }
        return super.tokenStream(fieldName, reader);
    }

    /**
     * Write a cross referenced HTML file.
     * @param out Writer to store HTML cross-reference
     */
    @Override
    public void writeXref(Writer out) throws IOException {
        if ((fa != null) && (fa.getGenre() == Genre.PLAIN || fa.getGenre() == Genre.XREFABLE)) {
            fa.writeXref(out);
        }
    }
}
