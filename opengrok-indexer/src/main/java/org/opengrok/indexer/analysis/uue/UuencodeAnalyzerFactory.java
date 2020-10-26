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
 * Copyright (c) 2012, 2013, Constantine A. Murenin &lt;C++@Cns.SU&gt;
 */
package org.opengrok.indexer.analysis.uue;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * @author Constantine A. Murenin &lt;http://cnst.su/&gt;
 */

public class UuencodeAnalyzerFactory extends FileAnalyzerFactory {
    
    private static final String name = "UUEncoded";
    
    private static final String[] SUFFIXES = {
       /**
         * XXX:
         * FreeBSD and DragonFly .fnt files are uuencoded;
         * Minix3 .fnt files are binary. -- 2013-04 cnst
         */
        "UU", "UUE", /*"FNT",*/ "BASE64"
    };

    private static final String[] MAGICS = {
        "begin 4",
        "begin 6",
        "begin 7",
        "begin-b" /* XXX: Should be "begin-base64 ", but there is a limit of magics size*/
    };

    public UuencodeAnalyzerFactory() {
        super(null, null, SUFFIXES, MAGICS, null, "text/plain", AbstractAnalyzer.Genre.PLAIN, name);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new UuencodeAnalyzer(this);
    }
}
