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
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import static org.opengrok.indexer.history.HistoryEntry.TAGS_SEPARATOR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.TreeSet;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code bzr tags} command
 * into a set of tag entries.
 */
public class BazaarTagParser implements Executor.StreamHandler {
    
    /**
     * Store tag entries created by processStream.
     */
    private final TreeSet<TagEntry> entries = new TreeSet<>();
    
    /**
     * Returns the set of entries that has been created.
     *
     * @return entries a set of tag entries
     */
    public TreeSet<TagEntry> getEntries() {
        return entries;
    }
    
    @Override
    public void processStream(InputStream input) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("  *");
                if (parts.length < 2) {
                    throw new IOException("Tag line contains more than 2 columns: " + line);
                }
                // Grrr, how to parse tags with spaces inside?
                // This solution will loose multiple spaces;-/
                String tag = parts[0];
                for (int i = 1; i < parts.length - 1; ++i) {
                    tag += " " + parts[i];
                }
                TagEntry tagEntry = new BazaarTagEntry(Integer.parseInt(parts[parts.length - 1]), tag);
                // Bazaar lists multiple tags on more lines. We need to merge those into single TagEntry
                TagEntry higher = entries.ceiling(tagEntry);
                if (higher != null && higher.equals(tagEntry)) {
                    // Found in the tree, merge tags
                    entries.remove(higher);
                    tagEntry.setTags(higher.getTags() + TAGS_SEPARATOR + tag);
                }
                entries.add(tagEntry);
            }
        }
    }
}
