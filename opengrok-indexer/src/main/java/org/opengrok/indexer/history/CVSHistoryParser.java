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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.g00fy2.versioncompare.Version;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.util.Executor;

/**
 * Parse a stream of CVS log comments.
 */
class CVSHistoryParser implements Executor.StreamHandler {

    private enum ParseState {
        NAMES, TAG, REVISION, METADATA, COMMENT
    }

    private History history;
    private CVSRepository cvsRepository = new CVSRepository();

   /**
     * Process the output from the log command and insert the {@link HistoryEntry} objects created therein
     * into the {@link #history} field.
     *
     * @param input The output from the process
     * @throws java.io.IOException If an error occurs while reading the stream
     */
    @Override
    public void processStream(InputStream input) throws IOException {
        ArrayList<HistoryEntry> entries = new ArrayList<>();

        BufferedReader in = new BufferedReader(new InputStreamReader(input));

        history = new History();
        HistoryEntry entry = null;
        HashMap<String, String> tags = null;
        ParseState state = ParseState.NAMES;
        String s = in.readLine();
        while (s != null) {
            if (state == ParseState.NAMES && s.startsWith("symbolic names:")) {
                tags = new HashMap<>();
                state = ParseState.TAG;
                s = in.readLine();
            }
            if (state == ParseState.TAG) {
                if (s.startsWith("\t")) {
                    parseTag(tags, s);
                } else {
                    state = ParseState.REVISION;
                    s = in.readLine();
                }
            }
            if (state == ParseState.REVISION && s.startsWith("revision ")) {
                if (entry != null) {
                    entries.add(entry);
                }
                entry = new HistoryEntry();
                entry.setActive(true);
                String commit = s.substring("revision".length()).trim();
                entry.setRevision(commit);
                if (tags.containsKey(commit)) {
                    history.addTags(entry, tags.get(commit));
                }
                state = ParseState.METADATA;
                s = in.readLine();
            }
            if (state == ParseState.METADATA && s.startsWith("date: ")) {
                parseDateAuthor(entry, s);

                state = ParseState.COMMENT;
                s = in.readLine();
            }
            if (state == ParseState.COMMENT) {
                if (s.equals("----------------------------")) {
                    state = ParseState.REVISION;
                } else if (s.equals("=============================================================================")) {
                    state = ParseState.NAMES;
                } else {
                    if (entry != null) {
                        entry.appendMessage(s);
                    }
                }
            }
            s = in.readLine();
        }

        if (entry != null) {
            entries.add(entry);
        }

        history.setHistoryEntries(entries);
    }

    private void parseDateAuthor(HistoryEntry entry, String s) throws IOException {
        for (String pair : s.split(";")) {
            String[] keyVal = pair.split(":", 2);
            String key = keyVal[0].trim();
            String val = keyVal[1].trim();

            if ("date".equals(key)) {
                try {
                    val = val.replace('/', '-');
                    entry.setDate(cvsRepository.parse(val));
                } catch (ParseException pe) {
                    //
                    // Overriding processStream() thus need to comply with the
                    // set of exceptions it can throw.
                    //
                    throw new IOException("Failed to parse date: '" + val + "'", pe);
                }
            } else if ("author".equals(key)) {
                entry.setAuthor(val);
            }
        }
    }

    private void parseTag(HashMap<String, String> tags, String s) throws IOException {
        String[] pair = s.trim().split(": ");
        if (pair.length != 2) {
            //
            // Overriding processStream() thus need to comply with the
            // set of exceptions it can throw.
            //
            throw new IOException("Failed to parse tag: '" + s + "'");
        } else {
            if (tags.containsKey(pair[1])) {
                // Join multiple tags for one revision
                String oldTag = tags.get(pair[1]);
                tags.remove(pair[1]);
                tags.put(pair[1], oldTag + " " + pair[0]);
            } else {
                tags.put(pair[1], pair[0]);
            }
        }
    }

    /**
     * Sort history entries in the object according to semantic ordering of the revision string.
     * @param history {@link History} object
     */
    @VisibleForTesting
    static void sortHistoryEntries(History history) {
        List<HistoryEntry> entries = history.getHistoryEntries();
        entries.sort((h1, h2) -> new Version(h2.getRevision()).compareTo(new Version(h1.getRevision())));
        history.setHistoryEntries(entries);
    }

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repository Pointer to the SubversionRepository
     * @return object representing the file's history
     */
    History parse(File file, Repository repository) throws HistoryException {
        cvsRepository = (CVSRepository) repository;
        try {
            Executor executor = cvsRepository.getHistoryLogExecutor(file);
            int status = executor.exec(true, this);

            if (status != 0) {
                throw new HistoryException("Failed to get history for: \"" +
                                           file.getAbsolutePath() + "\" Exit code: " + status);
            }
        } catch (IOException e) {
            throw new HistoryException("Failed to get history for: \"" +
                                       file.getAbsolutePath() + "\"", e);
        }

        // In case there is a branch, the log entries can be returned in
        // unsorted order (as a result of using '-r1.1:branch' for 'cvs log')
        // so they need to be sorted according to revision.
        if (cvsRepository.getBranch() != null && !cvsRepository.getBranch().isEmpty()) {
            sortHistoryEntries(history);
        }

        return history;
    }

    /**
     * Parse the given string. Used for testing.
     *
     * @param buffer The string to be parsed
     * @return The parsed history
     * @throws IOException if we fail to parse the buffer
     */
    @VisibleForTesting
    History parse(String buffer) throws IOException {
        processStream(new ByteArrayInputStream(buffer.getBytes(StandardCharsets.UTF_8)));
        return history;
    }
}
