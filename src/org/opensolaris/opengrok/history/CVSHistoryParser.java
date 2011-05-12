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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
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
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.util.Executor;

/**
 * Parse a stream of CVS log comments.
 */
class CVSHistoryParser implements Executor.StreamHandler {

    private enum ParseState {
        REVISION, METADATA, COMMENT
    }    

    private History history;
    private CVSRepository repository=new CVSRepository();

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
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();

        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        
        history = new History();
        HistoryEntry entry = null;
        ParseState state = ParseState.REVISION;
        String s = in.readLine();
        while (s != null) {
            if (state == ParseState.REVISION && s.startsWith("revision")) {
                if (entry != null) {
                    entries.add(entry);
                }
                entry = new HistoryEntry();
                entry.setActive(true);
                String commit = s.substring("revision".length()).trim();
                entry.setRevision(commit);
                state = ParseState.METADATA;
                s = in.readLine();
            }
            if (state == ParseState.METADATA && s.startsWith("date:")) {
                for (String pair : s.split(";")) {
                    String[] keyVal = pair.split(":", 2);
                    String key = keyVal[0].trim();
                    String val = keyVal[1].trim();

                    if ("date".equals(key)) {
                        try {
                            val = val.replace('/', '-');
                            entry.setDate(df.parse(val));
                        } catch (ParseException pe) {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to parse date: '" + val + "'", pe);
                        }
                    } else if ("author".equals(key)) {
                        entry.setAuthor(val);
                    }
                }

                state = ParseState.COMMENT;
                s = in.readLine();
            }
            if (state == ParseState.COMMENT) {
                if (s.startsWith("--------") || s.startsWith("========")) {
                    state = ParseState.REVISION;
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

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repos Pointer to the SubversionReporitory
     * @return object representing the file's history
     */
    History parse(File file, Repository repos) throws HistoryException {
        repository = (CVSRepository) repos;
        try {
            Executor executor = repository.getHistoryLogExecutor(file);
            int status = executor.exec(true, this);

            if (status != 0) {
                throw new HistoryException("Failed to get history for: \"" +
                                           file.getAbsolutePath() + "\" Exit code: " + status);
            }
        } catch (IOException e) {
            throw new HistoryException("Failed to get history for: \"" +
                                       file.getAbsolutePath() + "\"", e);
        }

        return history;
    }
    
    /**
     * Parse the given string.
     * 
     * @param buffer The string to be parsed
     * @return The parsed history
     * @throws IOException if we fail to parse the buffer
     */
    History parse(String buffer) throws IOException {
        processStream(new ByteArrayInputStream(buffer.getBytes("UTF-8")));
        return history;
    }
}
