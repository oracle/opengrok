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

import java.util.LinkedList;
import java.util.NoSuchElementException;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;

public final class List2TokenStream extends TokenStream {
    private LinkedList<String> l;
    private String[] subTokens;
    private int si;
    public List2TokenStream(LinkedList<String> l){
        this.l = l;
        subTokens = null;
    }
    public Token next() {
        if(subTokens == null || subTokens.length == si) {
            try {
                String tok = l.remove();
                if(tok != null) {
                    if(tok.indexOf('.') > 0) {
                        subTokens = tok.split("[^a-z0-9A-Z_]+");
                        //System.err.println("split " + tok + " into "+ subTokens.length);
                    } else {
                        subTokens = null;
                        return new Token(tok,0,0);
                    }
                    si = 0;
                } else {
                    return null;
                }
            } catch (NoSuchElementException nop) {
                return null;
            }
        }
        if (si < subTokens.length) {
            return new Token(subTokens[si++], 0, 0);
        } else {
            return null;
        }
    }
    
    public void close() {
        l = null;
    }
}
