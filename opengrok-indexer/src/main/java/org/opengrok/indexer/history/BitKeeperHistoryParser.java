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
 * Copyright (c) 2017, James Service <jas2701@googlemail.com>.
 * Portions Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * BitKeeperHistoryParser handles parsing the output of `bk log` into a history object.
 *
 * @author James Service  {@literal <jas2701@googlemail.com>}
 */
class BitKeeperHistoryParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitKeeperHistoryParser.class);

    /**
     * Parses dates.
     */
    private final SimpleDateFormat dateFormat;
    /**
     * Store entries processed from executor output.
     */
    private final List<HistoryEntry> entries = new ArrayList<>();
    /**
     * Store renamed files processed from executor output.
     */
    private final Set<String> renamedFiles = new TreeSet<>();

    /**
     * Constructor to construct the thing to be constructed.
     *
     * @param datePattern a simple date format string
     */
    BitKeeperHistoryParser(String datePattern) {
        dateFormat = new SimpleDateFormat(datePattern);
    }

    /**
     * Returns the history that has been created.
     *
     * @return history a history object
     */
    History getHistory() {
        return new History(entries, new ArrayList<>(renamedFiles));
    }

    /**
     * Process the output of a `bk log` command.
     *
     * Each input line should be in the following format:
     * either
     *   D FILE\tREVISION\tDATE\tUSER(\tRENAMED_FROM)?
     * or
     *   C COMMIT_MESSAGE_LINE
     *
     * BitKeeper always outputs the file name relative to its root directory, which is nice for us since that's what
     * History expects.
     *
     * @param input the executor input stream
     * @throws IOException if the stream reader throws an IOException
     */
    @Override
    public void processStream(InputStream input) throws IOException {
        HistoryEntry entry = null;

        final BufferedReader in = new BufferedReader(new InputStreamReader(input));
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            if (line.startsWith("D ")) {
                if (entry != null) {
                    entries.add(entry);
                    entry = null;
                }

                final String[] fields = line.substring(2).split("\t");
                final HistoryEntry newEntry = new HistoryEntry();
                try {
                    if (fields[0].equals("ChangeSet")) {
                        continue;
                    }
                    newEntry.addFile(fields[0].intern());
                    newEntry.setRevision(fields[1]);
                    newEntry.setDate(dateFormat.parse(fields[2]));
                    newEntry.setAuthor(fields[3]);
                    newEntry.setActive(true);
                } catch (final Exception e) {
                    LOGGER.log(Level.SEVERE, "Error: malformed BitKeeper log output {0}", line);
                    continue;
                }

                entry = newEntry;
                if (fields.length == 5) {
                    renamedFiles.add(fields[4]);
                }
            } else if (line.startsWith("C ")) {
                if (entry != null) {
                    final String messageLine = line.substring(2);
                    entry.appendMessage(messageLine);
                }
            }
        }

        if (entry != null) {
            entries.add(entry);
        }
    }
}
