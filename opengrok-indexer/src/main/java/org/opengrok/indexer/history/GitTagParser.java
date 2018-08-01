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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.TreeSet;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code git log} command
 * into a set of tag entries.
 */
public class GitTagParser implements Executor.StreamHandler {
    /**
     * Store tag entries created by {@link processStream()}.
     */
    private final TreeSet<TagEntry> entries = new TreeSet<>();
    
    private final String tags;
    
    GitTagParser(String tags) {
        this.tags = tags;
    }
    
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
        String hash = null;
        Date date = null;
        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("commit")) {
                    String parts[] = line.split(":");
                    if (parts.length < 2) {
                        throw new IOException("Tag line contains more than 2 columns: " + line);
                    }
                    hash = parts[1];
                }
                if (line.startsWith("Date")) {
                    String parts[] = line.split(":");
                    if (parts.length < 2) {
                        throw new IOException("Tag line contains more than 2 columns: " + line);
                    }
                    date = new Date((long) (Integer.parseInt(parts[1])) * 1000);
                }
            }
        }

        // Git can have tags not pointing to any commit, but tree instead
        // Lets use Unix timestamp of 0 for such commits
        if (date == null) {
            date = new Date(0);
        }
        entries.add(new GitTagEntry(hash, date, tags));
    }
}
