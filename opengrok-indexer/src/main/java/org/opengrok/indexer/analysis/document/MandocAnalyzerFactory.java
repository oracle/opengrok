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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.document;

import java.io.IOException;
import java.io.InputStream;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

public class MandocAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "Mandoc";

    public static final Matcher MATCHER = new Matcher() {
        @Override
        public AnalyzerFactory isMagic(byte[] contents, InputStream in)
                throws IOException {
            return RuntimeEnvironment.getInstance().getMandoc() != null ?
                getTrueMenMatcher().isMagic(contents, in) :
                getTrueMdocMatcher().isMagic(contents, in);
        }

        @Override
        public AnalyzerFactory forFactory() {
            return getTrueMenMatcher().forFactory();
        }
    };

    public static final MandocAnalyzerFactory DEFAULT_INSTANCE =
        new MandocAnalyzerFactory();

    protected MandocAnalyzerFactory() {
        super(null, null, null, null, MATCHER, "text/plain", AbstractAnalyzer.Genre.PLAIN, NAME);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new MandocAnalyzer(this);
    }

    // Because DEFAULT_INSTANCE during its initialization uses the MATCHER,
    // while at the same time the DocumentMatcher in its initialization takes
    // a FileAnalyzerFactory, and because we want the instances to be the same
    // instance, then defer initialization of the DocumentMatcher using the
    // "16.6 Lazy initialization holder class idiom," written by Brian Goetz
    // and Tim Peierls with assistance from members of JCP JSR-166 Expert Group
    // and released to the public domain, as explained at
    // http://creativecommons.org/licenses/publicdomain .
    private static class TrueMatcherHolder {
        /**
         * The prologue, which consists of the Dd, Dt, and Os macros in that
         * order, is required for every document.
         *
         * As {@link TroffXref} does not present mdoc(5) documents well, even
         * if no mandoc binary is configured, then we want a
         * {@link MandocAnalyzer} that presents a plain-text cross-referencing.
         */
        public static final DocumentMatcher MDOCMATCHER = new DocumentMatcher(
            DEFAULT_INSTANCE, new String[] {".Dd", ".Dt", ".Os"});

        /**
         * As with {@code MDOCMATCHER} except that when a mandoc binary is
         * configured, then man(5) documents with a .TH also will use the
         * {@link MandocAnalyzer}.
         */
        public static final DocumentMatcher MENMATCHER = new DocumentMatcher(
            DEFAULT_INSTANCE, new String[] {".Dd", ".Dt", ".Os", ".TH"});
    }

    /** Gets a matcher that is mdoc(5)-specific. */
    private static DocumentMatcher getTrueMdocMatcher() {
        return TrueMatcherHolder.MDOCMATCHER;
    }

    /** Gets a matcher that matches mdoc(5) and man(5). */
    private static DocumentMatcher getTrueMenMatcher() {
        return TrueMatcherHolder.MENMATCHER;
    }
}
