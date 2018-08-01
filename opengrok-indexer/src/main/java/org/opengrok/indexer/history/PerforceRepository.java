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
package org.opengrok.indexer.history;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * Access to a Perforce repository
 *
 * @author Emilio Monti - emilmont@gmail.com
 */
public class PerforceRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerforceRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opengrok.indexer.history.Perforce";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "p4";

    public PerforceRepository() {
        type = "Perforce";

        ignoredFiles.add(".p4config");
    }

    @Override
    public Annotation annotate(File file, String rev) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("annotate");
        cmd.add("-qci");
        cmd.add(file.getPath() + getRevisionCmd(rev));

        Executor executor = new Executor(cmd, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        PerforceAnnotationParser parser = new PerforceAnnotationParser(file, rev);
        executor.exec(true, parser);
        
        return parser.getAnnotation();
    }

    @Override
    InputStream getHistoryGet(String parent, String basename, String rev) {
        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("print");
        cmd.add("-q");
        cmd.add(basename + getRevisionCmd(rev));
        Executor executor = new Executor(cmd, new File(parent));
        executor.exec();
        return new ByteArrayInputStream(executor.getOutputString().getBytes());
    }

    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("sync");
        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    @Override
    boolean fileHasHistory(File file) {
        return true;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    private static final PerforceRepository testRepo
            = new PerforceRepository();

    /**
     * Check if a given file is in the depot
     *
     * @param file The file to test
     * @return true if the given file is in the depot, false otherwise
     */
    public static boolean isInP4Depot(File file, boolean interactive) {
        boolean status = false;
        if (testRepo.isWorking()) {
            ArrayList<String> cmd = new ArrayList<>();
            String name = file.getName();
            File dir = file.getParentFile();
            if (file.isDirectory()) {
                dir = file;
                name = "*";
                cmd.add(testRepo.RepoCommand);
                cmd.add("dirs");
                cmd.add(name);
                Executor executor = new Executor(cmd, dir, interactive ?
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout() :
                RuntimeEnvironment.getInstance().getCommandTimeout());
                executor.exec();
                /* OUTPUT:
                 stdout: //depot_path/name
                 stderr: name - no such file(s).
                 */
                status = (executor.getOutputString().contains("//"));
            }
            if (!status) {
                cmd.clear();
                cmd.add(testRepo.RepoCommand);
                cmd.add("files");
                cmd.add(name);
                Executor executor = new Executor(cmd, dir, interactive ?
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout() :
                RuntimeEnvironment.getInstance().getCommandTimeout());
                executor.exec();
                /* OUTPUT:
                 stdout: //depot_path/name
                 stderr: name - no such file(s).
                 */
                status = (executor.getOutputString().contains("//"));
            }
        }
        return status;
    }

    @Override
    boolean isRepositoryFor(File file, boolean interactive) {
        return isInP4Depot(file, interactive);
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
        return new PerforceHistoryParser().parse(file, this);
    }

    @Override
    String determineParent(boolean interactive) throws IOException {
        return null;
    }

    @Override
    String determineBranch(boolean interactive) {
        return null;
    }
    /**
     * Parse internal rev number and returns it in format suitable for P4 command-line.
     * @param rev Internal rev number.
     * @return rev number formatted for P4 command-line.
     */
    public static String getRevisionCmd(String rev) {
        if(rev == null || "".equals(rev)) {
            return "";
        }
        return "@" + rev;
    }

    @Override
    String determineCurrentVersion(boolean interactive) throws IOException {
        return null;
    }
}
