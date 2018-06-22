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
package org.opensolaris.opengrok.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.opensolaris.opengrok.analysis.plain.PlainFullTokenizer;
import org.opensolaris.opengrok.analysis.plain.PlainSymbolTokenizer;
import org.opensolaris.opengrok.search.QueryBuilder;

public class CompatibleAnalyser extends Analyzer {

    public CompatibleAnalyser() {
        super(Analyzer.PER_FIELD_REUSE_STRATEGY);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        switch (fieldName) {
            case QueryBuilder.FULL:
                return new TokenStreamComponents(
                    createNonWhitespaceFullTokenizer());
            case QueryBuilder.REFS:
                return new TokenStreamComponents(
                    createNonWhitespaceSymbolTokenizer());
            case QueryBuilder.DEFS:
                return new TokenStreamComponents(
                    createNonWhitespaceSymbolTokenizer());
            case QueryBuilder.PATH:
            case QueryBuilder.PROJECT:
                return new TokenStreamComponents(new PathTokenizer());
            case QueryBuilder.HIST:
                return new HistoryAnalyzer().createComponents(fieldName);
            default:
                return new TokenStreamComponents(
                    createPlainFullTokenizer(TokenizerMode.SYMBOLS_ONLY));
        }
    }

    private JFlexTokenizer createPlainFullTokenizer(TokenizerMode mode) {
        JFlexTokenizer tokenizer = new JFlexTokenizer(new PlainFullTokenizer(
                FileAnalyzer.dummyReader));
        tokenizer.setTokenizerMode(mode);
        return tokenizer;
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

    private JFlexTokenizer createNonWhitespaceFullTokenizer() {
        return createPlainFullTokenizer(TokenizerMode.NON_WHITESPACE_ONLY);
    }

    private JFlexTokenizer createNonWhitespaceSymbolTokenizer() {
        JFlexTokenizer tokenizer = new JFlexTokenizer(new PlainSymbolTokenizer(
            FileAnalyzer.dummyReader));
        tokenizer.setTokenizerMode(TokenizerMode.NON_WHITESPACE_ONLY);
        return tokenizer;
    }
}