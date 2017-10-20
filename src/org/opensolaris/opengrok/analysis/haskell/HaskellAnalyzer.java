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
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis.haskell;

import java.io.Reader;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.JFlexXref;
import org.opensolaris.opengrok.analysis.plain.AbstractSourceCodeAnalyzer;

/**
 * @author Harry Pan
 */

public class HaskellAnalyzer extends AbstractSourceCodeAnalyzer {

    /**
     * Creates a new instance of HaskellAnalyzer
     * @param factory
     */
    protected HaskellAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        SymbolTokenizer=new HaskellSymbolTokenizer(FileAnalyzer.dummyReader);
    }    

    @Override
    protected JFlexXref newXref(Reader reader) {
        return new HaskellXref(reader);
    }
}
