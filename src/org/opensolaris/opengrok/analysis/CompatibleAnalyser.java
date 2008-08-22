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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.io.Reader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.opensolaris.opengrok.analysis.plain.PlainFullTokenizer;
import org.opensolaris.opengrok.analysis.plain.PlainSymbolTokenizer;

public class CompatibleAnalyser extends Analyzer {
    PathAnalyzer pather;
    HistoryAnalyzer historer;
    
    public CompatibleAnalyser() {
        historer = new HistoryAnalyzer();
        pather  = new PathAnalyzer();
    }
    
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if ("full".equals(fieldName)) {
            return new PlainFullTokenizer(reader);
        } else if ("refs".equals(fieldName)) {
            return new PlainSymbolTokenizer(reader);
        } else if ("defs".equals(fieldName)) {
            return new PlainSymbolTokenizer(reader);
        } else if ("path".equals(fieldName) || "project".equals(fieldName)) {
            return pather.tokenStream(fieldName, reader);
        } else if ("hist".equals(fieldName)) {
            return historer.tokenStream(fieldName, reader);
        } 
        return new PlainFullTokenizer(reader);
    }
}
