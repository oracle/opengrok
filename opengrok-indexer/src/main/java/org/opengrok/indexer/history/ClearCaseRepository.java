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
 * Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.BufferSink;
import org.opengrok.indexer.util.Executor;

/**
 * Access to a ClearCase repository.
 *
 */
public class ClearCaseRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearCaseRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.ClearCase";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "cleartool";

    public ClearCaseRepository() {
        type = "ClearCase";
        datePatterns = new String[]{
            "yyyyMMdd.HHmmss"
        };
    }

    /**
     * Get an executor to be used for retrieving the history log for the named
     * file.
     *
     * @param file The file to retrieve history for
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(final File file) throws IOException {
        String filename = getRepoRelativePath(file);

        List<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("lshistory");
        if (file.isDirectory()) {
            cmd.add("-dir");
        }
        cmd.add("-fmt");
        cmd.add("%e\n%Nd\n%Fu (%u)\n%Vn\n%Nc\n.\n");
        cmd.add(filename);

        return new Executor(cmd, new File(getDirectoryName()));
    }

    @Override
    boolean getHistoryGet(
            BufferSink sink, String parent, String basename, String rev) {

        File directory = new File(getDirectoryName());

        try {
            String filename = (new File(parent, basename)).getCanonicalPath()
                    .substring(getDirectoryName().length() + 1);
            final File tmp = File.createTempFile("opengrok", "tmp");
            String tmpName = tmp.getCanonicalPath();

            // cleartool can't get to a previously existing file
            if (tmp.exists() && !tmp.delete()) {
                LOGGER.log(Level.WARNING,
                        "Failed to remove temporary file used by history cache");
            }

            String decorated = filename + "@@" + rev;
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String[] argv = {RepoCommand, "get", "-to", tmpName, decorated};
            Executor executor = new Executor(Arrays.asList(argv), directory,
                    RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
            int status = executor.exec();
            if (status != 0) {
                LOGGER.log(Level.SEVERE, "Failed to get history: {0}",
                        executor.getErrorString());
                return false;
            }

            try (FileInputStream in = new FileInputStream(tmp)) {
                copyBytes(sink, in);
            } finally {
                // delete the temporary file on close
                if (!tmp.delete()) {
                    // failed, lets do the next best thing then ..
                    // delete it on JVM exit
                    tmp.deleteOnExit();
                }
            }
            return true;
        } catch (Exception exp) {
            LOGGER.log(Level.WARNING,
                    "Failed to get history: " + exp.getClass().toString(), exp);
        }

        return false;
    }

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation
     * @throws java.io.IOException if I/O exception occurred
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> argv = new ArrayList<>();

        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        argv.add(RepoCommand);
        argv.add("annotate");
        argv.add("-nheader");
        argv.add("-out");
        argv.add("-");
        argv.add("-f");
        argv.add("-fmt");
        argv.add("%u|%Vn|");

        if (revision != null) {
            argv.add(revision);
        }
        argv.add(file.getName());

        Executor executor = new Executor(argv, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        ClearCaseAnnotationParser parser = new ClearCaseAnnotationParser(file.getName());
        executor.exec(true, parser);

        return parser.getAnnotation();
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether ClearCase has history
        // available for a file?
        // Otherwise, this is harmless, since ClearCase's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(RepoCommand, "-version");
        }
        return working;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        // if the parent contains a file named "[.]view.dat" or
        // the parent is named "vobs" or the canonical path
        // is found in "cleartool lsvob -s"
        File fWindows = new File(file, "view.dat");
        File fUnix = new File(file, ".view.dat");
        if (fWindows.exists() || fUnix.exists()) {
            return true;
        } else if (file.isDirectory() && file.getName().equalsIgnoreCase("vobs")) {
            return true;
        } else if (isWorking()) {
            try {
                String canonicalPath = file.getCanonicalPath();
                for (String vob : getAllVobs()) {
                    if (canonicalPath.equalsIgnoreCase(vob)) {
                        return true;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        "Could not get canonical path for \"" + file + "\"", e);
            }
        }
        return false;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        return null;
    }

    private static class VobsHolder {
        static String[] vobs = runLsvob();
    }

    private static String[] getAllVobs() {
        return VobsHolder.vobs;
    }

    private static final ClearCaseRepository testRepo
            = new ClearCaseRepository();

    private static String[] runLsvob() {
        if (testRepo.isWorking()) {
            Executor exec = new Executor(
                    new String[]{testRepo.RepoCommand, "lsvob", "-s"});
            int rc;
            if ((rc = exec.exec(true)) == 0) {
                String output = exec.getOutputString();

                if (output == null) {
                    LOGGER.log(Level.SEVERE,
                            "\"cleartool lsvob -s\" output was null");
                    return new String[0];
                }
                String sep = System.getProperty("line.separator");
                String[] vobs = output.split(Pattern.quote(sep));
                LOGGER.log(Level.CONFIG, "Found VOBs: {0}",
                        Arrays.asList(vobs));
                return vobs;
            }
            LOGGER.log(Level.SEVERE,
                    "\"cleartool lsvob -s\" returned non-zero status: {0}", rc);
        }
        return new String[0];
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new ClearCaseHistoryParser().parse(file, this);
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        return null;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) {
        return null;
    }
}
