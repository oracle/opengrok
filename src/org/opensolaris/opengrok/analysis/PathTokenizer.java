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

import java.io.Reader;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.Tokenizer;

public class PathTokenizer extends Tokenizer {

    private static final char dirSep = '/';
    private boolean dot = false;

    public PathTokenizer(Reader input) {
        super(input);
    }

    public final Token next() throws java.io.IOException {
        if (dot) {
            dot = false;
            return new Token(".", 0, 0);
        }

        char buf[] = new char[64];
        int c;
        int i = 0;
        do {
            c = input.read();
            if (c == -1) {
                return null;
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
        return (new Token(String.copyValueOf(buf, 0, i), 0, 0));
    }
}
