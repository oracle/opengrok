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
 * Copyright (c) 2026, Siyabend Urun <urunsiyabend@gmail.com>.
 */
package org.opengrok.indexer.analysis.groovy;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.analysis.JFlexTokenizer;
import org.opengrok.indexer.analysis.JFlexXref;
import org.opengrok.indexer.analysis.plain.AbstractSourceCodeAnalyzer;

import java.io.Reader;

/**
 * Represents an analyzer for the Groovy language.
 */
@SuppressWarnings("java:S110")
public class GroovyAnalyzer extends AbstractSourceCodeAnalyzer {

    /**
     * Creates a new instance of {@link GroovyAnalyzer}.
     * @param factory instance
     */
    protected GroovyAnalyzer(FileAnalyzerFactory factory) {
        super(factory, () -> new JFlexTokenizer(new GroovySymbolTokenizer(
                AbstractAnalyzer.DUMMY_READER)));
    }

    /**
     * @return {@code "groovy"} to match the OpenGrok-customized definitions
     */
    @Override
    public String getCtagsLang() {
        // convert this from groovy to Groovy when universal-ctags officially supports it
        // also edit the comment above too!!
        return "groovy";
    }


    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20260521_01
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20260521_01; // Edit comment above too!
    }

    /**
     * Creates a wrapped {@link GroovyXref} instance.
     * @return a defined instance
     */
    @Override
    protected JFlexXref newXref(Reader reader) {
        return new JFlexXref(new GroovyXref(reader));
    }
}
