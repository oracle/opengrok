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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis.sql;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.plain.PlainAnalyzer;
import org.opensolaris.opengrok.history.Annotation;

public class SQLAnalyzer extends PlainAnalyzer {
    public static String[] suffixes = {
        "SQL",
    };

    private final SQLXref xref = new SQLXref((Reader)null);

    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write HTML cross-reference
     */
    public void writeXref(Writer out) throws IOException {
        xref.reInit(content, len);
        // TODO xref.setDefs(defs);
        xref.write(out);
    }

    /**
     * Write a cross referenced HTML file. Reads the source from
     * an input stream.
     *
     * @param in input source
     * @param out output xref writer
     * @param annotation annotation for the file (could be null)
     */
    public static void writeXref(InputStream in, Writer out,
                                 Annotation annotation) throws IOException {
        SQLXref xref = new SQLXref(in);
        xref.annotation = annotation;
        xref.write(out);
    }
}
