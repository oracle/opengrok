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
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.function.Supplier;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.opengrok.indexer.configuration.Project;

/**
 * @author Chandan
 */
public abstract class AbstractAnalyzer extends Analyzer {

    public static final Reader DUMMY_READER = new StringReader("");
    protected AnalyzerFactory factory;
    protected Supplier<JFlexTokenizer> symbolTokenizerFactory;
    protected Project project;
    protected Ctags ctags;
    protected boolean scopesEnabled;
    protected boolean foldingEnabled;

    public AbstractAnalyzer(ReuseStrategy reuseStrategy) {
        super(reuseStrategy);
    }

    /**
     * Subclasses should override to return the case-insensitive name aligning
     * with either a built-in Universal Ctags language name or an OpenGrok
     * custom language name.
     * @return a defined instance or {@code null} if the analyzer has no aligned
     * Universal Ctags language
     */
    public abstract String getCtagsLang();

    public abstract long getVersionNo();

    /**
     * Subclasses should override to produce a value relevant for the evolution
     * of their analysis in each release.
     *
     * @return 0 since {@link AbstractAnalyzer} is not specialized
     */
    protected int getSpecializedVersionNo() {
        return 0;
    }

    public void setCtags(Ctags ctags) {
        this.ctags = ctags;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public void setScopesEnabled(boolean scopesEnabled) {
        this.scopesEnabled = supportsScopes() && scopesEnabled;
    }

    public void setFoldingEnabled(boolean foldingEnabled) {
        this.foldingEnabled = supportsScopes() && foldingEnabled;
    }

    protected abstract boolean supportsScopes();

    /**
     * Get the factory which created this analyzer.
     *
     * @return the {@code FileAnalyzerFactory} which created this analyzer
     */
    public final AnalyzerFactory getFactory() {
        return factory;
    }

    public AbstractAnalyzer.Genre getGenre() {
        return factory.getGenre();
    }

    public abstract String getFileTypeName();

    public abstract void analyze(Document doc, StreamSource src, Writer xrefOut)
            throws IOException, InterruptedException;

    public abstract Xrefer writeXref(WriteXrefArgs args) throws IOException;

    @Override
    protected abstract TokenStreamComponents createComponents(String fieldName);

    protected abstract void addNumLines(Document doc, int value);

    protected abstract void addLOC(Document doc, int value);

    @Override
    protected abstract TokenStream normalize(String fieldName, TokenStream in);

    /**
     * What kind of file is this?
     */
    public enum Genre {
        /**
         * xrefed - line numbered context.
         */
        PLAIN("p"),
        /**
         * xrefed - summarizer context.
         */
        XREFABLE("x"),
        /**
         * not xrefed - no context - used by diff/list.
         */
        IMAGE("i"),
        /**
         * not xrefed - no context.
         */
        DATA("d"),
        /**
         * not xrefed - summarizer context from original file.
         */
        HTML("h");
        private final String typeName;

        Genre(String typename) {
            this.typeName = typename;
        }

        /**
         * Get the type name value used to tag lucene documents.
         *
         * @return a none-null string.
         */
        public String typeName() {
            return typeName;
        }

        /**
         * Get the Genre for the given type name.
         *
         * @param typeName name to check
         * @return {@code null} if it doesn't match any genre, the genre
         * otherwise.
         * @see #typeName()
         */
        public static Genre get(String typeName) {
            if (typeName == null) {
                return null;
            }
            for (Genre g : values()) {
                if (g.typeName.equals(typeName)) {
                    return g;
                }
            }
            return null;
        }
    }
}
