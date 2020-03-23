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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.io.IOException;
import java.util.List;
%%
%public
%class LineBreakerScanner
%char
%unicode
%type boolean
%eofval{
    return false;
%eofval}
%eof{
    length = yychar;

    /*
     * Following JFlexXref's custom, an empty file or a file ending with EOL
     * produces an additional line of length zero. We also ensure there are two
     * entries to describe the boundaries.
     */
    if (lastHadEOL || offsets.size() <= 1) {
        offsets.add(yychar);
    }
%eof}
%{
    private int length;

    private boolean lastHadEOL;

    private List<Integer> offsets;

    public int getLength() {
        return length;
    }

    /**
     * Sets the required target to write.
     * @param offsets a required instance
     */
    public void setTarget(List<Integer> offsets) {
        this.length = 0;
        this.lastHadEOL = false;
        this.offsets = offsets;
        offsets.add(0);
    }

    /**
     * Call {@link #yylex()} until {@code false}, which consumes all input so
     * that the argument to {@link #setTarget(List)} contains the entire
     * transformation.
     */
    public void consume() throws IOException {
        while (yylex()) {
            //noinspection UnnecessaryContinue
            continue;
        }
    }
%}

%include Common.lexh
%%

[^\n\r]* {EOL}    {
    offsets.add(yychar + yylength());
    lastHadEOL = true;
}

[^\n\r]+    {
    offsets.add(yychar + yylength());
    lastHadEOL = false;
}
