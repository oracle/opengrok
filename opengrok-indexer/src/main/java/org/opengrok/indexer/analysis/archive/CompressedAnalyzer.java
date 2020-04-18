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
 * Portions Copyright (c) 2017-2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.archive;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.analysis.data.HugeTextAnalyzerFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * Represents a base for compressed formats (e.g. gzip or bzip2) but not for
 * archive formats that have compression (e.g. Zip or Jar).
 * @author Chandan
 */
public abstract class CompressedAnalyzer extends FileAnalyzer {

    protected Genre g;

    @Override
    public Genre getGenre() {
        if (g != null) {
            return g;
        }
        return super.getGenre();
    }

    protected CompressedAnalyzer(AnalyzerFactory factory) {
        super(factory);
    }

    protected void analyzeUncompressed(
            Document doc, Writer xrefOut, AbstractAnalyzer fa, StreamSource compressedSrc)
            throws IOException, InterruptedException {

        if (fa.getGenre() == Genre.PLAIN) {
            if (meetsHugeTextThreshold(compressedSrc)) {
                fa = HugeTextAnalyzerFactory.DEFAULT_INSTANCE.getAnalyzer();
                g = Genre.DATA;
            } else {
                g = Genre.XREFABLE;
            }
        } else if (fa.getGenre() == Genre.XREFABLE) {
            g = Genre.XREFABLE;
        } else {
            g = Genre.DATA;
        }

        fa.analyze(doc, compressedSrc, xrefOut);
        if (doc.get(QueryBuilder.T) != null) {
            doc.removeField(QueryBuilder.T);
        }
        doc.add(new Field(QueryBuilder.T, g.typeName(),
                AnalyzerGuru.string_ft_stored_nanalyzed_norms));
    }

    private boolean meetsHugeTextThreshold(StreamSource compressedSrc) throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        int hugeTextThresholdBytes = env.getHugeTextThresholdBytes();
        if (Integer.MAX_VALUE == hugeTextThresholdBytes) {
            // Don't bother decompressing to count if the limit is MAX_VALUE.
            return false;
        }

        byte[] buf = new byte[8 * 1024];
        int bytesRead = 0;
        int n;
        try (InputStream in = compressedSrc.getStream()) {
            while ((n = in.read(buf, 0, buf.length)) != -1) {
                bytesRead += n;
                if (bytesRead >= hugeTextThresholdBytes) {
                    return true;
                }
            }
        }
        return false;
    }
}
