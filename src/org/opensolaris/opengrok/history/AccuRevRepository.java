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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to an AccuRev repository (here an actual user workspace)
 *
 * AccuRev requires that a user logs into their system before it can be used. So
 * on the machine acting as the OpenGrok server, some valid user has to be
 * permanently logged in. (accurev login -n &lt;user&gt;)
 *
 * It appears that the file path that is given to all these methods is the
 * complete path to the file which includes the path to the root of the source
 * location. This means that when using the -P option of OpenGrok to make all
 * the directories pointed to by the source root to be seen as separate projects
 * is not all as it would seem. The History GURU always starts building the
 * history cache using the source root. Well there is NO HISTORY for anything at
 * the source root because it is not part of an actual AccuRev depot. The
 * directories within the source root directory represent the work areas of
 * AccuRev and it is those areas where history can be obtained. This
 * implementation allows those directories to be symbolic links to the actual
 * workspaces.
 *
 * Other assumptions:
 *
 * There is only one associated AccuRev depot associated with workspaces.
 *
 * @author Steven Haehn
 */
public class AccuRevRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccuRevRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY
            = "org.opensolaris.opengrok.history.AccuRev";
    /**
     * The command to use to access the repository if none was given explicitly
     */
    public static final String CMD_FALLBACK = "accurev";
    private static final Pattern annotationPattern
            = Pattern.compile("^\\s+(\\d+\\\\\\d+)\\s+(\\w+)\\s+.*");   // version, user, code line
    private static final Pattern depotPattern
            = Pattern.compile("^Depot:\\s+(\\w+)");
    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    public AccuRevRepository() {
        type = "AccuRev";
        datePatterns = new String[]{
            "yyyy/MM/dd hh:mm:ss"
        };
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
    }

    @Override
    public Annotation annotate(File file, String rev) throws IOException {

        Annotation a = new Annotation(file.getName());

        ArrayList<String> cmd = new ArrayList<>();

        String path = file.getAbsolutePath();

        cmd.add(RepoCommand);
        cmd.add("annotate");
        cmd.add("-fvu");      // version & user

        if (rev != null) {
            cmd.add("-v");
            cmd.add(rev.trim());
        }

        cmd.add(path);

        Executor executor = new Executor(cmd, file.getParentFile());
        executor.exec();
        try (BufferedReader reader
                = new BufferedReader(executor.getOutputReader())) {
            String line;
            int lineno = 0;
            try {
                while ((line = reader.readLine()) != null) {
                    ++lineno;
                    Matcher matcher = annotationPattern.matcher(line);

                    if (matcher.find()) {
                        String version = matcher.group(1);
                        String author = matcher.group(2);
                        a.addLine(version, author, true);
                    } else {
                        LOGGER.log(Level.SEVERE,
                                "Did not find annotation in line {0}: [{1}]",
                                new Object[]{lineno, line});
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,
                        "Could not read annotations for " + file, e);
            }
        }

        return a;
    }

    /**
     * Get an executor to be used for retrieving the history log for the given
     * file. (used by AccuRevHistoryParser).
     *
     * @param file file for which history is to be retrieved.
     * @return An Executor ready to be started
     */
    Executor getHistoryLogExecutor(File file) throws IOException {

        String path = file.getAbsolutePath();

        ArrayList<String> cmd = new ArrayList<>();

        cmd.add(RepoCommand);
        cmd.add("hist");

        if (!file.isDirectory()) {
            cmd.add("-k");
            cmd.add("keep");  // get a list of all 'real' file versions
        }

        cmd.add(path);
        
        File workingDirectory = file.isDirectory() ? file : file.getParentFile();
        
        return new Executor(cmd, workingDirectory);
    }

    @Override
    InputStream getHistoryGet(String parent, String basename, String rev) {

        ArrayList<String> cmd = new ArrayList<>();
        InputStream inputStream = null;
        File directory = new File(parent);

        /*
         * ----------------------------------------------------------------- The
         * only way to guarantee getting the contents of a file is to fire off
         * an AccuRev 'stat'us command to get the element ID number for the
         * subsequent 'cat' command. (Element ID's are unique for a file, unless
         * evil twins are present) This is because it is possible that the file
         * may have been moved to a different place in the depot. The 'stat'
         * command will produce a line with the format:
         *
         * <filePath> <elementID> <virtualVersion> (<realVersion>) (<status>)
         *
         *  /./myFile e:17715 CP.73_Depot/2 (3220/2) (backed)
         *-----------------------------------------------------------------
         */
        cmd.add(RepoCommand);
        cmd.add("stat");
        cmd.add("-fe");
        cmd.add(basename);
        Executor executor = new Executor(cmd, directory);
        executor.exec();

        String elementID = null;

        try (BufferedReader info = new BufferedReader(executor.getOutputReader())) {
            String line = info.readLine();
            String[] statInfo = line.split("\\s+");
            elementID = statInfo[1].substring(2); // skip over 'e:'

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "Could not obtain status for {0}", basename);
        }

        if (elementID != null) {
            /*
             * ------------------------------------------ This really gets the
             * contents of the file.
             *------------------------------------------
             */
            cmd.clear();
            cmd.add(RepoCommand);
            cmd.add("cat");
            cmd.add("-v");
            cmd.add(rev.trim());
            cmd.add("-e");
            cmd.add(elementID);

            executor = new Executor(cmd, directory);
            executor.exec();

            inputStream
                    = new ByteArrayInputStream(executor.getOutputString().getBytes());
        }

        return inputStream;
    }

    @Override
    public void update() throws IOException {      
        File directory = new File(getDirectoryName());
        List<String> cmd = new ArrayList<>();
        
        cmd.add(RepoCommand);
        cmd.add("update");

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

    /**
     * Check if a given path is associated with an AccuRev workspace
     *
     * The AccuRev 'info' command provides a Depot name when in a known
     * workspace. Otherwise, the Depot name will be missing.
     *
     * @param path The presumed path to an AccuRev workspace directory.
     * @return true if the given path is in the depot, false otherwise
     */
    private boolean isInAccuRevDepot(File wsPath) {

        boolean status = false;

        if (isWorking()) {
            ArrayList<String> cmd = new ArrayList<>();

            cmd.add(RepoCommand);
            cmd.add("info");

            Executor executor = new Executor(cmd, wsPath);
            executor.exec(true);

            try (BufferedReader info = new BufferedReader(executor.getOutputReader())) {
                String line;
                while ((line = info.readLine()) != null) {

                    Matcher depotMatch = depotPattern.matcher(line);

                    if (line.contains("not logged in")) {
                        LOGGER.log(
                                Level.SEVERE, "Not logged into AccuRev server");
                        break;
                    }

                    if (depotMatch.find()) {
                        status = true;
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,
                        "Could not find AccuRev repository for {0}", wsPath);
            }
        }

        return status;
    }

    public String getDepotRelativePath(File file) {

        String path = File.separator + "." + File.separator;

        try {
            path = env.getPathRelativeToSourceRoot(file, 0);

            if (path.startsWith(File.separator)) {
                path = File.separator + "." + path;
            } else {
                path = File.separator + "." + File.separator + path;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Unable to determine depot relative path for {0}",
                    file.getPath());
        }

        return path;
    }

    @Override
    boolean isRepositoryFor(File sourceHome) {

        return isInAccuRevDepot(sourceHome);
    }

    @Override
    public boolean isWorking() {

        if (working == null) {

            working = checkCmd(RepoCommand, "info");
        }

        return working;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new AccuRevHistoryParser().parse(file, this);
    }

    @Override
    String determineParent() throws IOException {
        return null;
    }

    @Override
    String determineBranch() {
        return null;
    }
}
