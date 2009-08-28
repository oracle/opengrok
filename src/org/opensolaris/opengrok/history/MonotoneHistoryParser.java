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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
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
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;

/**
 * Class used to parse the history log from Monotone
 * 
 * @author Trond Norbye
 */
class MonotoneHistoryParser implements Executor.StreamHandler {

    private List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
    private final MonotoneRepository repository;
    private final String mydir;
    private final int rootLength;

    MonotoneHistoryParser(MonotoneRepository repository) {
        this.repository = repository;
        mydir = repository.getDirectoryName() + File.separator;
        rootLength =
                RuntimeEnvironment.getInstance().getSourceRootPath().length();
    }

    /**
     * Parse the history for the specified file or directory. If a changeset is
     * specified, only return the history from the changeset right after the
     * specified one.
     *
     * @param file the file or directory to get history for
     * @param changeset the changeset right before the first one to fetch, or
     * {@code null} if all changesets should be fetched
     * @return history for the specified file or directory
     * @throws HistoryException if an error happens when parsing the history
     */
    History parse(File file, String changeset) throws HistoryException {
        Executor executor = repository.getHistoryLogExecutor(file, changeset);
        int status = executor.exec(true, this);

        if (status != 0) {
            throw new HistoryException("Failed to get history for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
        }

        return new History(entries);
    }

    /**
     * Process the output from the hg log command and insert the HistoryEntries
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
        while ((s = in.readLine()) != null) {
            s = s.trim();
            if ("-----------------------------------------------------------------".equals(s)) {
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
                    if (s.startsWith("Revision:")) {
                        String rev = s.substring("Revision:".length()).trim();
                        entry.setRevision(rev);
                        ++state;
                    }
                    break;
                case 1:
                    if (s.startsWith("Author:")) {
                        entry.setAuthor(s.substring("Author:".length()).trim());
                        ++state;
                    }
                    break;
                case 2:
                    if (s.startsWith("Date:")) {
                        Date date = new Date();
                        try {
                            date = df.parse(s.substring("date:".length()).trim());
                        } catch (ParseException pe) {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "Could not parse date: " + s, pe);
                        }
                        entry.setDate(date);
                        ++state;
                    }
                    break;
                case 3:
                    if (s.startsWith("Modified ") || s.startsWith("Added ") || s.startsWith("Deleted ")) {
                        ++state;
                    } else if (s.equalsIgnoreCase("ChangeLog:")) {
                        state = 5;
                    }
                    break;
                case 4:
                    if (s.startsWith("Modified ") || s.startsWith("Added ") || s.startsWith("Deleted ")) {
                        /* swallow */
                    } else if (s.equalsIgnoreCase("ChangeLog:")) {
                        state = 5;
                    } else {
                        String files[] = s.split(" ");
                        for (String f : files) {
                            File file = new File(mydir, f);
                            String name = file.getCanonicalPath().substring(rootLength);
                            entry.addFile(name);
                        }
                    }
                    break;
                case 5:
                    entry.appendMessage(s);
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
}
