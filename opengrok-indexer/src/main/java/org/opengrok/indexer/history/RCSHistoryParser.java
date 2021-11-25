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
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.suigeneris.jrcs.rcs.Archive;
import org.suigeneris.jrcs.rcs.impl.Node;
import org.suigeneris.jrcs.rcs.parse.ParseException;
import org.suigeneris.jrcs.rcs.Version;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Virtualise RCS file as a reader, getting a specified version.
 */
class RCSHistoryParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(RCSHistoryParser.class);

    private static File readCVSRoot(File root, File cvsDir, String name) throws IOException {
        String cvsroot = readFirstLine(root);

        if (cvsroot == null) {
            return null;
        }
        if (cvsroot.charAt(0) != '/') {
            return null;
        }

        File repository = new File(cvsDir, "Repository");
        String repo = readFirstLine(repository);
        String dir = cvsroot + File.separatorChar + repo;
        String filename = name + ",v";
        File rcsFile = new File(dir, filename);
        if (!rcsFile.exists()) {
            File atticFile = new File(dir + File.separatorChar + "Attic", filename);
            if (atticFile.exists()) {
                rcsFile = atticFile;
            }
        }
        return rcsFile;
    }

    History parse(File file, Repository repos) throws HistoryException {
        try {
            return parseFile(file);
        } catch (IOException ioe) {
            throw new HistoryException(ioe);
        }
    }

    private History parseFile(File file) throws IOException {
        File rcsfile = getRCSFile(file);
        if (rcsfile == null) {
            return null;
        }

        try {
            Archive archive = new Archive(rcsfile.getPath());
            Version ver = archive.getRevisionVersion();
            Node n = archive.findNode(ver);
            n = n.root();

            ArrayList<HistoryEntry> entries = new ArrayList<>();
            traverse(n, entries);

            History history = new History();
            history.setHistoryEntries(entries);
            return history;
        } catch (ParseException pe) {
            throw RCSRepository.wrapInIOException(
                    "Could not parse file " + file.getPath(), pe);
        }
    }

    private void traverse(Node n, List<HistoryEntry> history) {
        if (n == null) {
            return;
        }
        traverse(n.getChild(), history);
        TreeMap<?, ?> brt = n.getBranches();
        if (brt != null) {
            for (Object o : brt.values()) {
                Node b = (Node) o;
                traverse(b, history);
            }
        }
        if (!n.isGhost()) {
            HistoryEntry entry = new HistoryEntry();
            entry.setRevision(n.getVersion().toString());
            entry.setDate(n.getDate());
            entry.setAuthor(n.getAuthor());
            entry.setMessage(n.getLog());
            entry.setActive(true);
            history.add(entry);
        }
    }

    protected static File getRCSFile(File file) {
        return getRCSFile(file.getParent(), file.getName());
    }

    protected static File getRCSFile(String parent, String name) {
        File rcsDir = new File(parent, "RCS");
        File rcsFile = new File(rcsDir, name + ",v");
        if (rcsFile.exists()) {
            return rcsFile;
        }
        // not RCS, try CVS instead
        return getCVSFile(parent, name);
    }

    protected static File getCVSFile(String parent, String name) {
        try {
            File CVSdir = new File(parent, "CVS");
            if (CVSdir.isDirectory() && CVSdir.canRead()) {
                File root = new File(CVSdir, "Root");
                if (root.canRead()) {
                    return readCVSRoot(root, CVSdir, name);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Failed to retrieve CVS file of parent: " + parent + ", name: " + name, e);
        }
        return null;
    }

    /**
     * Read the first line of a file.
     * @param file the file from which to read
     * @return the first line of the file, or {@code null} if the file is empty
     * @throws IOException if an I/O error occurs while reading the file
     */
    private static String readFirstLine(File file) throws IOException {
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            return in.readLine();
        }
    }
}
