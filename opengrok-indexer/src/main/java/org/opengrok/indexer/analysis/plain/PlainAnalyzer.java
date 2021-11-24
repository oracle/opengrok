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
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.analysis.ExpandTabsReader;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.NumLinesLOC;
import org.opengrok.indexer.analysis.OGKTextField;
import org.opengrok.indexer.analysis.OGKTextVecField;
import org.opengrok.indexer.analysis.Scopes;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.analysis.TextAnalyzer;
import org.opengrok.indexer.analysis.WriteXrefArgs;
import org.opengrok.indexer.analysis.Xrefer;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.NullWriter;

/**
 * Analyzer for plain text files.
 *
 * Created on September 21, 2005
 * @author Chandan
 */
public class PlainAnalyzer extends TextAnalyzer {

    /**
     * Creates a new instance of PlainAnalyzer.
     * @param factory defined instance for the analyzer
     */
    protected PlainAnalyzer(AnalyzerFactory factory) {
        super(factory);
    }

    /**
     * Creates a new instance of {@link PlainAnalyzer}.
     * @param factory defined instance for the analyzer
     * @param symbolTokenizerFactory defined instance for the analyzer
     */
    protected PlainAnalyzer(AnalyzerFactory factory,
            Supplier<JFlexTokenizer> symbolTokenizerFactory) {
        super(factory, symbolTokenizerFactory);
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
     * @return 20180208_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180208_00; // Edit comment above too!
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

    private static class XrefWork {
        Xrefer xrefer;
        Exception exception;

        XrefWork(Xrefer xrefer) {
            this.xrefer = xrefer;
        }

        XrefWork(Exception e) {
            this.exception = e;
        }
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException, InterruptedException {
        Definitions defs = null;
        NullWriter nullWriter = null;

        doc.add(new OGKTextField(QueryBuilder.FULL, getReader(src.getStream())));

        String fullPath = doc.get(QueryBuilder.FULLPATH);
        if (fullPath != null && ctags != null) {
            defs = ctags.doCtags(fullPath);
            if (defs != null && defs.numberOfSymbols() > 0) {
                tryAddingDefs(doc, defs, src);
                byte[] tags = defs.serialize();
                doc.add(new StoredField(QueryBuilder.TAGS, tags));
            }
        }
        /*
         * This is to explicitly use appropriate analyzer's token stream to
         * work around #1376: symbols search works like full text search.
         */
        JFlexTokenizer symbolTokenizer = symbolTokenizerFactory.get();
        OGKTextField ref = new OGKTextField(QueryBuilder.REFS, symbolTokenizer);
        symbolTokenizer.setReader(getReader(src.getStream()));
        doc.add(ref);

        if (scopesEnabled && xrefOut == null) {
            /*
             * Scopes are generated during xref generation. If xrefs are
             * turned off we still need to run writeXref() to produce scopes,
             * we use a dummy writer that will throw away any xref output.
             */
            nullWriter = new NullWriter();
            xrefOut = nullWriter;
        }

        if (xrefOut != null) {
            try (Reader in = getReader(src.getStream())) {
                RuntimeEnvironment env = RuntimeEnvironment.getInstance();
                WriteXrefArgs args = new WriteXrefArgs(in, xrefOut);
                args.setDefs(defs);
                args.setProject(project);
                CompletableFuture<XrefWork> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return new XrefWork(writeXref(args));
                    } catch (IOException e) {
                        return new XrefWork(e);
                    }
                }, env.getIndexerParallelizer().getXrefWatcherExecutor()).
                        orTimeout(env.getXrefTimeout(), TimeUnit.SECONDS);
                XrefWork xrefWork = future.get(); // Will throw ExecutionException wrapping TimeoutException on timeout.
                Xrefer xref = xrefWork.xrefer;

                if (xref != null) {
                    Scopes scopes = xref.getScopes();
                    if (scopes.size() > 0) {
                        byte[] scopesSerialized = scopes.serialize();
                        doc.add(new StoredField(QueryBuilder.SCOPES,
                                scopesSerialized));
                    }

                    String path = doc.get(QueryBuilder.PATH);
                    addNumLinesLOC(doc, new NumLinesLOC(path, xref.getLineNumber(), xref.getLOC()));
                } else {
                    // Re-throw the exception from writeXref().
                    throw new IOException(xrefWork.exception);
                }
            } catch (ExecutionException e) {
                throw new InterruptedException("failed to generate xref :" + e);
            } finally {
                if (nullWriter != null) {
                    nullWriter.close();
                }
            }
        }
    }

    // DefinitionsTokenStream should not be used in try-with-resources
    @SuppressWarnings("java:S2095")
    private void tryAddingDefs(Document doc, Definitions defs, StreamSource src) throws IOException {

        DefinitionsTokenStream defstream = new DefinitionsTokenStream();
        defstream.initialize(defs, src, this::wrapReader);

        /*
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
