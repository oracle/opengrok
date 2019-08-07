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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Ross <cross@distal.com>.
 */

package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;
import static org.opengrok.indexer.history.PerforceRepository.protectPerforceFilename;

/**
 * Parse source history for a Perforce Repository.
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceHistoryParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerforceHistoryParser.class);

    History parse(File file, Repository repos) throws HistoryException {
        return this.parse(file, null, repos);
    }

    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param sinceRevision the revision before the start of desired history
     * @param repos Pointer to the {@code PerforceRepository}
     * @return object representing the file's history
     * @throws HistoryException if a problem occurs while executing p4 command
     */
    History parse(File file, String sinceRevision, Repository repos) throws HistoryException {
        History history;

        if (!PerforceRepository.isInP4Depot(file, false)) {
            return null;
        }

        try {
            if (file.isDirectory()) {
                /* TODO: Do I need to think about revisions here? */
                history = parseDirectory(file);
            } else {
                if (sinceRevision == null || "".equals(sinceRevision)) {
                    /* Get all revisions */
                    history = getRevisions(file, null);
                } else {
                    /* Get revisions between specified and head */
                    history = getRevisionsSince(file, sinceRevision);
                }
            }
        } catch (IOException ioe) {
            throw new HistoryException(ioe);
        }
        return history;
    }

    private History parseDirectory(File file) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("p4");
        cmd.add("changes");
        cmd.add("-t");
        cmd.add("...");

        Executor executor = new Executor(cmd, file.getCanonicalFile());
        executor.exec();
        return parseChanges(executor.getOutputReader());
    }

    /**
     * Retrieve the history of a given file.
     *
     * @param file the file to parse history for
     * @param rev the revision at which to end history
     * @return object representing the file's history
     */
    public static History getRevisions(File file, String rev) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("p4");
        cmd.add("filelog");
        cmd.add("-lti");
        cmd.add(protectPerforceFilename(file.getName()) + PerforceRepository.getRevisionCmd(rev));
        Executor executor = new Executor(cmd, file.getCanonicalFile().getParentFile());
        executor.exec();

        return parseFileLog(executor.getOutputReader());
    }

    /**
     * Retrieve the history of a given file, beginning after the specified
     * revision.
     *
     * @param file the file to parse history for
     * @param rev the revision before the start of desired history
     * @return object representing the file's history
     */
    public static History getRevisionsSince(File file, String rev) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("p4");
        cmd.add("filelog");
        cmd.add("-lti");
        /* Okay.  This is a little cheeky.  getRevisionCmd(String,String) gives
         * a range spec that _includes_ the first revision.  But, we don't want
         * that in this case here.  So, presume that "rev" is always an integer
         * for perforce, add one to it, then convert back into a string to
         * pass into getRevisionCmd as a starting revision. */
        try {
            Integer irev = Integer.parseInt(rev);
            irev += 1;
            rev = irev.toString();
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING,
                    "Unable to increment revision {}, NumberFormatException",
                    new Object[]{rev});
            /* Move along with rev unchanged... */
        }
        cmd.add(file.getName() + PerforceRepository.getRevisionCmd(rev, "now"));
        Executor executor = new Executor(cmd, file.getCanonicalFile().getParentFile());
        executor.exec();

        return parseFileLog(executor.getOutputReader());
    }

    private static final Pattern REVISION_PATTERN = Pattern.compile(
            "#\\d+ change (\\d+) \\S+ on (\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}) by ([^@]+)");
    private static final Pattern CHANGE_PATTERN = Pattern.compile(
            "Change (\\d+) on (\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}) by ([^@]+)@\\S* '([^']*)'");

    /**
     * Parses the history in the given string. The given reader will be closed.
     *
     * @param fileHistory String with history to parse
     * @return History object with all the history entries
     * @throws java.io.IOException if it fails to read from the supplied reader
     */
    protected static History parseChanges(Reader fileHistory) throws IOException {
        /* OUTPUT:
        Directory changelog:
        Change 177601 on 2008/02/12 by user@host 'description'
         */
        History history = new History();
        List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        try (BufferedReader reader = new BufferedReader(fileHistory)) {
            String line;
            Matcher matcher = CHANGE_PATTERN.matcher("");
            while ((line = reader.readLine()) != null) {
                matcher.reset(line);
                if (matcher.find()) {
                    HistoryEntry entry = new HistoryEntry();
                    entry.setRevision(matcher.group(1)); 
                    int year = Integer.parseInt(matcher.group(2));
                    int month = Integer.parseInt(matcher.group(3));
                    int day = Integer.parseInt(matcher.group(4));
                    int hour = Integer.parseInt(matcher.group(5));
                    int minute = Integer.parseInt(matcher.group(6));
                    int second = Integer.parseInt(matcher.group(7));
                    entry.setDate(newDate(year, month, day, hour, minute, second));
                    entry.setAuthor(matcher.group(8));
                    entry.setMessage(matcher.group(9).trim());
                    entry.setActive(true);
                    entries.add(entry);
                }
            }
        }
        history.setHistoryEntries(entries);
        return history;
    }

    /**
     * Parse file log. Te supplied reader will be closed.
     *
     * @param fileLog reader to the information to parse
     * @return A history object containing history entries
     * @throws java.io.IOException If it fails to read from the supplied reader.
     */
    protected static History parseFileLog(Reader fileLog) throws IOException {
        List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        HistoryEntry entry = null;
        try (BufferedReader reader = new BufferedReader(fileLog)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = REVISION_PATTERN.matcher(line);
                if (matcher.find()) {
                    /* An entry finishes when a new entry starts ... */
                    if (entry != null) {
                        entries.add(entry);
                        entry = null;
                    }
                    /* New entry */
                    entry = new HistoryEntry();
                    entry.setRevision(matcher.group(1));
                    int year = Integer.parseInt(matcher.group(2));
                    int month = Integer.parseInt(matcher.group(3));
                    int day = Integer.parseInt(matcher.group(4));
                    int hour = Integer.parseInt(matcher.group(5));
                    int minute = Integer.parseInt(matcher.group(6));
                    int second = Integer.parseInt(matcher.group(7));
                    entry.setDate(newDate(year, month, day, hour, minute, second));
                    entry.setAuthor(matcher.group(8));
                    entry.setActive(true);
                } else {
                    if (entry != null) {
                        /* ... an entry can also finish when some branch/edit entry is encountered */
                        if (line.startsWith("... ...")) {
                            entries.add(entry);
                            entry = null;
                        } else {
                            entry.appendMessage(line);
                        }
                    }
                }
            }
        }
        /* ... an entry can also finish when the log is finished */
        if (entry != null) {
            entries.add(entry);
        }

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    /**
     * Create a Date object representing the specified date.
     *
     * @param year the year
     * @param month the month (January is 1, February is 2, ...)
     * @param day the day of the month
     * @param hour of the day
     * @param minute of the day
     * @param second of the day
     * @return a Date object representing the date
     */
    private static Date newDate(int year, int month, int day, int hour, int minute, int second) {
        Calendar cal = Calendar.getInstance();
        // Convert 1-based month to 0-based
        cal.set(year, month - 1, day, hour, minute, second);
        return cal.getTime();
    }
}
