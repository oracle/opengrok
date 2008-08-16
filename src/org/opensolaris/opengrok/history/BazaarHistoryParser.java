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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

/**
 * Parse a stream of Bazaar log comments.
 */
class BazaarHistoryParser implements HistoryParser {

    public History parse(File file, Repository repos)
            throws IOException, ParseException {
        BazaarRepository mrepos = (BazaarRepository) repos;
        History history = new History();

        Process process = null;
        BufferedReader in = null;
        try {
            process = mrepos.getHistoryLogProcess(file);
            if (process == null) {
                return null;
            }

            SimpleDateFormat df =
                    new SimpleDateFormat("EEE yyyy-MM-dd hh:mm:ss ZZZZ");
            ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();

            String mydir = mrepos.getDirectoryName() + File.separator;
            int rootLength = RuntimeEnvironment.getInstance().getSourceRootPath().length();

            InputStream is = process.getInputStream();
            in = new BufferedReader(new InputStreamReader(is));
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
                if (s.equals("------------------------------------------------------------")) {
                    if (entry != null && state == 4) {
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
                            String rev = s.substring("revno:".length()).trim();
                            entry.setRevision(rev);
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
                            } catch (Exception e) {
                                OpenGrokLogger.getLogger().log(Level.INFO, 
                                        "Failed to parse history timestamp for " + file, e);
                            }
                            ++state;
                        }
                        break;
                    case 3:
                        if (ident == nident && s.startsWith("message:")) {
                        // Just swallow
                        } else if (ident == nident && (s.startsWith("modified:") || s.startsWith("added:") || s.startsWith("removed:"))) {
                            ++state;
                        } else {
                            entry.appendMessage(s);
                        }
                        break;
                    case 4:
                        if (!(s.startsWith("modified:") || s.startsWith("added:") || s.startsWith("removed:"))) {
                            int idx = s.indexOf(" => ");
                            if (idx != -1) {
                                s = s.substring(idx + 4);
                            }

                            File f = new File(mydir, s);
                            String name = f.getCanonicalPath().substring(rootLength);
                            entry.addFile(name);
                        }
                        break;
                }
            }

            if (entry != null && state == 4) {
                entries.add(entry);
            }

            history.setHistoryEntries(entries);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException exp) {
                    // ignore
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
