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
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.Reader;
import java.util.Stack;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 *
 * Generally this is a "template" for all new Tokenizers, so be careful when
 * changing it, it will impact almost ALL symbol tokenizers in OpenGrok ...
 *
 * Created on August 24, 2009
 *
 * @author Lubos Kosco
 */
public abstract class JFlexTokenizer extends Tokenizer {

    protected Stack<Integer> stack = new Stack<>();

    // default jflex scanner methods and variables
    abstract public boolean yylex() throws IOException;

    abstract public void yyreset(Reader reader);

    abstract public void yyclose() throws IOException;

    abstract public void yybegin(int newState);

    abstract public int yystate();    
    
    //TODO can be removed once we figure out jflex generation of empty constructor
    protected JFlexTokenizer(Reader in) {
        super();
    }
    
    protected JFlexTokenizer() {
        super();
    }        

    /**
     * Reinitialize the tokenizer with new reader.
     * @throws java.io.IOException in case of I/O error
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        stack.clear();
        this.yyreset(input);
    }

    @Override
    public final void close() throws IOException {
        super.close();
        this.yyclose();
    }
    protected CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    protected OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    protected PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    protected int finalOffset;

    /**
     * This will re-initalize internal AttributeImpls, or it returns false if
     * end of input Reader ...
     *
     * @return false if no more tokens, otherwise true
     * @throws IOException in case of I/O error
     */
    @Override
    public final boolean incrementToken() throws IOException {
        return this.yylex();
    }

    protected void setAttribs(String str, int start, int end) {
        clearAttributes();
        //FIXME increasing below by one(default) might be tricky, need more analysis
        // after lucene upgrade to 3.5 below is most probably not even needed        
        this.posIncrAtt.setPositionIncrement(1);        
        this.termAtt.setEmpty();
        this.termAtt.append(str);
        this.offsetAtt.setOffset(start, end);
    }

    public void yypush(int newState) {
        this.stack.push(yystate());
        this.yybegin(newState);
    }

    public void yypop() {
        this.yybegin(this.stack.pop());
    }
}
