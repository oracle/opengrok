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
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import org.opengrok.indexer.util.Executor;

/**
 * Parse a stream of ClearCase log comments.
 */
class ClearCaseHistoryParser implements Executor.StreamHandler {

    private History history;
    private ClearCaseRepository repository = new ClearCaseRepository();

    History parse(File file, Repository repos) throws HistoryException {
        repository = (ClearCaseRepository) repos;
        try {
            Executor executor = repository.getHistoryLogExecutor(file);
            int status = executor.exec(true, this);

            if (status != 0) {
                throw new HistoryException("Failed to get history for: \"" +
                                           file.getAbsolutePath() + "\" Exit code: " + status);
            }

            return history;
        } catch (IOException e) {
            throw new HistoryException("Failed to get history for: \"" +
                                       file.getAbsolutePath() + "\"", e);
        }
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
        List<HistoryEntry> entries = new ArrayList<>();
        String s;
        HistoryEntry entry = null;
        while ((s = in.readLine()) != null) {
            if (!"create version".equals(s) && !"create directory version".equals(s)) {
                // skip this history entry
                while ((s = in.readLine()) != null) {
                    if (".".equals(s)) {
                        break;
                    }
                }
                continue;
            }

            entry = new HistoryEntry();
            if ((s = in.readLine()) != null) {
                try {
                    entry.setDate(repository.parse(s));
                } catch (ParseException pe) {
                    //
                    // Overriding processStream() thus need to comply with the
                    // set of exceptions it can throw.
                    //
                    throw new IOException("Could not parse date: " + s, pe);
                }
            }
            if ((s = in.readLine()) != null) {
                entry.setAuthor(s);
            }
            if ((s = in.readLine()) != null) {
                s = s.replace('\\', '/');
                entry.setRevision(s);
            }

            StringBuilder message = new StringBuilder();
            String glue = "";
            while ((s = in.readLine()) != null && !".".equals(s)) {
                if (s.isEmpty()) {
                    // avoid empty lines in comments
                    continue;
                }
                message.append(glue);
                message.append(s.trim());
                glue = "\n";
            }
            entry.setMessage(message.toString());
            entry.setActive(true);
            entries.add(entry);
        }
        history = new History();
        history.setHistoryEntries(entries);
    }

    /**
     * Parse the given string.
     *
     * @param buffer The string to be parsed
     * @return The parsed history
     * @throws IOException if we fail to parse the buffer
     */
    History parse(String buffer) throws IOException {
        processStream(new ByteArrayInputStream(buffer.getBytes(StandardCharsets.UTF_8)));
        return history;
    }
}
