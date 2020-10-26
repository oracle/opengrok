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
 */
package org.opengrok.indexer.analysis.plain;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

public class XMLAnalyzerFactory extends FileAnalyzerFactory {
    
    private static final String name = "XML";
    
    private static final String[] SUFFIXES = {
        "HTML", "HTM", "XML", "ASPX", "ASCX", "ASAX", "MASTER", "XAML"
    };

    private static final String[] MAGICS = {
        "<htm", "<HTM", "<?xm", "<?Xm", "<?XM",
        "<!--", "<!EN", "<!DO", "<tit",
        "<TIT", "<XML", "<xml", "<HEA", "<hea"
    };

    public XMLAnalyzerFactory() {
        super(null, null, SUFFIXES, MAGICS, null, "text/html", AbstractAnalyzer.Genre.PLAIN, name);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new XMLAnalyzer(this);
    }
}
