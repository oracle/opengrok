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

import java.io.Reader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;

public class PathTokenizer extends Tokenizer {

    // below should be '/' since we try to convert even windows file separators to unix ones
    private static final char dirSep = '/';
    private boolean dot = false;
    private final static char ADOT[]={'.'};
    private final TermAttribute termAtt = addAttribute(TermAttribute.class);    

    public PathTokenizer(Reader input) {
        super(input);
    }

    @Override
    public final boolean incrementToken() throws java.io.IOException {
        if (dot) {
            dot = false;
            termAtt.setTermBuffer(ADOT,0,1);
            return true;
        }

        char buf[] = new char[64];
        int c;
        int i = 0;
        do {
            c = input.read();
            if (c == -1) {
                return false;
            }
        } while (c == dirSep);

        do {
            if (i >= buf.length) {
                char nb[] = new char[buf.length * 2];
                System.arraycopy(buf, 0, nb, 0, buf.length);
                buf = nb;
            }
            buf[i++] = Character.toLowerCase((char) c);
            c = input.read();
        } while (c != dirSep && c != '.' && !Character.isWhitespace(c) && c != -1);
        if (c == '.') {
            dot = true;
        }
        termAtt.setTermBuffer(buf, 0, i);       
        return true;
    }
}
