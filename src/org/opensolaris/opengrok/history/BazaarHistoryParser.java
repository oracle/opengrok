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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
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
    private int rootLength;
    private List<HistoryEntry> entries = new ArrayList<HistoryEntry>(); //NOPMD
    private BazaarRepository repository=new BazaarRepository(); //NOPMD

    BazaarHistoryParser(BazaarRepository repository) {
        this.repository = repository;
        myDir = repository.getDirectoryName() + File.separator;
        rootLength =
                RuntimeEnvironment.getInstance().getSourceRootPath().length();
    }

    History parse(File file, String sinceRevision) throws HistoryException {
        Executor executor = repository.getHistoryLogExecutor(file, sinceRevision);
        int status = executor.exec(true, this);

        if (status != 0) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
        }

        // If a changeset to start from is specified, remove that changeset
        // from the list, since only the ones following it should be returned.
        // Also check that the specified changeset was found, otherwise throw
        // an exception.
        if (sinceRevision != null) {
            HistoryEntry entry = entries.isEmpty() ?
                null : entries.remove(entries.size() - 1);
            if (entry == null || !sinceRevision.equals(entry.getRevision())) {
                throw new HistoryException("No such revision: " + sinceRevision);
            }
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

        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        String s;

        HistoryEntry entry = null;
        int state = 0;
        int ident = 0;
        while ((s = in.readLine()) != null) {
            int nident = 0;
            int len = s.length();
            while (nident < len && s.charAt(nident) == ' ') {
                ++nident;
            }

            s = s.trim();
            if ("------------------------------------------------------------".equals(s)) {
                if (entry != null && state > 2) {
                    entries.add(entry);
                }
                entry = new HistoryEntry();
                entry.setActive(true);
                state = 0;
                ident = nident;
                continue;
            }

            switch (state) {
                case 0:
                    if (ident == nident && s.startsWith("revno:")) {
                        String rev[] = s.substring("revno:".length()).trim().split(" ");
                        entry.setRevision(rev[0]);
                        ++state;
                    }
                    break;
                case 1:
                    if (ident == nident && s.startsWith("committer:")) {
                        entry.setAuthor(s.substring("committer:".length()).trim());
                        ++state;
                    }
                    break;
                case 2:
                    if (ident == nident && s.startsWith("timestamp:")) {
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
                    if (!(ident == nident && s.startsWith("message:"))) {
                        if (ident == nident && (s.startsWith("modified:") || s.startsWith("added:") || s.startsWith("removed:"))) {
                            ++state;
                        } else {
                            entry.appendMessage(s);
                        }
                    }
                    break;
                case 4:
                    if (!(s.startsWith("modified:") || s.startsWith("added:") || s.startsWith("removed:"))) {
                        int idx = s.indexOf(" => ");
                        if (idx != -1) {
                            s = s.substring(idx + 4);
                        }

                        File f = new File(myDir, s);
                        String name = f.getCanonicalPath().substring(rootLength);
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
        rootLength = 0;
        processStream(new ByteArrayInputStream(buffer.getBytes("UTF-8")));
        return new History(entries);
    }
}
