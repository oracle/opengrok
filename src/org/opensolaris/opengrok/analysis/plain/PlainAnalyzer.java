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
package org.opensolaris.opengrok.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.ExpandTabsReader;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.analysis.OGKTextField;
import org.opensolaris.opengrok.analysis.OGKTextVecField;
import org.opensolaris.opengrok.analysis.Scopes;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.analysis.TextAnalyzer;
import org.opensolaris.opengrok.analysis.WriteXrefArgs;
import org.opensolaris.opengrok.analysis.Xrefer;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.util.NullWriter;

/**
 * Analyzer for plain text files Created on September 21, 2005
 *
 * @author Chandan
 */
public class PlainAnalyzer extends TextAnalyzer {

    /**
     * Creates a new instance of PlainAnalyzer
     * @param factory defined instance for the analyzer
     */
    protected PlainAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    /**
     * Creates a new instance of {@link PlainAnalyzer}.
     * @param factory defined instance for the analyzer
     * @param symbolTokenizer defined instance for the analyzer
     */
    protected PlainAnalyzer(FileAnalyzerFactory factory,
        JFlexTokenizer symbolTokenizer) {
        super(factory, symbolTokenizer);
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

    /**
     * Creates a wrapped {@link PlainXref} instance.
     * @param reader the data to produce xref for
     * @return an xref instance
     */
    @Override
    protected Xrefer newXref(Reader reader) {
        return new JFlexXref(new PlainXref(reader));
    }

    @Override
    protected Reader getReader(InputStream stream) throws IOException {
        return ExpandTabsReader.wrap(super.getReader(stream), project);
    }
    
    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut)
            throws IOException, InterruptedException {
        Definitions defs = null;

        doc.add(new OGKTextField(QueryBuilder.FULL,
            getReader(src.getStream())));

        String fullpath = doc.get(QueryBuilder.FULLPATH);
        if (fullpath != null && ctags != null) {
            defs = ctags.doCtags(fullpath);
            if (defs != null && defs.numberOfSymbols() > 0) {
                tryAddingDefs(doc, defs, src, fullpath);
                //this is to explicitly use appropriate analyzers tokenstream to workaround #1376 symbols search works like full text search 
                OGKTextField ref = new OGKTextField(QueryBuilder.REFS,
                    this.symbolTokenizer);
                this.symbolTokenizer.setReader(getReader(src.getStream()));
                doc.add(ref);
                byte[] tags = defs.serialize();
                doc.add(new StoredField(QueryBuilder.TAGS, tags));                
            }
        }
        
        if (scopesEnabled && xrefOut == null) {
            /*
             * Scopes are generated during xref generation. If xrefs are
             * turned off we still need to run writeXref to produce scopes,
             * we use a dummy writer that will throw away any xref output.
             */
            xrefOut = new NullWriter();
        }

        if (xrefOut != null) {
            try (Reader in = getReader(src.getStream())) {
                WriteXrefArgs args = new WriteXrefArgs(in, xrefOut);
                args.setDefs(defs);
                args.setProject(project);
                Xrefer xref = writeXref(args);
            
                Scopes scopes = xref.getScopes();
                if (scopes.size() > 0) {
                    byte[] scopesSerialized = scopes.serialize();
                    doc.add(new StoredField(QueryBuilder.SCOPES,
                        scopesSerialized));
                }

                addNumLines(doc, xref.getLineNumber());
                addLOC(doc, xref.getLOC());
            }
        }
    }

    private void tryAddingDefs(Document doc, Definitions defs, StreamSource src,
        String fullpath) throws IOException {

        DefinitionsTokenStream defstream = new DefinitionsTokenStream();
        defstream.initialize(defs, src, (reader) -> wrapReader(reader));

        /**
         *     Testing showed that UnifiedHighlighter will fall back to
         * ANALYSIS in the presence of multi-term queries (MTQs) such as
         * prefixes and wildcards even for fields that are analyzed with
         * POSTINGS -- i.e. with DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS.
         * This is despite UnifiedHighlighter seeming to indicate that
         * postings should be sufficient in the comment for
         * shouldHandleMultiTermQuery(String): "MTQ highlighting can be
         * expensive, particularly when using offsets in postings."
         *     DEFS re-analysis will not be correct, however, as the
         * PlainAnalyzer which UnifiedHighlighter will use on-the-fly will
         * not correctly integrate ctags Definitions.
         *     Storing term vectors, however, allows UnifiedHighlighter to
         * avoid re-analysis at the cost of a larger index. As DEFS are a
         * small subset of source text, it seems worth the cost to get
         * accurate highlighting for DEFS MTQs.
         */
        doc.add(new OGKTextVecField(QueryBuilder.DEFS, defstream));
    }

    /**
     * Identical to {@link #getReader(java.io.InputStream)} but overlaying an
     * existing stream.
     * @see #getReader(java.io.InputStream)
     */
    private Reader wrapReader(Reader reader) {
        return ExpandTabsReader.wrap(reader, project);
    }
}
