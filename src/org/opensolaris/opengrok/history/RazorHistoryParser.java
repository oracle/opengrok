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

/* Portions Copyright 2008 Peter Bray */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A History Parser for Razor
 * 
 * @author Peter Bray <Peter.Darren.Bray@gmail.com>
 */
public class RazorHistoryParser implements HistoryParser {

    private final static SimpleDateFormat DATE_TIME_FORMAT =
            new SimpleDateFormat("yyyy/MM/dd,hh:mm:ss");
    private final static Pattern ACTION_TYPE_PATTERN =
            Pattern.compile("^(INTRODUCE|CHECK-OUT|CHECK-IN|UN-CHECK-OUT|RENAME|EDIT_PROPS|ALTERED|CHECK-POINT|REVERT|INTRODUCE_AND_EDIT|BRANCH|BUMP|MERGE-CHECK-IN|PROMOTE)\\s+(.*)\\s+([\\.0-9]+)?\\s+(.*)\\s+(.*)\\s*$");
    private final static Pattern ADDITIONAL_INFO_PATTERN =
            Pattern.compile("^##(TITLE|NOTES|AUDIT|ISSUE):\\s+(.*)\\s*$");
    private final static boolean DUMP_HISTORY_ENTRY_ADDITIONS = false;

    public History parse(File file, Repository repository) throws Exception {

        RazorRepository repo = (RazorRepository) repository;

        File mappedFile = repo.getRazorHistoryFileFor(file);
        parseDebug("Mapping " + file.getPath() + " to '" + mappedFile.getPath() + "'");

        if (!mappedFile.exists()) {
            parseProblem("History File Mapping is NON-EXISTENT (" + mappedFile.getAbsolutePath() + ")");
            return null;
        }

        if (mappedFile.isDirectory()) {
            parseProblem("History File Mapping is a DIRECTORY (" + mappedFile.getAbsolutePath() + ")");
            return null;
        }

        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        HistoryEntry entry = null;

        String line;
        boolean ignoreEntry = false;
        boolean seenActionType = false;
        boolean lastWasTitle = true;

        BufferedReader contents = new BufferedReader(new FileReader(mappedFile.getAbsolutePath()));
        try {
            while ((line = contents.readLine()) != null) {

                parseDebug("Processing '" + line + "'");

                if (line.trim().length() == 0) {

                    if (entry != null && entry.getDate() != null) {
                        entries.add(entry);
                        if (DUMP_HISTORY_ENTRY_ADDITIONS) {
                            entry.dump();
                        }
                    }
                    entry = new HistoryEntry();
                    ignoreEntry = false;
                    seenActionType = false;

                } else if (!ignoreEntry) {

                    if (!seenActionType) {
                        Matcher matcher = ACTION_TYPE_PATTERN.matcher(line);

                        if (matcher.find()) {

                            seenActionType = true;
                            if (entry != null && entry.getDate() != null) {
                                entries.add(entry);
                                if (DUMP_HISTORY_ENTRY_ADDITIONS) {
                                    entry.dump();
                                }
                            }
                            entry = new HistoryEntry();

                            String actionType = matcher.group(1);
                            String userName = matcher.group(2);
                            String revision = matcher.group(3);
                            String state = matcher.group(4);
                            String dateTime = matcher.group(5);
                            parseDebug("New History Event Seen : actionType = " + actionType + ", userName = " + userName + ", revision = " + revision + ", state = " + state + ", dateTime = " + dateTime);
                            if (actionType.startsWith("INTRODUCE") ||
                                    actionType.contains("CHECK-IN") ||
                                    actionType.equals("CHECK-POINT") ||
                                    actionType.equals("REVERT")) {
                                entry.setAuthor(userName);
                                entry.setRevision(revision);
                                entry.setActive(state.equals("Active"));
                                Date date = DATE_TIME_FORMAT.parse(dateTime);
                                entry.setDate(date);
                                ignoreEntry = false;
                            } else {
                                ignoreEntry = true;
                            }
                        } else {
                            parseProblem("Expecting actionType and got '" + line + "'");
                        }
                    } else {
                        Matcher matcher = ADDITIONAL_INFO_PATTERN.matcher(line);

                        if (matcher.find()) {
                            String infoType = matcher.group(1);
                            String details = matcher.group(2);

                            if (infoType.equals("TITLE")) {
                                parseDebug("Setting Message : '" + details + "'");
                                entry.setMessage(details);
                                lastWasTitle = true;
                            } else if (infoType.equals("ISSUE")) {
                                parseDebug("Adding CR : '" + details + "'");
                                entry.addChangeRequest(details);
                            } else {
                                parseDebug("Ignoring Info Type Line '" + line + "'");
                            }
                        } else {
                            if (!line.startsWith("##") && line.startsWith("#")) {
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
                    }
                }
            }
        } finally {
            contents.close();
        }

        if (entry != null && entry.getDate() != null) {
            entries.add(entry);
            if (DUMP_HISTORY_ENTRY_ADDITIONS) {
                entry.dump();
            }
        }

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    private void parseDebug(String message) {
        // System.err.println( "RazorHistoryParser: " + message );
    }

    private void parseProblem(String message) {
        System.err.println("PROBLEM: RazorHistoryParser - " + message);
    }
}
