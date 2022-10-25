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
package org.opengrok.indexer.index;

import org.apache.lucene.document.Document;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.analysis.NumLinesLOC;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.NumberUtil;

/**
 * Represents a utility class for handling related to {@link NumLinesLOC}.
 */
public class NumLinesLOCUtil {

    /**
     * Reads data, if they exist, from the specified document.
     * @param doc {@link Document} instance
     * @return a defined instance
     */
    public static NullableNumLinesLOC read(Document doc) {
        String path = doc.get(QueryBuilder.D);
        if (path == null) {
            path = doc.get(QueryBuilder.PATH);
        }
        Long numLines = NumberUtil.tryParseLong(doc.get(QueryBuilder.NUML));
        Long loc = NumberUtil.tryParseLong(doc.get(QueryBuilder.LOC));
        return new NullableNumLinesLOC(path, numLines, loc);
    }

    /* private to enforce static */
    private NumLinesLOCUtil() {
    }
}
