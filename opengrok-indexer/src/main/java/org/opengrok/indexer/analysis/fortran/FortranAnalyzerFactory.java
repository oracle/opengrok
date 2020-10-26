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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.fortran;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

public class FortranAnalyzerFactory extends FileAnalyzerFactory {

    private static final String name = "Fortran";

    /**
     * Includes Fortran free-form extension F90 and its successors, per
     * "Fortran"¹: F95, F03, F08, F15.
     * <p>
     * "Intel® Fortran Compiler -- effect of file extensions on source form"
     * enumerates "F", "FTN", and "FOR" as "Fortran fixed-form source" -- which
     * is not specifically handled by {@link FortranAnalyzer} -- but "F" is
     * also kept as an originally-supported suffix.
     * <p>
     * ¹https://en.wikipedia.org/wiki/Fortran, Wikipedia, Creative Commons
     * Attribution-ShareAlike 3.0,
     * https://en.wikipedia.org/wiki/Wikipedia:Text_of_Creative_Commons_Attribution-ShareAlike_3.0_Unported_License
     */    
    private static final String[] SUFFIXES = {
        "F",
        "F90",
        "F95",
        "F03",
        "F08",
        "F15"};
     
    public FortranAnalyzerFactory() {
        super(null, null, SUFFIXES, null, null, "text/plain", AbstractAnalyzer.Genre.PLAIN, name);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new FortranAnalyzer(this);
    }
}
