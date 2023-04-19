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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.search.DirectoryEntry;

/**
 * Represents a transformer of lists of files and
 * {@link NullableNumLinesLOC} instances to zip them into a list of
 * {@link DirectoryEntry} instances.
 */
public class FileExtraZipper {

    /**
     * Merge the specified lists by looking up a possible entry in
     * {@code extras} for every element in {@code entries}.
     *
     * @param entries list of {@link DirectoryEntry} instances
     * @param extras  some OpenGrok-analyzed extra metadata
     */
    public void zip(List<DirectoryEntry> entries, List<NullableNumLinesLOC> extras) {

        if (extras == null) {
            return;
        }

        Map<String, NullableNumLinesLOC> byName = indexExtraByName(extras);

        for (DirectoryEntry entry : entries) {
            NullableNumLinesLOC extra = findExtra(byName, entry.getFile());
            entry.setExtra(extra);
        }
    }

    private NullableNumLinesLOC findExtra(Map<String, NullableNumLinesLOC> byName, File fileobj) {
        String key = fileobj.getName();
        return byName.get(key);
    }

    private Map<String, NullableNumLinesLOC> indexExtraByName(List<NullableNumLinesLOC> extras) {
        Map<String, NullableNumLinesLOC> byPath = new HashMap<>();
        for (NullableNumLinesLOC extra : extras) {
            if (extra.getPath() != null) {
                File f = new File(extra.getPath());
                String filename = f.getName();
                byPath.put(filename, extra);
            }
        }
        return byPath;
    }
}
