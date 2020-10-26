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
 * Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.verilog;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.plain.AbstractSourceCodeAnalyzer;

import java.io.Reader;

/**
 * Represents an analyzer for the SystemVerilog language.
 */
public class VerilogAnalyzer extends AbstractSourceCodeAnalyzer {

    /**
     * Creates a new instance of {@link VerilogAnalyzer}.
     * @param factory instance
     */
    protected VerilogAnalyzer(FileAnalyzerFactory factory) {
        super(factory, () -> new JFlexTokenizer(new VerilogSymbolTokenizer(
                AbstractAnalyzer.DUMMY_READER)));
    }

    /**
     * @return {@code "SystemVerilog"}
     */
    @Override
    public String getCtagsLang() {
        return "SystemVerilog";
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20190117_04
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20190117_04; // Edit comment above too!
    }

    /**
     * Creates a wrapped {@link VerilogXref} instance.
     * @return a defined instance
     */
    @Override
    protected JFlexXref newXref(Reader reader) {
        return new JFlexXref(new VerilogXref(reader));
    }
}
