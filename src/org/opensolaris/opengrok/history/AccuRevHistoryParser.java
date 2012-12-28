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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;

/**
 * Parse source history for a AccuRev Repository
 *
 * @author Steven Haehn
 */
public class AccuRevHistoryParser implements Executor.StreamHandler {

    private AccuRevRepository repository;
    private History history;
    private boolean foundHistory;

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos Pointer to the {@code AccuRevRepository}
     * @return object representing the file's history
     * @throws HistoryException if a problem occurs while executing p4 command
     */
    History parse(File file, Repository repos) throws HistoryException {

        repository = (AccuRevRepository) repos;

        history = null;
        foundHistory = false;

        String relPath = repository.getDepotRelativePath(file);

        /*
         * When the path given is really just the root to the source
         * workarea, no history is available, create fake.
         */
        if (relPath.equals("/./")) {

            List<HistoryEntry> entries = new ArrayList<HistoryEntry>();

            entries.add(new HistoryEntry(
                    "", new Date(), "OpenGrok", null, "Workspace Root", true));

            history = new History(entries);

        } else {
            try {
                /*
                 * Errors will be logged, so not bothering to add to the output.
                 */
                Executor executor = repository.getHistoryLogExecutor(file);
                executor.exec(true, this);

                /*
                 * Try again because there was no 'keep' history.
                 */
                if (!foundHistory) {
                    executor = repository.getHistoryLogExecutor(file);
                    executor.exec(true, this);
                }
            } catch (IOException e) {
                throw new HistoryException(
                        "Failed to get history for: \""
                        + file.getAbsolutePath() + "\"" + e);
            }
        }

        return history;
    }

    public void processStream(InputStream input) throws IOException {

        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        DateFormat df = repository.getDateFormat();
        String line;
        String user;
        Date date;
        HistoryEntry entry = null;

        history = new History();

        /*
         * Accurev history of an element (directory or file) looks like:
         *
         * element: /./path/to/element eid: 238865 transaction 1486194; purge;
         * 2012/02/28 12:46:55 ; user: tluksha version 2541/1 (2539/1)
         *
         * transaction 1476285; purge; 2012/02/03 12:16:25 ; user: shaehn
         * version 4241/1 (4241/1)
         *
         * transaction 1317224; promote; 2011/05/03 11:37:56 ; user: mtrinh #
         * changes to ngb to compile on windows version 13/93 (1000/2)
         *
         * ...
         *
         * What is of interest then is: user (author), version (revision), date,
         * comment (message)
         *
         * Any lines not recognized are ignored.
         */
        while ((line = in.readLine()) != null) {

            if (line.startsWith("e")) {
                continue;
            } // ignore element, eid

            if (line.startsWith("t")) {             // found transaction

                String data[] = line.split("; ");
                entry = new HistoryEntry();

                foundHistory = true;
                user = data[3].replaceFirst("user: ", "");

                entry.setMessage("");
                entry.setAuthor(user);

                try {
                    date = df.parse(data[2]);
                    entry.setDate(date);
                } catch (ParseException pe) {
                    OpenGrokLogger.getLogger().log(
                            Level.WARNING, "Could not parse date: " + line, pe);
                }

            } else if (line.startsWith("  #")) {  // found comment

                entry.appendMessage(line.substring(3));

            } else if (line.startsWith("  v")) {  // found version

                String data[] = line.split("\\s+");
                entry.setRevision(data[2]);
                entry.setActive(true);
                entries.add(entry);
            }
        }

        history.setHistoryEntries(entries);
    }
}
