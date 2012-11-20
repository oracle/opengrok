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

import java.io.Reader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public final class Hash2Tokenizer extends Tokenizer {
    int i=0;
    String term;
    String terms[];
    Iterator<String> keys;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private int finalOffset;

    public Hash2Tokenizer(Reader reader){
        super(reader);
        keys=new HashSet<String>().iterator();
    }
    
    public Hash2Tokenizer(Set<String> symbols){
        super(AnalyzerGuru.dummyR);
        keys=symbols.iterator();
    }
    
    public void reInit(Set<String> symbols) {
        keys = symbols.iterator();
    }

    @Override
    public final boolean incrementToken() throws java.io.IOException {
        clearAttributes();
        while (i <= 0) {            
            if (keys.hasNext()) {
                term = keys.next();
                terms = term.split("[^a-zA-Z_0-9]+");
                i = terms.length;
                if (i > 0) {
                    termAtt.setEmpty();
                    termAtt.append(terms[--i]);
                    return true;
                }
                // no tokens found in this key, try next
                continue;
            }
            return false;
        }
        finalOffset=0;
        termAtt.setEmpty();
        termAtt.append(terms[--i]);
        return true;
    }
}
