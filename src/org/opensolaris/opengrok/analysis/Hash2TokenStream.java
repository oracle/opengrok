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
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public final class Hash2TokenStream extends TokenStream {
    private Iterator<String> terms = Collections.emptyIterator();
    private final Iterator<String> keys;
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public Hash2TokenStream(Set<String> symbols){
        keys=symbols.iterator();
    }

    @Override
    public final boolean incrementToken() throws java.io.IOException {
        clearAttributes();

        // Loop until we have found terms or there are no more keys to read.
        while (!terms.hasNext()) {
            if (keys.hasNext()) {
                String term = keys.next();
                terms = Arrays.asList(term.split("[^a-zA-Z_0-9]+")).iterator();
            } else {
                // No more keys to read. Signal that there are no more
                // tokens in the stream.
                return false;
            }
        }

        // Terms is non-empty when we get here. Pick one.
        termAtt.setEmpty();
        termAtt.append(terms.next());
        return true;
    }
}
