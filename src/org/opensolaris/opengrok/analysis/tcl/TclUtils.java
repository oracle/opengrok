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
 * Copyright (c) 2017, cfraire@me.com.
 */

package org.opensolaris.opengrok.analysis.tcl;

import java.io.IOException;
import org.opensolaris.opengrok.analysis.JFlexXref;

/**
 * Represents a container for Tcl-related utility methods.
 */
public class TclUtils {
    /**
     * Write {@code whsp} to the {@code xref} output -- if the whitespace does
     * not contain any LFs then the full String is written; otherwise, pre-LF
     * spaces are condensed as usual.
     * @param xref the target instance
     * @param whsp a defined whitespace capture
     * @throws java.io.IOException if an output error occurs
     */
    public static void writeWhitespace(JFlexXref xref, String whsp)
            throws IOException {
        int i;
        if ((i = whsp.indexOf("\n")) == -1) {
            xref.out.write(whsp);
        } else {
            int numlf = 1, off = i + 1;
            while ((i = whsp.indexOf("\n", off)) != -1) {
                ++numlf;
                off = i + 1;
            }
            while (numlf-- > 0) xref.startNewLine();
            if (off < whsp.length()) xref.out.write(whsp.substring(off));
        }
    }

    /** private to enforce static */
    private TclUtils() {
    }
}
