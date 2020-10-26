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
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.plain;

import java.io.Reader;
import java.util.function.Supplier;

import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import org.opengrok.indexer.analysis.JFlexXref;

/**
 *
 * @author Lubos Kosco
 * 
 * This class should abstract all analysers that deal with source code.
 * Source code is specific that it has definitions and references.
 * Source code has custom xref generators, depending on symbols.
 * This class should mark the classes that can provide defs and refs.
 * NOTE: SymbolTokenizer gets set for #1376 in PlainAnalyzer::analyze
 * and not part of this class anymore due to changes in lucene 6 .
 * 
 * Anything shared just for source code analyzers should be here,
 * also all interfaces for source code analyzer should start here.
 * 
 * Any child is forced to provide necessary xref and symbol tokenizers,
 * if it fails to do so it will automatically behave like PlainAnalyzer.
 */
public abstract class AbstractSourceCodeAnalyzer extends PlainAnalyzer {

    /**
     * Creates a new instance of abstract analyzer.
     * @param factory defined instance for the analyzer
     * @param symbolTokenizerFactory defined instance for the analyzer
     */
    protected AbstractSourceCodeAnalyzer(AnalyzerFactory factory,
            Supplier<JFlexTokenizer> symbolTokenizerFactory) {
        super(factory, symbolTokenizerFactory);
    }
    
    /**
     * Create an xref for the language supported by this analyzer.
     * @param reader the data to produce xref for
     * @return an xref instance
     */
    @Override
    protected abstract JFlexXref newXref(Reader reader);
}
