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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.pascal;

import java.io.Reader;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.JFlexTokenizer;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.analysis.plain.AbstractSourceCodeAnalyzer;

/**
 *
 * @author Alex Anthony
 */
public class PascalAnalyzer extends AbstractSourceCodeAnalyzer {

    /**
     * Creates a new instance of {@link PascalAnalyzer}.
     * @param factory defined instance for the analyzer
     */
    protected PascalAnalyzer(FileAnalyzerFactory factory) {
        super(factory, new JFlexTokenizer(new PascalSymbolTokenizer(
            FileAnalyzer.dummyReader)));
    }       

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20180125_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180125_00; // Edit comment above too!
    }

    /**
     * Creates a wrapped {@link PascalXref} instance.
     * @param reader the data to produce xref for
     * @return a defined instance
     */
    @Override
    protected JFlexXref newXref(Reader reader) {
        return new JFlexXref(new PascalXref(reader));
    }
    
    @Override
    protected boolean supportsScopes() {
        return true;
    }
}
