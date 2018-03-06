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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.archive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.opensolaris.opengrok.analysis.AnalyzerGuru;
import org.opensolaris.opengrok.analysis.DigestedInputStream;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.StreamSource;

/**
 * Analyzes a BZip2 file Created on September 22, 2005
 *
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

    protected BZip2Analyzer(FileAnalyzerFactory factory) {
        super(factory);
    }
    private FileAnalyzer fa;

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut)
            throws IOException, InterruptedException {
        StreamSource bzSrc = wrap(src);
        String path = doc.get("path");
        if (path != null
                && (path.endsWith(".bz2") || path.endsWith(".BZ2") || path.endsWith(".bz"))) {
            String newname = path.substring(0, path.lastIndexOf('.'));
            //System.err.println("BZIPPED OF = " + newname);
            try (InputStream in = bzSrc.getStream()) {
                fa = AnalyzerGuru.getAnalyzer(in, newname);
            }
            if (fa instanceof BZip2Analyzer) {
                fa = null;
            } else {
                if (fa.getGenre() == Genre.PLAIN || fa.getGenre() == Genre.XREFABLE) {
                    this.g = Genre.XREFABLE;
                } else {
                    this.g = Genre.DATA;
                }
                fa.analyze(doc, bzSrc, xrefOut);
                if (doc.get("t") != null) {
                    doc.removeField("t");
                    if (g == Genre.XREFABLE) {
                        doc.add(new Field("t", g.typeName(), AnalyzerGuru.string_ft_stored_nanalyzed_norms));
                    }
                }
            }
        }
    }

    /**
     * Wrap the raw stream source in one that returns the uncompressed stream.
     * @param src a defined instance
     * @return a defined instance
     */
    public static StreamSource wrap(final StreamSource src) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                InputStream raw = src.getStream();
                return vetBZip2Stream(raw);
            }

            @Override
            public DigestedInputStream getSHA256stream() throws IOException {
                /*
                 * Unzip the underlying source stream, and ensure that the
                 * digest of the underlying stream -- and not the unzipped
                 * stream -- is returned.
                 */
                DigestedInputStream dis = src.getSHA256stream();
                BufferedInputStream bz2is = vetBZip2Stream(dis.getStream());

                return new DigestedInputStream() {
                    @Override
                    public InputStream getStream() {
                        return bz2is;
                    }

                    @Override
                    public MessageDigest getMessageDigest() {
                        return dis.getMessageDigest();
                    }

                    @Override
                    public byte[] digestAll() throws IOException {
                        return StreamSource.digestAll(this);
                    }

                    @Override
                    public void close() throws Exception {
                        bz2is.close();
                        dis.close();
                    }
                };
            }

            /**
             * As a BZip2 file starts with "BZ", but {@link CBZip2InputStream}
             * expects the magic bytes to be stripped off first, ensures the
             * presence of 'B' and 'Z' and hence consumes them.
             */
            private BufferedInputStream vetBZip2Stream(InputStream raw)
                    throws IOException {
                // A BZip2 file starts with "BZ", but CBZip2InputStream
                // expects the magic bytes to be stripped off first.
                if (raw.read() == 'B' && raw.read() == 'Z') {
                    return new BufferedInputStream(new CBZip2InputStream(raw));
                }
                throw new IOException("Not BZIP2 format");
            }
        };
    }    
}
