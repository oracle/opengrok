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
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved. Use is subject to license terms.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.opengrok.indexer.analysis.plain.PlainFullTokenizer;
import org.opengrok.indexer.analysis.plain.PlainSymbolTokenizer;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;

/**
 * Base class for all different File Analyzers.
 *
 * An Analyzer for a filetype provides
 * <ol>
 * <li>the file extensions and magic numbers it analyzes</li>
 * <li>a lucene document listing the fields it can support</li>
 * <li>TokenStreams for each of the field it said requires tokenizing in 2</li>
 * <li>cross reference in HTML format</li>
 * <li>The type of file data, plain text etc</li>
 * </ol>
 *
 * Created on September 21, 2005
 *
 * @author Chandan
 */
public class FileAnalyzer extends AbstractAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAnalyzer.class);

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
     * <p>
     * The value is the union of a {@link FileAnalyzer} root version and the
     * value from {@link #getSpecializedVersionNo()}. Changing the root version
     * affects all analyzers simultaneously; while subclasses can override
     * {@link #getSpecializedVersionNo()} to allow changes that affect a few.
     * @return (20061115_01 &lt;&lt; 32) | {@link #getSpecializedVersionNo()}
     */
    @Override
    public final long getVersionNo() {
        final int rootVersionNo = 20061115_01; // Edit comment above too!
        return ((long) rootVersionNo << 32) | getSpecializedVersionNo();
    }

    @Override
    protected boolean supportsScopes() {
        return false;
    }

    /**
     * Creates a new instance of FileAnalyzer.
     *
     * @param factory defined instance for the analyzer
     */
    public FileAnalyzer(AnalyzerFactory factory) {
        super(Analyzer.PER_FIELD_REUSE_STRATEGY);
        if (factory == null) {
            throw new IllegalArgumentException("`factory' is null");
        }
        this.factory = factory;
        this.symbolTokenizerFactory = this::createPlainSymbolTokenizer;
    }

    /**
     * Creates a new instance of {@link FileAnalyzer}.
     *
     * @param factory defined instance for the analyzer
     * @param symbolTokenizerFactory a defined instance relevant for the file
     */
    protected FileAnalyzer(AnalyzerFactory factory,
            Supplier<JFlexTokenizer> symbolTokenizerFactory) {

        super(Analyzer.PER_FIELD_REUSE_STRATEGY);
        if (factory == null) {
            throw new IllegalArgumentException("`factory' is null");
        }
        if (symbolTokenizerFactory == null) {
            throw new IllegalArgumentException("symbolTokenizerFactory is null");
        }
        this.factory = factory;
        this.symbolTokenizerFactory = symbolTokenizerFactory;
    }

    /**
     * Returns the normalized name of the analyzer, which should corresponds to
     * the file type. Example: The analyzer for the C language (CAnalyzer) would
     * return “c”.
     *
     * @return Normalized name of the analyzer.
     */
    @Override
    public String getFileTypeName() {
        String name = this.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String suffix = "analyzer";

        if (name.endsWith(suffix)) {
            return name.substring(0, name.length() - suffix.length());
        }

        return name;
    }

    /**
     * Analyze the contents of a source file. This includes populating the
     * Lucene document with fields to add to the index, and writing the
     * cross-referenced data to the specified destination.
     *
     * @param doc the Lucene document
     * @param src the input data source
     * @param xrefOut where to write the xref (may be {@code null})
     * @throws IOException if any I/O error
     * @throws InterruptedException if a timeout occurs
     */
    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut)
            throws IOException, InterruptedException {
        // not used
    }

    /**
     * Derived classes should override to write a cross referenced HTML file for
     * the specified args.
     *
     * @param args a defined instance
     * @return the instance used to write the cross-referencing
     * @throws java.io.IOException if an error occurs
     */
    @Override
    public Xrefer writeXref(WriteXrefArgs args) throws IOException {
        throw new UnsupportedOperationException(
                "Base FileAnalyzer cannot write xref");
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        switch (fieldName) {
            case QueryBuilder.FULL:
                return new TokenStreamComponents(createPlainFullTokenizer());
            case QueryBuilder.PATH:
            case QueryBuilder.PROJECT:
                return new TokenStreamComponents(new PathTokenizer());
            case QueryBuilder.HIST:
                try (HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer()) {
                    return historyAnalyzer.createComponents(fieldName);
                }
            //below is set by PlainAnalyzer to workaround #1376 symbols search works like full text search 
            case QueryBuilder.REFS: {
                return new TokenStreamComponents(symbolTokenizerFactory.get());
            }
            case QueryBuilder.DEFS:
                return new TokenStreamComponents(createPlainSymbolTokenizer());
            default:
                LOGGER.log(
                        Level.WARNING, "Have no analyzer for: {0}", fieldName);
                return null;
        }
    }

    /**
     * Add a field to store document number of lines.
     * @param doc the target document
     * @param value the number of lines
     */
    @Override
    protected void addNumLines(Document doc, int value)  {
        doc.add(new StoredField(QueryBuilder.NUML, value));
    }

    /**
     * Add a field to store document lines-of-code.
     * @param doc the target document
     * @param value the loc
     */
    @Override
    protected void addLOC(Document doc, int value)  {
        doc.add(new StoredField(QueryBuilder.LOC, value));
    }

    private JFlexTokenizer createPlainSymbolTokenizer() {
        return new JFlexTokenizer(new PlainSymbolTokenizer(
                AbstractAnalyzer.DUMMY_READER));
    }

    private JFlexTokenizer createPlainFullTokenizer() {
        return new JFlexTokenizer(new PlainFullTokenizer(
                AbstractAnalyzer.DUMMY_READER));
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        switch (fieldName) {
            case QueryBuilder.DEFS:
            case QueryBuilder.REFS:
                return in;
            default:
                return new LowerCaseFilter(in);
        }
    }
}
