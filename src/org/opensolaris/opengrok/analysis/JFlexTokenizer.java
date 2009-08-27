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

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.Tokenizer;

/**
 * this class was created because of lucene 2.4.1 update which introduced char[] in Tokens instead of String
 *
 * Created on August 24, 2009
 * @author Lubos Kosco
 */

public abstract class JFlexTokenizer extends Tokenizer {

    // just for passing reference
    private Token internal=null;

    // default jflex scanner method
    abstract public Token yylex() throws java.io.IOException ;

    /**
     * This is a convenience method for having correctly generated classes who reuse Tokens and save gc for lucene summarizer
     * you MUST consume the returned token to properly get the null value !
     * @param preusableToken
     * @return null if no more tokens, otherwise a pointer to the modified token
     * @throws java.io.IOException
     */
    @Override
    public final Token next(Token preusableToken) throws java.io.IOException {
        internal=this.yylex();
        if (internal!=null) {
        preusableToken.reinit(internal);
        return preusableToken; }
        preusableToken=null;
        return null;
    }
}
