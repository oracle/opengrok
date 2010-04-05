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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

public final class Hash2TokenStream extends TokenStream {
    int i=0;
    String term;
    String terms[];
    Iterator<String> keys;
    private final TermAttribute termAtt= (TermAttribute) addAttribute(TermAttribute.class);

    public Hash2TokenStream(Set<String> symbols){
        keys = symbols.iterator();
    }
    
    @Override
    public boolean incrementToken() throws java.io.IOException {
        while (true) {
            if (i <= 0) {
                if (keys.hasNext()) {
                    term = keys.next();
                    terms = term.split("[^a-zA-Z_0-9]+");
                    i = terms.length;
                    if (i > 0) {
                        termAtt.setTermBuffer(terms[--i]);
                        return true;
                    } else {
                        // no tokens found in this key, try next
                        continue;
                    }
                } else {
                    return false;
                }
            } else {
                //System.out.println("Returning " + term + h.get(term));
                termAtt.setTermBuffer(terms[--i]);
                return true;
            }
        }
    }

    @Override
    public void close() {
        // Nothing to close
    }    
}
