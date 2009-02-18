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
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.StringUtils;

/**
 * Parse a stream of Git log comments.
 */
class GitHistoryParser implements HistoryParser, Executor.StreamHandler {

    private enum ParseState {
        HEADER, MESSAGE, FILES
    };      

    private String myDir;
    private int rootLength;
    private History history;
    private GitRepository repository;
    
   /**
     * Process the output from the log command and insert the HistoryEntries
     * into the history field.
     *
     * @param input The output from the process
     * @throws java.io.IOException If an error occurs while reading the stream
     */
    public void processStream(InputStream input) throws IOException {
        DateFormat df = repository.getDateFormat();
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();

        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        
        history = new History();
        HistoryEntry entry = null;
        ParseState state = ParseState.HEADER;
        String s = in.readLine();
        while (s != null) {
            if (state == ParseState.HEADER) {

                if (s.startsWith("commit")) {
                    if (entry != null) {
                        entries.add(entry);
                    }
                    entry = new HistoryEntry();
                    entry.setActive(true);
                    String commit = s.substring("commit".length()).trim();
                    entry.setRevision(commit);
                } else if (s.startsWith("Author:") && entry != null) {
                    entry.setAuthor(s.substring("Author:".length()).trim());
                } else if (s.startsWith("AuthorDate:") && entry != null) {
                    String dateString =
                            s.substring("AuthorDate:".length()).trim();
                    try {
                        entry.setDate(df.parse(dateString));
                    } catch (ParseException pe) {
                        OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to parse author date: " + s, pe);
                    }
                } else if (StringUtils.isOnlyWhitespace(s)) {
                    // We are done reading the heading, start to read the message
                    state = ParseState.MESSAGE;
                    
                    // The current line is empty - the message starts on the next line (to be parsed below).
                    s = in.readLine();
                }

            }
            if (state == ParseState.MESSAGE) {
                if ((s.length() == 0) || Character.isWhitespace(s.charAt(0))) {
                    if (entry != null) {
                        entry.appendMessage(s);
                    }
                } else {
                    // This is the list of files after the message - add them
                    state = ParseState.FILES;
                }
            }
            if (state == ParseState.FILES) {
                if (StringUtils.isOnlyWhitespace(s) || s.startsWith("commit")) {
                    state = ParseState.HEADER;
                    continue; // Parse this line again - do not read a new line
                } else {
                    if (entry != null) {
                        File f = new File(myDir, s);
                        String name = f.getCanonicalPath().substring(rootLength);
                        entry.addFile(name);
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
    public History parse(File file, Repository repos) throws HistoryException {
        myDir = repos.getDirectoryName()+ File.separator;
        rootLength = RuntimeEnvironment.getInstance().getSourceRootPath().length();
        repository = (GitRepository) repos;
        Executor executor = repository.getHistoryLogExecutor(file);
        int status = executor.exec(true, this);

        if (status != 0) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
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
    public History parse(String buffer) throws IOException {
        myDir = File.separator;
        rootLength = 0;
        processStream(new ByteArrayInputStream(buffer.getBytes("UTF-8")));
        return history;
    }
}
