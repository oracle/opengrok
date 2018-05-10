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
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;

/**
 * This class gives access to repositories built on top of SCCS (including
 * TeamWare).
 */
public class SCCSRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SCCSRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opensolaris.opengrok.history.SCCS";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "sccs";

    public SCCSRepository() {
        type = "SCCS";
        datePatterns = new String[]{
            "yy/MM/dd"
        };

        ignoredDirs.add("SCCS");
    }

    @Override
    public InputStream getHistoryGet(String parent, String basename, String rev) {
        try {
            File history = SCCSHistoryParser.getSCCSFile(parent, basename);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            return SCCSget.getRevision(RepoCommand, history, rev);
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "An error occurred while getting revision", ex);
            return null;
        }
    }

    private Map<String,String> getAuthors(File file) throws IOException {
        Map<String, String> authors = new HashMap<>();

        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("prs");
        argv.add("-e");
        argv.add("-d");
        argv.add(":I: :P:");
        argv.add(file.getCanonicalPath());

        Executor executor = new Executor(argv, file.getCanonicalFile().getParentFile(),
            RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        SCCSRepositoryAuthorParser parser = new SCCSRepositoryAuthorParser();
        executor.exec(true, parser);
        
        return parser.getAuthors();
    }

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     * @throws java.io.IOException if I/O exception occurs
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        Map<String,String> authors = getAuthors(file);

        ArrayList<String> argv = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("get");
        argv.add("-m");
        argv.add("-p");
        if (revision != null) {
            argv.add("-r" + revision);
        }
        argv.add(file.getCanonicalPath());
        Executor executor = new Executor(argv, file.getCanonicalFile().getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        SCCSRepositoryAnnotationParser parser = new SCCSRepositoryAnnotationParser(file, authors);
        executor.exec(true, parser);
        return parser.getAnnotation();
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public void update() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean fileHasHistory(File file) {
        String parentFile = file.getParent();
        String name = file.getName();
        File f = new File(parentFile + "/SCCS/s." + name);
        return f.exists();
    }

    @Override
    boolean isRepositoryFor(File file, boolean interactive) {
        if (file.isDirectory()) {
            File f = new File(file, "codemgr_wsdata");
            if (f.isDirectory()) {
                return true;
            }
            f = new File(file, "Codemgr_wsdata");
            if (f.isDirectory()) {
                return true;
            }
            return new File(file, "SCCS").isDirectory();
        }
        return false;
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(RepoCommand, "help", "help");
            if (!working) {
                working = checkCmd(RepoCommand, "--version");
            }
        }
        return working;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return false;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new SCCSHistoryParser().parse(file, this);
    }

    @Override
    String determineParent(boolean interactive) throws IOException {
        File parentFile = new File(getDirectoryName() + File.separator
                + "Codemgr_wsdata" + File.separator + "parent");
        String parent = null;

        if (parentFile.isFile()) {
            String line;
            try (BufferedReader in = new BufferedReader(new FileReader(parentFile))) {
                if ((line = in.readLine()) == null) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get parent for {0} (cannot read line)",
                            getDirectoryName());
                }
                if (!line.startsWith("VERSION")) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get parent for {0} (first line does not start with VERSION)",
                            getDirectoryName());
                }
                if ((parent = in.readLine()) == null) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get parent for {0} (cannot read second line)",
                            getDirectoryName());
                }
            }
        }

        return parent;
    }

    @Override
    String determineBranch(boolean interactive) {
        return null;
    }

    @Override
    String determineCurrentVersion(boolean interactive) throws IOException {
        return null;
    }
}
