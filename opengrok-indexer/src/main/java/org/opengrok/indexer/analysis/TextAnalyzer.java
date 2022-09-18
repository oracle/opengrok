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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.opengrok.indexer.util.IOUtils;

public abstract class TextAnalyzer extends FileAnalyzer {

    /**
     * Creates a new instance of {@link TextAnalyzer}.
     * @param factory defined instance for the analyzer
     */
    protected TextAnalyzer(AnalyzerFactory factory) {
        super(factory);
    }

    /**
     * Creates a new instance of {@link TextAnalyzer}.
     * @param factory defined instance for the analyzer
     * @param symbolTokenizerFactory defined instance for the analyzer
     */
    protected TextAnalyzer(AnalyzerFactory factory,
            Supplier<JFlexTokenizer> symbolTokenizerFactory) {
        super(factory, symbolTokenizerFactory);
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20171223_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20171223_00; // Edit comment above too!
    }

    /**
     * Write a cross referenced HTML file reads the source from in.
     * @param args a defined instance
     * @return the instance used to write the cross-referencing
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Xrefer writeXref(WriteXrefArgs args) throws IOException {
        if (args == null) {
            throw new IllegalArgumentException("`args' is null");
        }
        Xrefer xref = newXref(args.getIn());
        xref.setDefs(args.getDefs());
        xref.setScopesEnabled(scopesEnabled);
        xref.setFoldingEnabled(foldingEnabled);
        xref.setAnnotation(args.getAnnotation());
        xref.setProject(args.getProject());
        xref.write(args.getOut());
        return xref;
    }

    /**
     * Derived classes should implement to create an xref for the language
     * supported by this analyzer.
     * @param reader the data to produce xref for
     * @return an xref instance
     */
    protected abstract Xrefer newXref(Reader reader);

    /**
     * Gets a BOM-stripped {@link Reader} (default UTF-8 charset) of the
     * specified {@code stream}, wrapped in a {@link ZeroReader}.
     * @param stream input stream
     * @return Reader instance
     */
    protected Reader getReader(InputStream stream) throws IOException {
        // sourceRoot is read with UTF-8 as a default.
        return new ZeroReader(IOUtils.createBOMStrippedReader(stream,
                StandardCharsets.UTF_8.name()));
    }
}
