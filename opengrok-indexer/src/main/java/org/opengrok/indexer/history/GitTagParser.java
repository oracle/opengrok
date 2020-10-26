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
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import static org.opengrok.indexer.history.HistoryEntry.TAGS_SEPARATOR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * handles parsing the output of the {@code git log} command
 * into a set of tag entries.
 */
class GitTagParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitTagParser.class);

    /**
     * E.g. as follows and producing a single capture, $1, for a tag name.
     * <p>506e0a1ddf50341a0603af27ecc254ccb72d7dcb:1473681887:tag: 0.12.1.6, origin/0.12-stable:
     * <p>d305482d0acf552ccd290d6133a52547b8da16be:1427209918:tag: 0.12.1.5:
     */
    private static final Pattern PRETTY_TAG_MATCHER =
            Pattern.compile("tag:\\s+(\\S[^,:]*)(?:,\\s+|:)");

    /**
     * Stores the externally provided set.
     */
    private final TreeSet<TagEntry> entries;

    GitTagParser(TreeSet<TagEntry> entries) {
        this.entries = entries;
    }
    
    @Override
    public void processStream(InputStream input) throws IOException {
        ArrayList<String> tagNames = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(GitRepository.newLogReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String originalLine = line;
                String hash;
                Date date;
                int i;

                /*
                 * E.g.
                 * 506e0a1ddf50341a0603af27ecc254ccb72d7dcb:1473681887:tag: 0.12.1.6, origin/0.12-stable:
                 * d305482d0acf552ccd290d6133a52547b8da16be:1427209918:tag: 0.12.1.5:
                 */
                if ((i = line.indexOf(":")) == -1) {
                    LOGGER.log(Level.WARNING, "Bad tags log for %H: {0}", originalLine);
                    continue;
                }
                hash = line.substring(0, i);
                line = line.substring(i + 1);

                if ((i = line.indexOf(":")) == -1) {
                    LOGGER.log(Level.WARNING, "Bad tags log for %at: {0}", originalLine);
                    continue;
                }
                String timestamp = line.substring(0, i);
                line = line.substring(i + 1);
                date = new Date((long) (Integer.parseInt(timestamp)) * 1000);

                /*
                 * GitHub's louie0817 identified a problem where multiple tags
                 * for the same Git commit were not recognized since OpenGrok's
                 * TagEntry equals() compares solely the date when the "repo
                 * does not use linear numbering," and formerly OpenGrok was
                 * parsing individual tags as separate entries. With
                 * louie0817's suggested bulk command, the tag names appear on
                 * the same line and can therefore be attached to the same
                 * entry before inserting to the map.
                 */
                tagNames.clear();
                Matcher m = PRETTY_TAG_MATCHER.matcher(line);
                while (m.find()) {
                    tagNames.add(m.group(1)); // $1
                }
                if (!tagNames.isEmpty()) {
                    /*
                     * See Repository assignTagsInHistory() where multiple
                     * identified tags are joined with comma-space.
                     */
                    String joinedTagNames = String.join(TAGS_SEPARATOR, tagNames);
                    GitTagEntry tagEntry = new GitTagEntry(hash, date, joinedTagNames);
                    entries.add(tagEntry);
                }
            }
        }
    }
}
