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
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.Reader;
import org.apache.lucene.analysis.Analyzer;
import org.opensolaris.opengrok.analysis.plain.PlainFullTokenizer;
import org.opensolaris.opengrok.analysis.plain.PlainSymbolTokenizer;

public class CompatibleAnalyser extends Analyzer {

    public CompatibleAnalyser() {
        super(new Analyzer.PerFieldReuseStrategy());
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        if ("full".equals(fieldName)) {
            final PlainFullTokenizer plainfull = new PlainFullTokenizer(reader);
            TokenStreamComponents tsc_pf = new TokenStreamComponents(plainfull) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    plainfull.reInit(reader);
                    super.setReader(reader);
                }
            };
            return tsc_pf;
        } else if ("refs".equals(fieldName)) {
            final PlainSymbolTokenizer plainref = new PlainSymbolTokenizer(reader);
            TokenStreamComponents tsc_pr = new TokenStreamComponents(plainref) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    plainref.reInit(reader);
                    super.setReader(reader);
                }
            };
            return tsc_pr;
        } else if ("defs".equals(fieldName)) {
            final PlainSymbolTokenizer plaindef = new PlainSymbolTokenizer(reader);
            TokenStreamComponents tsc_pd = new TokenStreamComponents(plaindef) {
                @Override
                protected void setReader(final Reader reader) throws IOException {
                    plaindef.reInit(reader);
                    super.setReader(reader);
                }
            };
            return tsc_pd;
        } else if ("path".equals(fieldName)) {
            final PathTokenizer pathtokenizer = new PathTokenizer(reader);
            TokenStreamComponents tsc_path = new TokenStreamComponents(pathtokenizer);
            return tsc_path;
        } else if ("project".equals(fieldName)) {
            final PathTokenizer projecttokenizer = new PathTokenizer(reader);
            TokenStreamComponents tsc_project = new TokenStreamComponents(projecttokenizer);
            return tsc_project;
        } else if ("hist".equals(fieldName)) {
            return new HistoryAnalyzer().createComponents(fieldName, reader);
        }
        return new TokenStreamComponents(new PlainFullTokenizer(reader));
    }
}
