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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Chris Ross <cross@distal.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * Access to a Perforce repository.
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerforceRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.Perforce";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "p4";

    public PerforceRepository() {
        type = "Perforce";

        ignoredFiles.add(".p4config");
    }

    static String protectPerforceFilename(String name) {
        /* For each of the [four] special characters, replace them with */
        /* the recognized escape sequence for perforce. */
        /* NOTE: Must replace '%' first, or that translation would */
        /* affect the output of the others. */
        String t = name.replace("%", "%25");
        t = t.replace("#", "%23");
        t = t.replace("*", "%2A");
        t = t.replace("@", "%40");
        if (LOGGER.isLoggable(Level.FINEST) && !name.equals(t)) {
            LOGGER.log(Level.FINEST,
                       "protectPerforceFilename: replaced ''{0}'' with ''{1}''",
                       new Object[]{name, t});
        }
        return t;
    }

    static String unprotectPerforceFilename(String name) {
        String t = name.replace("%40", "@");
        t = t.replace("%23", "#");
        t = t.replace("%2A", "*");
        t = t.replace("%25", "%");
        if (LOGGER.isLoggable(Level.FINEST) && !name.equals(t)) {
            LOGGER.log(Level.FINEST,
                    "unprotectPerforceFilename: replaced ''{0}'' with ''{1}''",
                    new Object[]{name, t});
        }
        return t;
    }

    @Override
    public Annotation annotate(File file, String rev) {
        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("annotate");
        cmd.add("-qci");
        cmd.add(protectPerforceFilename(file.getName()) + getRevisionCmd(rev));

        Executor executor = new Executor(cmd, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        PerforceAnnotationParser parser = new PerforceAnnotationParser(this, file);
        executor.exec(true, parser);
        
        return parser.getAnnotation();
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {

        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("print");
        cmd.add("-q");
        cmd.add(protectPerforceFilename(basename) + getRevisionCmd(rev));
        Executor executor = new Executor(cmd, new File(parent));
        // TODO: properly evaluate Perforce return code
        executor.exec();
        try {
            copyBytes(out::write, executor.getOutputStream());
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "Failed to get history for file {0}/{1} in revision {2}: ",
                    new Object[]{parent, basename, rev});
        }
        return false;
    }

    @Override
    boolean fileHasHistory(File file) {
        return true;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    /**
     * Check if a given file is in the depot.
     *
     * @param file The file to test
     * @param cmdType command timeout type
     * @return true if the given file is in the depot, false otherwise
     */
    boolean isInP4Depot(File file, CommandTimeoutType cmdType) {
        boolean status = false;
        if (isWorking()) {
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();
            ArrayList<String> cmd = new ArrayList<>();
            String name = protectPerforceFilename(file.getName());
            File dir = file.getParentFile();
            if (file.isDirectory()) {
                dir = file;
                name = "*";
                cmd.add(RepoCommand);
                cmd.add("dirs");
                cmd.add(name);
                Executor executor = new Executor(cmd, dir, env.getCommandTimeout(cmdType));
                executor.exec();
                /* OUTPUT:
                 stdout: //depot_path/name
                 stderr: name - no such file(s).
                 */
                status = (executor.getOutputString().contains("//"));
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST, "p4 status is {0} for {1}",
                            new Object[] {status, file});
                }
            }
            if (!status) {
                cmd.clear();
                cmd.add(RepoCommand);
                cmd.add("files");
                cmd.add(name);
                Executor executor = new Executor(cmd, dir, env.getCommandTimeout(cmdType));
                executor.exec();
                /* OUTPUT:
                 stdout: //depot_path/name
                 stderr: name - no such file(s).
                 */
                status = (executor.getOutputString().contains("//"));
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST, "p4 status is {0} for {1}",
                            new Object[] {status, file});
                }
            }
        }
        return status;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        return isInP4Depot(file, cmdType);
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(RepoCommand, "help");
        }
        return working;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        PerforceHistoryParser parser = new PerforceHistoryParser(this);
        return parser.parse(file);
    }

    @Override
    History getHistory(File file, String sinceRevision) throws HistoryException {
        PerforceHistoryParser parser = new PerforceHistoryParser(this);
        return parser.parse(file, sinceRevision);
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) {
        return null;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) {
        return null;
    }
    /**
     * Parse internal rev number and return it in a format suitable for P4 command-line.
     * @param rev Internal rev number.
     * @return rev number formatted for P4 command-line.
     */
    String getRevisionCmd(String rev) {
        if (rev == null || "".equals(rev)) {
            return "";
        }
        return "@" + rev;
    }
    /**
     * Parse rev numbers and return it as a range in a format suitable for P4 command-line.
     * @param first First revision number.
     * @param last Last revision number.
     * @return rev number formatted for P4 command-line.
     */
    String getRevisionCmd(String first, String last) {
        if ((first == null || "".equals(first)) &&
            ((last == null) || "".equals(last))) {
            return "";
        }
        String ret = "@";
        if (first == null || "".equals(first)) {
            ret += "0,";
        } else {
            ret += first + ",";
        }
        if (last == null || "".equals(last)) {
            ret += "now";
        } else {
            ret += last;
        }
        return ret;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        File directory = new File(getDirectoryName());
        List<String> cmd = new ArrayList<>();

        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("changes");
        cmd.add("-t");
        cmd.add("-m");
        cmd.add("1");
        cmd.add("...#have");

        Executor executor = new Executor(cmd, directory,
                RuntimeEnvironment.getInstance().getCommandTimeout(cmdType));
        if (executor.exec(false) != 0) {
            throw new IOException(executor.getErrorString());
        }

        return executor.getOutputString().trim();
    }
}
