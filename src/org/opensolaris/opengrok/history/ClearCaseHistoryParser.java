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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * Parse a stream of ClearCase log comments.
 */
class ClearCaseHistoryParser implements HistoryParser {
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.US);

    public History parse(File file, Repository repos) throws HistoryException {
        try {
            return parseFile(file, repos);
        } catch (IOException ioe) {
            throw new HistoryException(ioe);
        }
    }

    private History parseFile(File file, Repository repos)
            throws IOException {
        ClearCaseRepository mrepos = (ClearCaseRepository) repos;
        History history = new History();

        Process process = null;
        BufferedReader in = null;
        try {
            process = mrepos.getHistoryLogProcess(file);
            if (process == null) {
                return null;
            }

            ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();

            InputStream is = process.getInputStream();
            in = new BufferedReader(new InputStreamReader(is));

            String s;
            HistoryEntry entry = null;
            while ((s = in.readLine()) != null) {
                if (!"create version".equals(s) &&
                   !"create directory version".equals(s)) {
                    // skip this history entry
                    while ((s = in.readLine()) != null) {
                        if (".".equals(s)) {
                            break;
                        }
                    }
                    continue;
                }

                entry = new HistoryEntry();
                if ((s = in.readLine()) != null) {
                    synchronized (FORMAT) {
                        try {
                            entry.setDate(FORMAT.parse(s));
                        } catch (ParseException pe) {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "Could not parse date: " + s, pe);
                        }
                    }
                }
                if ((s = in.readLine()) != null) {
                    entry.setAuthor(s);
                }
                if ((s = in.readLine()) != null) {
                    s = s.replace('\\', '/');
                    entry.setRevision(s);
                }

                StringBuffer message = new StringBuffer();
                String glue = "";
                while ((s = in.readLine()) != null && !".".equals(s)) {
                    if ("".equals(s)) {
                        // avoid empty lines in comments
                        continue;
                    }
                    message.append(glue);
                    message.append(s.trim());
                    glue = "\n";
                }
                entry.setMessage(message.toString());

                entry.setActive(true);

                entries.add(entry);
            }

            history.setHistoryEntries(entries);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException exp) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while closing stream.", exp);
                }
            }

            // Clean up zombie-processes...
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
