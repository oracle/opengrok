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
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.document.Document;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.NumLinesLOC;
import org.opengrok.indexer.analysis.OGKTextField;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.analysis.TextAnalyzer;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.XrefWork;
import org.opengrok.indexer.analysis.Xrefer;
import org.opengrok.indexer.search.QueryBuilder;

/**
 * Analyzes HTML files.
 * Created on September 30, 2005
 * @author Chandan
 */
public class XMLAnalyzer extends TextAnalyzer {

    /**
     * Creates a new instance of XMLAnalyzer.
     * @param factory factory to be used
     */
    protected XMLAnalyzer(AnalyzerFactory factory) {
        super(factory);
    }

    /**
     * @return {@code "XML"}
     */
    @Override
    public String getCtagsLang() {
        return "XML";
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20180112_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180112_00; // Edit comment above too!
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException, InterruptedException {
        doc.add(new OGKTextField(QueryBuilder.FULL, getReader(src.getStream())));

        if (xrefOut != null) {
            try (Reader in = getReader(src.getStream())) {
                WriteXrefArgs args = new WriteXrefArgs(in, xrefOut);
                args.setProject(project);
                XrefWork xrefWork = new XrefWork(args, this);
                Xrefer xref = xrefWork.getXrefer();

                if (xref != null) {
                    String path = doc.get(QueryBuilder.PATH);
                    addNumLinesLOC(doc, new NumLinesLOC(path, xref.getLineNumber(), xref.getLOC()));
                }
            } catch (ExecutionException e) {
                throw new InterruptedException("failed to generate xref :" + e);
            }
        }
    }

    /**
     * Creates a wrapped {@link XMLXref} instance.
     * @param reader the data to produce xref for
     * @return xref instance
     */
    @Override
    protected JFlexXref newXref(Reader reader) {
        return new JFlexXref(new XMLXref(reader));
    }
}
