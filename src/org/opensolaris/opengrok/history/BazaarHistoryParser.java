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
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;

/**
 * Parse a stream of Bazaar log comments.
 */
class BazaarHistoryParser implements Executor.StreamHandler {

    private String myDir;
    private List<HistoryEntry> entries = new ArrayList<HistoryEntry>(); //NOPMD
    private BazaarRepository repository=new BazaarRepository(); //NOPMD

    BazaarHistoryParser(BazaarRepository repository) {
        this.repository = repository;
        myDir = repository.getDirectoryName() + File.separator;
    }

    History parse(File file, String sinceRevision) throws HistoryException {
        try {
            Executor executor = repository.getHistoryLogExecutor(file, sinceRevision);
            int status = executor.exec(true, this);

            if (status != 0) {
                throw new HistoryException("Failed to get history for: \"" +
                                           file.getAbsolutePath() + "\" Exit code: " + status);
            }
        } catch (IOException e) {
            throw new HistoryException("Failed to get history for: \"" +
                                       file.getAbsolutePath() + "\"", e);
        }

        // If a changeset to start from is specified, remove that changeset
        // from the list, since only the ones following it should be returned.
        // Also check that the specified changeset was found, otherwise throw
        // an exception.
        if (sinceRevision != null) {
            repository.removeAndVerifyOldestChangeset(entries, sinceRevision);
        }

        return new History(entries);
    }

    /**
     * Process the output from the log command and insert the HistoryEntries
     * into the history field.
     *
     * @param input The output from the process
     * @throws java.io.IOException If an error occurs while reading the stream
     */
    @Override
    public void processStream(InputStream input) throws IOException {
        DateFormat df = repository.getDateFormat();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String s;

        HistoryEntry entry = null;
        int state = 0;
        while ((s = in.readLine()) != null) {
            if ("------------------------------------------------------------".equals(s)) {
                if (entry != null && state > 2) {
                    entries.add(entry);
                }
                entry = new HistoryEntry();
                entry.setActive(true);
                state = 0;
                continue;
            }

            switch (state) {
                case 0:
                    // First, go on until revno is found.
                    if (s.startsWith("revno:")) {
                        String rev[] = s.substring("revno:".length()).trim().split(" ");
                        entry.setRevision(rev[0]);
                        ++state;
                    }
                    break;
                case 1:
                    // Then, look for committer.
                    if (s.startsWith("committer:")) {
                        entry.setAuthor(s.substring("committer:".length()).trim());
                        ++state;
                    }
                    break;
                case 2:
                    // And then, look for timestamp.
                    if (s.startsWith("timestamp:")) {
                        try {
                            Date date = df.parse(s.substring("timestamp:".length()).trim());
                            entry.setDate(date);
                        } catch (ParseException e) {
                            OpenGrokLogger.getLogger().log(Level.WARNING,
                                    "Failed to parse history timestamp:" + s, e);
                        }
                        ++state;
                    }
                    break;
                case 3:
                    // Expect the commit message to follow immediately after
                    // the timestamp, and that everything up to the list of
                    // modified, added and removed files is part of the commit
                    // message.
                    if (s.startsWith("modified:") || s.startsWith("added:") || s.startsWith("removed:")) {
                        ++state;
                    } else if (s.startsWith("  ")) {
                        // Commit messages returned by bzr log -v are prefixed
                        // with two blanks.
                        entry.appendMessage(s.substring(2));
                    }
                    break;
                case 4:
                    // Finally, store the list of modified, added and removed
                    // files. (Except the labels.)
                    if (!(s.startsWith("modified:") || s.startsWith("added:") || s.startsWith("removed:"))) {
                        // The list of files is prefixed with blanks.
                        s = s.trim();

                        int idx = s.indexOf(" => ");
                        if (idx != -1) {
                            s = s.substring(idx + 4);
                        }

                        File f = new File(myDir, s);
                        String name = env.getPathRelativeToSourceRoot(f, 0);
                        entry.addFile(name);
                    }
                    break;
                default:
                    OpenGrokLogger.getLogger().warning("Unknown parser state: " + state);
                    break;
                }
        }

        if (entry != null && state > 2) {
            entries.add(entry);
        }
    }

   /**
     * Parse the given string.
     *
     * @param buffer The string to be parsed
     * @return The parsed history
     * @throws IOException if we fail to parse the buffer
     */
    History parse(String buffer) throws IOException {
        myDir = File.separator;
        processStream(new ByteArrayInputStream(buffer.getBytes("UTF-8")));
        return new History(entries);
    }
}
