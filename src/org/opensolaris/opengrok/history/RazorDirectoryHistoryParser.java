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
import java.util.ArrayList;
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * A Directory History Parser for Razor
 * 
 * @author Peter Bray <Peter.Darren.Bray@gmail.com>
 */
public class RazorDirectoryHistoryParser extends DirectoryHistoryParser {

    @Override
    public History parse(File directory, Repository repository) throws Exception {

        RazorRepository repo = (RazorRepository) repository;

        File mappedDirectory = repo.getRazorHistoryFileFor(directory);

        if (!mappedDirectory.isDirectory()) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "RazorDirectoryHistory::parse( " + directory.getPath() + " ) is NOT A DIRECTORY");
            return null;
        }

        HistoryEntry entry = new HistoryEntry();
        traverse(directory, repo, entry);

        ArrayList<HistoryEntry> entries = new ArrayList<HistoryEntry>();
        entries.add(entry);
        // entry.dump();

        History history = new History();
        history.setHistoryEntries(entries);
        return history;
    }

    private void traverse(File directory, RazorRepository repo, HistoryEntry entry) throws Exception {

        for (String filename : directory.list()) {
            if (!".razor".equals(filename)) {
                File file = new File(directory, filename);
                File mappedFile = repo.getRazorHistoryFileFor(file);
                String opengrokName = repo.getOpenGrokFileNameFor(file);

                if (!mappedFile.isDirectory()) {
                    entry.addFile(opengrokName);
                } else {
                    traverse(file, repo, entry);
                }
            }
        }
    }
}
