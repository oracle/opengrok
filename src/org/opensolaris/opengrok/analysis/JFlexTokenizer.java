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
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.analysis;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * this class was created because of lucene 2.4.1 update which introduced char[] in Tokens instead of String
 * lucene 3.0.0 uses AttributeSource instead of Tokens to make things even easier :-D
 * lucene 3.5.0 CharTermAttribute will be used
 *
 * Generally this is a "template" for all new Tokenizers, so be carefull when changing it,
 * it will impact almost ALL symbol tokenizers in OpenGrok ...
 *
 * Created on August 24, 2009
 * @author Lubos Kosco
 */

public abstract class JFlexTokenizer extends Tokenizer {

    // default jflex scanner methods and variables
    abstract public boolean yylex() throws IOException;
    abstract public void yyreset(Reader reader);
    abstract public void yyclose() throws IOException;

    /**
     * Reinitialize the tokenizer with new contents.
     *
     * @param contents a char buffer with text to tokenize
     * @param length the number of characters to use from the char buffer
     */
    public final void reInit(char[] contents, int length) {
        yyreset(new CharArrayReader(contents, 0, length));
    }

    @Override
    public final void close() throws IOException {
        yyclose();
    }

    protected CharTermAttribute termAtt= addAttribute(CharTermAttribute.class);
    protected OffsetAttribute offsetAtt= addAttribute(OffsetAttribute.class);
    protected PositionIncrementAttribute posIncrAtt= addAttribute(PositionIncrementAttribute.class);

    /**
     * This will reinitalize internal AttributeImpls, or it returns false if end of input Reader ...
     * @return false if no more tokens, otherwise true
     * @throws java.io.IOException
     */
    @Override
    public final boolean incrementToken() throws java.io.IOException {
        return this.yylex();
    }

    protected void setAttribs(String str, int start, int end) {
        //FIXME increasing below by one(default) might be tricky, need more analysis
        // after lucene upgrade to 3.5 below is most probably not even needed
        this.posIncrAtt.setPositionIncrement(1);
        this.termAtt.setEmpty();
        this.termAtt.append(str);
        this.offsetAtt.setOffset(start, end);
    }
}
