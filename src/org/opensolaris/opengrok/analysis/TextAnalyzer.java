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
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.opensolaris.opengrok.util.IOUtils;

public abstract class TextAnalyzer extends FileAnalyzer {

    public TextAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    /**
     * Write a cross referenced HTML file reads the source from in
     * @param args a defined instance
     * @return the instance used to write the cross-referencing
     * @throws IOException if an I/O error occurs
     */
    @Override
    public JFlexXref writeXref(WriteXrefArgs args) throws IOException {
        if (args == null) throw new IllegalArgumentException("`args' is null");
        JFlexXref xref = newXref(args.getIn());
        xref.setDefs(args.getDefs());
        xref.setScopesEnabled(scopesEnabled);
        xref.setFoldingEnabled(foldingEnabled);
        xref.annotation = args.getAnnotation();
        xref.project = args.getProject();
        xref.write(args.getOut());
        return xref;
    }

    /**
     * Derived classes should implement to create an xref for the language
     * supported by this analyzer.
     * @param reader the data to produce xref for
     * @return an xref instance
     */
    protected abstract JFlexXref newXref(Reader reader);

    protected Reader getReader(InputStream stream) throws IOException {
        return IOUtils.createBOMStrippedReader(stream);
    }
}
