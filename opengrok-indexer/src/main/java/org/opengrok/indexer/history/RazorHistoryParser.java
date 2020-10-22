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
 * Portions Copyright (c) 2008, Peter Bray.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.StringUtils;

/**
 * A History Parser for Razor.
 *
 * @author Peter Bray <Peter.Darren.Bray@gmail.com>
 */
class RazorHistoryParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RazorHistoryParser.class);

    private RazorRepository repository = new RazorRepository();

    private static final Pattern ACTION_TYPE_PATTERN =
            Pattern.compile("^(INTRODUCE|CHECK-OUT|CHECK-IN|UN-CHECK-OUT|RENAME|EDIT_PROPS|ALTERED|CHECK-POINT|" +
                    "REVERT|INTRODUCE_AND_EDIT|BRANCH|BUMP|MERGE-CHECK-IN|PROMOTE)\\s+(\\S*)\\s+([\\.0-9]+)?\\s+(\\S*)\\s+(\\S*)\\s*$");
    private static final Pattern ADDITIONAL_INFO_PATTERN =
            Pattern.compile("^##(TITLE|NOTES|AUDIT|ISSUE):\\s+(.*)\\s*$");
    private static final boolean DUMP_HISTORY_ENTRY_ADDITIONS = false;

    History parse(File file, Repository repos) throws HistoryException {
        try {
            return parseFile(file, repos);
        } catch (IOException ioe) {
            throw new HistoryException(ioe);
        }
    }

    private History parseFile(File file, Repository repos)
            throws IOException {

        repository = (RazorRepository) repos;
        File mappedFile = repository.getRazorHistoryFileFor(file);
        parseDebug("Mapping " + file.getPath() + " to '" + mappedFile.getPath() + "'");

        if (!mappedFile.exists()) {
            parseProblem("History File Mapping is NON-EXISTENT (" + mappedFile.getAbsolutePath() + ")");
            return null;
        }

        if (mappedFile.isDirectory()) {
            parseProblem("History File Mapping is a DIRECTORY (" + mappedFile.getAbsolutePath() + ")");
            return null;
        }
        try (FileReader contents = new FileReader(mappedFile.getAbsoluteFile())) {
            return parseContents(new BufferedReader(contents));
        }
    }

    protected History parseContents(BufferedReader contents) throws IOException {
        String line;

        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        HistoryEntry entry = null;

        boolean ignoreEntry = false;
        boolean seenActionType = false;
        boolean lastWasTitle = true;

        Matcher actionMatcher = ACTION_TYPE_PATTERN.matcher("");
        Matcher infoMatcher = ADDITIONAL_INFO_PATTERN.matcher("");
        while ((line = contents.readLine()) != null) {

            parseDebug("Processing '" + line + "'");

            if (StringUtils.isOnlyWhitespace(line)) {

                if (entry != null && entry.getDate() != null) {
                    entries.add(entry);
                    dumpEntry(entry);
                }
                entry = new HistoryEntry();
                ignoreEntry = false;
                seenActionType = false;

            } else if (!ignoreEntry) {

                if (seenActionType) {
                    infoMatcher.reset(line);

                    if (infoMatcher.find()) {
                        String infoType = infoMatcher.group(1);
                        String details = infoMatcher.group(2);

                        if ("TITLE".equals(infoType)) {
                            parseDebug("Setting Message : '" + details + "'");
                            entry.setMessage(details);
                            lastWasTitle = true;
                        } else {
                            parseDebug("Ignoring Info Type Line '" + line + "'");
                        }
                    } else {
                        if (!line.startsWith("##") && line.charAt(0) == '#') {
                            parseDebug("Seen Comment : '" + line + "'");
                            if (lastWasTitle) {
                                entry.appendMessage("");
                                lastWasTitle = false;
                            }
                            entry.appendMessage(line.substring(1));
                        } else {
                            parseProblem("Expecting addlInfo and got '" + line + "'");
                        }
                    }
                } else {
                    actionMatcher.reset(line);

                    if (actionMatcher.find()) {

                        seenActionType = true;
                        if (entry != null && entry.getDate() != null) {
                            entries.add(entry);
                            dumpEntry(entry);
                        }
                        entry = new HistoryEntry();

                        String actionType = actionMatcher.group(1);
                        String userName = actionMatcher.group(2);
                        String revision = actionMatcher.group(3);
                        String state = actionMatcher.group(4);
                        String dateTime = actionMatcher.group(5);
                        parseDebug("New History Event Seen : actionType = " + actionType + ", userName = " + userName +
                                ", revision = " + revision + ", state = " + state + ", dateTime = " + dateTime);
                        if (actionType.startsWith("INTRODUCE") ||
                                actionType.contains("CHECK-IN") ||
                                "CHECK-POINT".equals(actionType) ||
                                "REVERT".equals(actionType)) {
                            entry.setAuthor(userName);
                            entry.setRevision(revision);
                            entry.setActive("Active".equals(state));
                            Date date = null;
                            try {
                                date = repository.parse(dateTime);
                            } catch (ParseException pe) {
                                //
                                // Overriding processStream() thus need to comply with the
                                // set of exceptions it can throw.
                                //
                                throw new IOException("Could not parse date: " + dateTime, pe);
                            }
                            entry.setDate(date);
                            ignoreEntry = false;
                        } else {
                            ignoreEntry = true;
                        }
                    } else {
                        parseProblem("Expecting actionType and got '" + line + "'");
                    }
                }
            }
        }

        if (entry != null && entry.getDate() != null) {
            entries.add(entry);
            dumpEntry(entry);
        }

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    private void dumpEntry(HistoryEntry entry) {
        if (DUMP_HISTORY_ENTRY_ADDITIONS) {
            entry.dump();
        }
    }

    private void parseDebug(String message) {
        LOGGER.log(Level.FINE, "RazorHistoryParser: " + message );
    }

    private void parseProblem(String message) {
        LOGGER.log(Level.SEVERE, "PROBLEM: RazorHistoryParser - " + message);
    }
}
