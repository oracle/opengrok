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
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.executables;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;
import org.opengrok.indexer.analysis.archive.ZipMatcherBase;

public final class JarAnalyzerFactory extends FileAnalyzerFactory {
    
    private static final String name = "Jar";
    
    private static final String[] SUFFIXES = {
        "JAR", "WAR", "EAR"
    };

    private static final Matcher MATCHER = new ZipMatcherBase() {

        @Override
        public String description() {
            return "PK\\{3}\\{4} magic signature with 0xCAFE Extra Field ID";
        }

        @Override
        public AnalyzerFactory forFactory() {
            return JarAnalyzerFactory.DEFAULT_INSTANCE;
        }

        @Override
        protected boolean doesCheckExtraFieldID() {
            return true;
        }

        @Override
        protected Integer strictExtraFieldID() {
            return 0xCAFE;
        }
    };

    public static final JarAnalyzerFactory DEFAULT_INSTANCE =
            new JarAnalyzerFactory();

    private JarAnalyzerFactory() {
        super(null, null, SUFFIXES, null, MATCHER, null, AbstractAnalyzer.Genre.XREFABLE, name);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new JarAnalyzer(this);
    }
}
