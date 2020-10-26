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
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.javascript;

import java.io.Reader;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.plain.AbstractSourceCodeAnalyzer;

/**
 *
 * @author Lubos Kosco
 *
 */
public class JavaScriptAnalyzer extends AbstractSourceCodeAnalyzer {

    /**
     * Creates a new instance of {@link JavaScriptAnalyzer}.
     * @param factory defined instance for the analyzer
     */
    protected JavaScriptAnalyzer(AnalyzerFactory factory) {
        super(factory, () -> new JFlexTokenizer(new JavaScriptSymbolTokenizer(
                AbstractAnalyzer.DUMMY_READER)));
    }

    /**
     * @return {@code "JavaScript"}
     */
    @Override
    public String getCtagsLang() {
        return "JavaScript";
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20191006_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20191006_00; // Edit comment above too!
    }

    /**
     * Creates a wrapped {@link JavaScriptXref} instance.
     * @param reader the data to produce xref for
     * @return a defined instance
     */
    @Override
    protected JFlexXref newXref(Reader reader) {
        return new JFlexXref(new JavaScriptXref(reader));
    }
}
