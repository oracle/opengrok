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

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

/**
 * this class was created because of lucene 2.4.1 update which introduced char[] in Tokens instead of String
 * lucene 3.0.0 uses AttributeSource instead of Tokens to make things even easier :-D
 *
 * Generally this is a "template" for all new Tokenizers, so be carefull when changing it,
 * it will impact almost ALL symbol tokenizers in OpenGrok ...
 *
 * Created on August 24, 2009
 * @author Lubos Kosco
 */

public abstract class JFlexTokenizer extends Tokenizer {

    // default jflex scanner methods and variables
    abstract public boolean yylex() throws java.io.IOException ;
    
    protected TermAttribute termAtt= (TermAttribute) addAttribute(TermAttribute.class);
    protected OffsetAttribute offsetAtt=(OffsetAttribute) addAttribute(OffsetAttribute.class);
    //fixme increasing below might be tricky, need more analysis
    protected PositionIncrementAttribute posIncrAtt= (PositionIncrementAttribute) addAttribute(PositionIncrementAttribute.class);

    /**
     * This will reinitalize internal AttributeImpls, or it returns false if end of input Reader ...
     * @return false if no more tokens, otherwise true
     * @throws java.io.IOException
     */    
    @Override
    public boolean incrementToken() throws java.io.IOException {
        return this.yylex();        
    }

    protected void setAttribs(char[] startTermBuffer, int termBufferOffset, int termBufferLength, int start, int end) {
        this.posIncrAtt.setPositionIncrement(1);
        this.termAtt.setTermBuffer(startTermBuffer,termBufferOffset,termBufferLength);
        this.offsetAtt.setOffset(start, end);
    }

    protected void setAttribs(String str, int start, int end) {
        this.setAttribs(str.toCharArray(),0,str.length(),start, end);
    }
}
