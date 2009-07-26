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
 * Parse a stream of mercurial log comments.
 */
class MercurialHistoryParser implements Executor.StreamHandler {

    /** Prefix which identifies lines with the description of a commit. */
    private static final String DESC_PREFIX = "description: ";

    private List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
    private final MercurialRepository repository;
    private final String mydir;
    private final int rootLength;

    MercurialHistoryParser(MercurialRepository repository) {
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

        // If a changeset to start from is specified, remove that changeset
        // from the list, since only the ones following it should be returned.
        // Also check that the specified changeset was found, otherwise throw
        // an exception.
        if (changeset != null) {
            HistoryEntry entry = entries.isEmpty() ?
                null : entries.remove(entries.size() - 1);
            if (entry == null || !changeset.equals(entry.getRevision())) {
                throw new HistoryException("No such revision: " + changeset);
            }
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
    public void processStream(InputStream input) throws IOException {
        DateFormat df = repository.getDateFormat();
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        entries = new ArrayList<HistoryEntry>();
        String s;
        HistoryEntry entry = null;
        while ((s = in.readLine()) != null) {
            if (s.startsWith("changeset:")) {
                entry = new HistoryEntry();
                entries.add(entry);
                entry.setActive(true);
                entry.setRevision(s.substring("changeset:".length()).trim());
            } else if (s.startsWith("user:") && entry != null) {
                entry.setAuthor(s.substring("user:".length()).trim());
            } else if (s.startsWith("date:") && entry != null) {
                Date date = new Date();
                try {
                    date = df.parse(s.substring("date:".length()).trim());
                } catch (ParseException pe) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Could not parse date: " + s, pe);
                }
                entry.setDate(date);
            } else if (s.startsWith("files:") && entry != null) {
                String[] strings = s.split(" ");
                for (int ii = 1; ii < strings.length; ++ii) {
                    if (strings[ii].length() > 0) {
                        File f = new File(mydir, strings[ii]);
                        String name = f.getCanonicalPath().substring(rootLength);
                        entry.addFile(name);
                    }
                }
            } else if (s.startsWith(DESC_PREFIX) && entry != null) {
                entry.setMessage(decodeDescription(s));
            }
        }
    }

    /**
     * Decode a line with a description of a commit. The line is a sequence of
     * XML character entities that need to be converted to single characters.
     * This is to prevent problems if the log message contains one of the
     * prefixes that {@link #processStream(InputStream)} is looking for (bug
     * #405).
     *
     * This method is way too tolerant, and won't complain if the line has
     * a different format than expected. It will return weird results, though.
     *
     * @param line the XML encoded line
     * @return the decoded description
     */
    private String decodeDescription(String line) {
        StringBuilder out = new StringBuilder();
        int value = 0;

        // fetch the char values from the &#ddd; sequences
        for (int i = DESC_PREFIX.length(); i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isDigit(ch)) {
                value = value * 10 + Character.getNumericValue(ch);
            } else if (ch == ';') {
                out.append((char) value);
                value = 0;
            }
        }

        assert value == 0 : "description did not end with a semi-colon";

        return out.toString();
    }
}
