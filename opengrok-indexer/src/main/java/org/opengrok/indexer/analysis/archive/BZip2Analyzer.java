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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.archive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.search.QueryBuilder;

/**
 * Analyzes a BZip2 file.
 *
 * Created on September 22, 2005
 * @author Chandan
 */
public class BZip2Analyzer extends FileAnalyzer {

    private Genre g;

    @Override
    public Genre getGenre() {
        if (g != null) {
            return g;
        }
        return super.getGenre();
    }

    protected BZip2Analyzer(AnalyzerFactory factory) {
        super(factory);
    }

    /**
     * @return {@code null} as there is no aligned language
     */
    @Override
    public String getCtagsLang() {
        return null;
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20180111_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180111_00; // Edit comment above too!
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut)
            throws IOException, InterruptedException {
        AbstractAnalyzer fa;

        StreamSource bzSrc = wrap(src);
        String path = doc.get(QueryBuilder.PATH);
        if (path != null
                && (path.endsWith(".bz2") || path.endsWith(".BZ2") || path.endsWith(".bz"))) {
            String newname = path.substring(0, path.lastIndexOf('.'));
            //System.err.println("BZIPPED OF = " + newname);
            try (InputStream in = bzSrc.getStream()) {
                fa = AnalyzerGuru.getAnalyzer(in, newname);
            }
            if (!(fa instanceof BZip2Analyzer)) {
                if (fa.getGenre() == Genre.PLAIN || fa.getGenre() == Genre.XREFABLE) {
                    this.g = Genre.XREFABLE;
                } else {
                    this.g = Genre.DATA;
                }
                fa.analyze(doc, bzSrc, xrefOut);
                if (doc.get(QueryBuilder.T) != null) {
                    doc.removeField(QueryBuilder.T);
                    if (g == Genre.XREFABLE) {
                        doc.add(new Field(QueryBuilder.T, g.typeName(),
                                AnalyzerGuru.string_ft_stored_nanalyzed_norms));
                    }
                }
            }
        }
    }

    /**
     * Wrap the raw stream source in one that returns the uncompressed stream.
     */
    private static StreamSource wrap(final StreamSource src) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                InputStream raw = src.getStream();
                // A BZip2 file starts with "BZ", but CBZip2InputStream
                // expects the magic bytes to be stripped off first.
                if (raw.read() == 'B' && raw.read() == 'Z') {
                    return new BufferedInputStream(new CBZip2InputStream(raw));
                } else {
                    throw new IOException("Not BZIP2 format");
                }
            }
        };
    }    
}
