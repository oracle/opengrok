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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

/**
 * Parse a stream of Bazaar log comments.
 */
class BazaarHistoryParser implements Executor.StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BazaarHistoryParser.class);

    private String myDir;
    private final List<HistoryEntry> entries = new ArrayList<>();
    private final BazaarRepository repository;

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

        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String s;

        var entry  = createHistoryEntry();
        int state = 0;
        while ((s = in.readLine()) != null) {
            if ("------------------------------------------------------------".equals(s)) {
                if (state > 2) {
                    entries.add(entry);
                }
                entry = createHistoryEntry();
                state = 0;
                continue;
            }

            switch (state) {
                case 0:
                    // First, go on until revno is found.
                    state = state + populateRevisionInHistoryEntry(s, entry);
                    break;
                case 1:
                    // Then, look for committer.
                    state = state + populateCommitterInHistoryEntry(s, entry);
                    break;
                case 2:
                    // And then, look for timestamp.
                    state = state + populateDateInHistoryEntry(s, entry);
                    break;
                case 3:
                    // Expect the commit message to follow immediately after the timestamp
                    state = state + populateCommitMessageInHistoryEntry(s, entry);
                    break;
                case 4:
                    // Finally, store the list of modified, added and removed
                    // files. (Except the labels.)
                    populateCommitFilesInHistoryEntry(s, entry);
                    break;
                default:
                    LOGGER.log(Level.WARNING, "Unknown parser state: {0}", state);
                    break;
            }
        }

        if (state > 2) {
            entries.add(entry);
        }
    }

    private HistoryEntry createHistoryEntry() {
        var entry  = new HistoryEntry();
        entry.setActive(true);
        return entry;
    }

    /**
     *
     * @param currentLine currentLine of string from stream
     * @param historyEntry current history entry to populate committer
     */
    private void populateCommitFilesInHistoryEntry(@NotNull String currentLine,
                                           @NotNull HistoryEntry historyEntry) throws IOException {

        if (!(currentLine.startsWith("modified:") || currentLine.startsWith("added:")
                || currentLine.startsWith("removed:"))) {
            // The list of files is prefixed with blanks.
            currentLine = currentLine.trim();

            int idx = currentLine.indexOf(" => ");
            if (idx != -1) {
                currentLine = currentLine.substring(idx + 4);
            }

            File f = new File(myDir, currentLine);
            try {
                String name = RuntimeEnvironment.getInstance().getPathRelativeToSourceRoot(f);
                historyEntry.addFile(name.intern());
            } catch (ForbiddenSymlinkException e) {
                LOGGER.log(Level.FINER, e.getMessage());
                // ignored
            } catch (InvalidPathException e) {
                LOGGER.log(Level.WARNING, e.getMessage());
            }
        }
    }

    /**
     *
     * @param currentLine currentLine of string from stream
     * @param historyEntry current history entry to populate committer
     * @return 1 if committer is populated else 0
     */
    private int populateCommitMessageInHistoryEntry(@NotNull String currentLine, @NotNull HistoryEntry historyEntry) {
        // everything up to the list of
        // modified, added and removed files is part of the commit
        // message.
        int state = 0;
        if (currentLine.startsWith("modified:") || currentLine.startsWith("added:") || currentLine.startsWith("removed:")) {
            ++state;
        } else if (currentLine.startsWith("  ")) {
            // Commit messages returned by bzr log -v are prefixed
            // with two blanks.
            historyEntry.appendMessage(currentLine.substring(2));
        }
        return state;
    }

    /**
     *
     * @param currentLine currentLine of string from stream
     * @param historyEntry current history entry to populate committer
     * @return 1 if committer is populated else 0
     */
    private int populateCommitterInHistoryEntry(@NotNull String currentLine, @NotNull HistoryEntry historyEntry) {
        int state = 0;
        if (currentLine.startsWith("committer:")) {
            historyEntry.setAuthor(currentLine.substring("committer:".length()).trim());
            ++state;
        }
        return state;
    }

    /**
     *
     * @param currentLine currentLine of string from stream
     * @param historyEntry current history entry to populate Date
     * @return 1 if Date is populated else 0
     * @throws IOException on failure to parse timestamp
     */
    private int populateDateInHistoryEntry(@NotNull String currentLine, @NotNull HistoryEntry historyEntry) throws IOException {
        int state = 0;
        if (currentLine.startsWith("timestamp:")) {
            try {
                Date date = repository.parse(currentLine.substring("timestamp:".length()).trim());
                historyEntry.setDate(date);
            } catch (ParseException e) {
                //
                // Overriding processStream() thus need to comply with the
                // set of exceptions it can throw.
                //
                throw new IOException("Failed to parse history timestamp:" + currentLine, e);
            }
            ++state;
        }
        return state;
    }

    /**
     *
     * @param currentLine currentLine of string from stream
     * @param historyEntry current history entry to populate Revision
     * @return 1 if revision is populated else 0
     */
    private int populateRevisionInHistoryEntry(@NotNull String currentLine, @NotNull HistoryEntry historyEntry) {
        int state = 0;
        if (currentLine.startsWith("revno:")) {
            String[] rev = currentLine.substring("revno:".length()).trim().split(" ");
            historyEntry.setRevision(rev[0]);
            ++state;
        }
        return state;
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
        processStream(new ByteArrayInputStream(buffer.getBytes(StandardCharsets.UTF_8)));
        return new History(entries);
    }
}
