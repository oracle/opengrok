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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.analysis.data;

import org.apache.lucene.document.Document;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.OGKTextField;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.LimitedReader;
import org.opengrok.indexer.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Represents an analyzer for huge text data files that are not eligible for
 * xref.
 */
public class HugeTextAnalyzer extends FileAnalyzer {

    /**
     * Creates a new instance.
     * @param factory defined instance for the analyzer
     */
    protected HugeTextAnalyzer(AnalyzerFactory factory) {
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
     * @return 20200415_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20200415_00; // Edit comment above too!
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        /*
         * Though we don't intend to xref, Lucene demands consistency or else it
         * would throw IllegalArgumentException: cannot change field "full" from
         * index options=DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS to
         * inconsistent index options=DOCS_AND_FREQS_AND_POSITIONS
         */
        doc.add(new OGKTextField(QueryBuilder.FULL, getReader(src.getStream())));
    }

    protected Reader getReader(InputStream stream) throws IOException {
        // sourceRoot is read with UTF-8 as a default.
        return new LimitedReader(IOUtils.createBOMStrippedReader(stream,
                StandardCharsets.UTF_8.name()),
                RuntimeEnvironment.getInstance().getHugeTextLimitCharacters());
    }
}
