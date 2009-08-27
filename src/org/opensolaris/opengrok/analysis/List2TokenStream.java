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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis;

import java.util.List;
import java.util.logging.Level;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.opensolaris.opengrok.OpenGrokLogger;

public final class List2TokenStream extends TokenStream {

    private List<String> l;
    private String[] subTokens;
    private int si;

    public List2TokenStream(List<String> l) {
        this.l = l;
        subTokens = null;
    }

    @Override
    public Token next(Token reusableToken) {
        if (l == null || l.isEmpty()) {
            OpenGrokLogger.getLogger().log(Level.FINE, "Cannot get tokens from an empty list!");
            return null;
        }

        if (subTokens == null || subTokens.length == si) {
            String tok = l.remove(0);
            if (tok == null) {
                return null;
            } else {
                if (tok.indexOf('.') > 0) {
                    subTokens = tok.split("[^a-z0-9A-Z_]+");
                } else {
                    subTokens = null;
                    reusableToken.reinit(tok,0,0);
                    return reusableToken;
                }
                si = 0;
            }
        }
        if (si < subTokens.length) {
            reusableToken.reinit(subTokens[si++], 0, 0);
            return reusableToken;
        } else {
            return null;
        }
    }

    @Override
    public void close() {
        l = null;
    }
}
