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

package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensolaris.opengrok.util.Executor;

/**
 * Parse source history for a Perforce Repository
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceHistoryParser implements HistoryParser {
    
    private final static Pattern revision_regexp = Pattern.compile("#(\\d+) change \\d+ \\S+ on (\\d{4})/(\\d{2})/(\\d{2}) by ([^@]+)");

    public static List<HistoryEntry> getRevisions(File file, String rev) throws IOException {
        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("p4");
        cmd.add("filelog");
        cmd.add("-l");
        cmd.add(file.getName() + ((rev == null) ? "" : "#"+rev));
        Executor executor = new Executor(cmd, file.getCanonicalFile().getParentFile());
        executor.exec();
        
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        BufferedReader output_reader = new BufferedReader(executor.getOutputReader());
        String line;
        HistoryEntry entry = null;
        while ((line = output_reader.readLine()) != null) {
            Matcher matcher = revision_regexp.matcher(line);
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
                Calendar calendar = new GregorianCalendar(year, month, day); 
                entry.setDate(calendar.getTime());
                entry.setAuthor(matcher.group(5));
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
        /* ... an entry can also finish when the log is finished */
        if (entry != null) {
            entries.add(entry);
        }
        
        return entries;
    }
    
    private final static Pattern change_pattern = Pattern.compile("Change (\\d+) on (\\d{4})/(\\d{2})/(\\d{2}) by ([^@]+)@\\S* '([^']*)'");
    /**
     * Parse the history for the specified file.
     *
     * @param file the file to parse history for
     * @param repository Pointer to the PerforceReporitory
     * @return object representing the file's history
     * @throws IOException if a problem occurs while executing p4 command
     */
    public History parse(File file, Repository repository) throws IOException {
        if (!PerforceRepository.isInP4Depot(file)) {
            return null;
        }
        
        List<HistoryEntry> entries = null;
        if (file.isDirectory()) {
            ArrayList<String> cmd = new ArrayList<String>();
            cmd.add("p4");
            cmd.add("changes");
            cmd.add("...");
            
            Executor executor = new Executor(cmd, file.getCanonicalFile());
            executor.exec();
            /* OUTPUT:
                Directory changelog:
                Change 177601 on 2008/02/12 by user@host 'description'
             */
            Matcher matcher = change_pattern.matcher(executor.getOutputString());
            entries = new ArrayList<HistoryEntry>();
            while (matcher.find()) {
                HistoryEntry entry = new HistoryEntry();
                entry.setRevision(matcher.group(1));
                int year = Integer.parseInt(matcher.group(2));
                int month = Integer.parseInt(matcher.group(3));
                int day = Integer.parseInt(matcher.group(4));
                Calendar calendar = new GregorianCalendar(year, month, day); 
                entry.setDate(calendar.getTime());
                entry.setAuthor(matcher.group(5));
                entry.setMessage(matcher.group(6).trim());
                entry.setActive(true);
                entries.add(entry);
            } 
        } else {
            entries = getRevisions(file, null);
        }
        
        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }    
}
