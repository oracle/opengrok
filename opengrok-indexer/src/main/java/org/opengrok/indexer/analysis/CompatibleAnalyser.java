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
package org.opengrok.indexer.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.opengrok.indexer.analysis.plain.PlainFullTokenizer;
import org.opengrok.indexer.analysis.plain.PlainSymbolTokenizer;
import org.opengrok.indexer.search.QueryBuilder;

public class CompatibleAnalyser extends Analyzer {

    public CompatibleAnalyser() {
        super(Analyzer.PER_FIELD_REUSE_STRATEGY);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        switch (fieldName) {
            case QueryBuilder.FULL:
                return new TokenStreamComponents(createPlainFullTokenizer());
            case QueryBuilder.REFS:
                return new TokenStreamComponents(createPlainSymbolTokenizer());
            case QueryBuilder.DEFS:
                return new TokenStreamComponents(createPlainSymbolTokenizer());
            case QueryBuilder.PATH:
            case QueryBuilder.PROJECT:
                return new TokenStreamComponents(new PathTokenizer());
            case QueryBuilder.HIST:
                try (HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer()) {
                    return historyAnalyzer.createComponents(fieldName);
                }
            default:
                return new TokenStreamComponents(createPlainFullTokenizer());
        }
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
