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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Parse a stream of Git log comments.
 */
class GitHistoryParser implements HistoryParser {

    private enum ParseState {
        HEADER, MESSAGE, FILES
    };      
            
    /**
     * Parse a git history entry.
     * 
     * @param in The reader to read the data to parse from
     * @param directory The directory the files is inside
     * @param rootLength The length of the path that is the standard (non relative) part
     * @return History entry
     * @throws java.io.IOException if it fails to read
     */
    public History parse(BufferedReader in, String directory, int rootLength)
            throws IOException {

        SimpleDateFormat df =
                new SimpleDateFormat("EEE MMM dd hh:mm:ss yyyy ZZZZ", Locale.US);
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();

        History history = new History();
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
                } else if (s.trim().isEmpty()) {
                    // We are done reading the heading, start to read the message
                    state = ParseState.MESSAGE;
                    
                    // The current line is empty - the message starts on the next line (to be parsed below).
                    s = in.readLine();
                }

            }
            if (state == ParseState.MESSAGE) {
                if (s.isEmpty() || Character.isWhitespace(s.charAt(0))) {
                    if (entry != null) {
                        entry.appendMessage(s);
                    }
                } else {
                    // This is the list of files after the message - add them
                    state = ParseState.FILES;
                }
            }
            if (state == ParseState.FILES) {
                if (s.trim().equals("") || s.startsWith("commit")) {
                    state = ParseState.HEADER;
                    continue; // Parse this line again - do not read a new line
                } else {
                    if (entry != null) {
                        File f = new File(directory, s);
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
        return history;
    }
            
    public History parse(File file, Repository repos)
            throws IOException {

        GitRepository mrepos = (GitRepository) repos;
        History history = null;
        
        Process process = null;
        BufferedReader in = null;
        try {
            process = mrepos.getHistoryLogProcess(file);
            if (process == null) {
                return null;
            }

            InputStream is = process.getInputStream();
            in = new BufferedReader(new InputStreamReader(is));
            String mydir = mrepos.getDirectoryName() + File.separator;
            int rootLength = RuntimeEnvironment.getInstance().getSourceRootPath().length();

            history = parse(in, mydir, rootLength);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing stream", e);
                }
            }

            if (process != null) {
                try {
                    process.exitValue();
                } catch (IllegalThreadStateException exp) {
                    // the process is still running??? just kill it..
                    process.destroy();
                }
            }
        }

        return history;
    }
}
