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
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.Executor;

/**
 * Parse a stream of mercurial log comments.
 */
class MercurialHistoryParser implements HistoryParser, Executor.StreamHandler {

    private History history;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm ZZZZ", Locale.US);
    String mydir;
    int rootLength;

    public History parse(File file, Repository repos) throws IOException {
        MercurialRepository mrepos = (MercurialRepository) repos;
        mydir = mrepos.getDirectoryName() + File.separator;
        rootLength = RuntimeEnvironment.getInstance().getSourceRootPath().length();

        Executor executor = mrepos.getHistoryLogExecutor(file);
        int status = executor.exec(true, this);

        if (status != 0) {
            OpenGrokLogger.getLogger().log(Level.INFO, "Failed to get history for: \"" +
                    file.getAbsolutePath() + "\" Exit code: " + status);
        }

        return history;
    }

    /**
     * Process the output from the hg log command and insert the HistoryEntries
     * into the history field.
     *
     * @param input The output from the process
     * @throws java.io.IOException If an error occurs while reading the stream
     */
    public void processStream(InputStream input) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        String s;
        boolean description = false;
        HistoryEntry entry = null;
        while ((s = in.readLine()) != null) {
            if (s.startsWith("changeset:")) {
                if (entry != null) {
                    entries.add(entry);
                }
                entry = new HistoryEntry();
                entry.setActive(true);
                entry.setRevision(s.substring("changeset:".length()).trim());
                description = false;
            } else if (s.startsWith("user:") && entry != null) {
                entry.setAuthor(s.substring("user:".length()).trim());
                description = false;
            } else if (s.startsWith("date:") && entry != null) {
                Date date = new Date();
                try {
                    date = df.parse(s.substring("date:".length()).trim());
                } catch (ParseException pe) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Could not parse date: " + s, pe);
                }
                entry.setDate(date);
                description = false;
            } else if (s.startsWith("files:") && entry != null) {
                description = false;
                String[] strings = s.split(" ");
                for (int ii = 1; ii < strings.length; ++ii) {
                    if (strings[ii].length() > 0) {
                        File f = new File(mydir, strings[ii]);
                        String name = f.getCanonicalPath().substring(rootLength);
                        entry.addFile(name);
                    }
                }
            } else if (s.startsWith("description:") && entry != null) {
                description = true;
            } else if (description && entry != null) {
                entry.appendMessage(s);
            }
        }

        if (entry != null) {
            entries.add(entry);
        }

        history = new History();
        history.setHistoryEntries(entries);
    }
}
