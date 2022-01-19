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
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * This class gives access to repositories built on top of SCCS (including TeamWare).
 */
public class SCCSRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SCCSRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.SCCS";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "sccs";

    static final String CODEMGR_WSDATA = "Codemgr_wsdata";

    public SCCSRepository() {
        type = "SCCS";
        /*
         * Originally there was only the "yy/MM/dd" pattern however the newer SCCS implementations seem
         * to use the time as well. Some historical SCCS versions might still use that, so it is left there
         * for potential compatibility.
         */
        datePatterns = new String[]{
            "yy/MM/dd HH:mm:ss",
            "yy/MM/dd"
        };

        ignoredDirs.add("SCCS");
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {
        try {
            File history = SCCSHistoryParser.getSCCSFile(parent, basename);
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            try (InputStream in = SCCSget.getRevision(RepoCommand, history, rev)) {
                copyBytes(out::write, in);
            }
            return true;
        } catch (FileNotFoundException ex) {
            // continue below
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "An error occurred while getting revision", ex);
        }
        return false;
    }

    private Map<String, String> getAuthors(File file) throws IOException {
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
        Map<String, String> authors = getAuthors(file);

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
    public boolean fileHasHistory(File file) {
        String parentFile = file.getParent();
        String name = file.getName();
        File f = SCCSHistoryParser.getSCCSFile(parentFile, name);
        if (f == null) {
            return false;
        }
        return f.exists();
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File f = new File(file, CODEMGR_WSDATA.toLowerCase()); // OK no ROOT
            if (f.isDirectory()) {
                return true;
            }
            f = new File(file, CODEMGR_WSDATA);
            if (f.isDirectory()) {
                return true;
            }
            return new File(file, SCCSHistoryParser.SCCS_DIR_NAME).isDirectory();
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
        return new SCCSHistoryParser(this).parse(file);
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        File parentFile = Paths.get(getDirectoryName(), CODEMGR_WSDATA, "parent").toFile();
        String parent = null;

        if (parentFile.isFile()) {
            String line;
            try (BufferedReader in = new BufferedReader(new FileReader(parentFile))) {
                if ((line = in.readLine()) == null) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get parent for {0} (cannot read first line of {1})",
                            new Object[]{getDirectoryName(), parentFile.getName()});
                    return null;
                }
                if (!line.startsWith("VERSION")) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get parent for {0} (first line does not start with VERSION)",
                            getDirectoryName());
                }
                if ((parent = in.readLine()) == null) {
                    LOGGER.log(Level.WARNING,
                            "Failed to get parent for {0} (cannot read second line of {1})",
                            new Object[]{getDirectoryName(), parentFile.getName()});
                }
            }
        }

        return parent;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) {
        return null;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        return null;
    }
}
