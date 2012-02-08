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
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.util.Iterator;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public final class List2TokenStream extends TokenStream {

    private Iterator<String> it;
    private String[] subTokens;
    private int si;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public List2TokenStream(List<String> l) {
        it = l.iterator();
    }

    @Override
    public boolean incrementToken() {
        if (!it.hasNext()) {
            // reached end of stream
            return false;
        }

        if (subTokens == null || subTokens.length == si) {
            String tok = it.next();
            if (tok == null) {
                return false;
            }
            if (tok.indexOf('.') > 0) {
                subTokens = tok.split("[^a-z0-9A-Z_]+");
            } else {
                subTokens = null;
                termAtt.setEmpty();
                termAtt.append(tok);
                return true;
            }
            si = 0;
        }
        if (si < subTokens.length) {
            termAtt.setEmpty();
            termAtt.append(subTokens[si++]);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        it = null;
    }
}
