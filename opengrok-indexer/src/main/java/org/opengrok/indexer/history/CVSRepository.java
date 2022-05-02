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
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * Access to a CVS repository.
 */
public class CVSRepository extends RCSRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CVSRepository.class);

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.cvs";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "cvs";

    public CVSRepository() {
        /*
         * This variable is set in the ancestor to TRUE which has a side effect
         * that this repository is always marked as working even though it does
         * not have the binary available on the system.
         *
         * Setting this to null restores the default behavior (as java
         * default for reference is null) for this repository - detecting the
         * binary and act upon that.
         *
         * @see #isWorking
         */
        working = null;
        setType("CVS");
        datePatterns = new String[]{
            "yyyy-MM-dd hh:mm:ss"
        };

        ignoredFiles.add(".cvsignore");
        ignoredDirs.add("CVS");
        ignoredDirs.add("CVSROOT");
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            working = checkCmd(RepoCommand, "--version");
        }
        return working;
    }

    @Override
    public void setDirectoryName(File directory) {
        super.setDirectoryName(directory);

        if (isWorking()) {
            File rootFile = new File(getDirectoryName() + File.separatorChar
                    + "CVS" + File.separatorChar + "Root");
            String root;

            if (!rootFile.exists()) {
                LOGGER.log(Level.FINE, "CVS Root file {0} does not exist", rootFile);
                return;
            }

            try (BufferedReader input = new BufferedReader(new FileReader(rootFile))) {
                root = input.readLine();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, String.format("failed to read %s", rootFile), e);
                return;
            }

            if (!root.startsWith("/")) {
                setRemote(true);
            }
        }
    }

    @Override
    File getRCSFile(File file) {
        File cvsFile
                = RCSHistoryParser.getCVSFile(file.getParent(), file.getName());
        if (cvsFile != null && cvsFile.exists()) {
            return cvsFile;
        }
        return null;
    }

    @Override
    public boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File cvsDir = new File(file, "CVS");
            return cvsDir.isDirectory();
        }
        return false;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) throws IOException {
        String branch = null;

        File tagFile = new File(getDirectoryName(), "CVS/Tag");
        if (tagFile.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(tagFile))) {
                String line = br.readLine();
                if (line != null) {
                    branch = line.substring(1);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING,
                    "Failed to work with CVS/Tag file of {0}",
                    getDirectoryName() + ": " + ex.getClass().toString());
            } catch (Exception exp) {
                LOGGER.log(Level.WARNING,
                    "Failed to get revision tag of {0}",
                    getDirectoryName() + ": " + exp.getClass().toString());
            }
        }

        return branch;
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
        cmd.add("log");

        if (getBranch() != null && !getBranch().isEmpty()) {
            // Generate history on this branch and follow up to the origin.
            cmd.add("-r1.1:" + branch);
        } else {
            // Get revisions on this branch only (otherwise the revisions
            // list produced by the cvs log command would be unsorted).
            cmd.add("-b");
        }

        if (filename.length() > 0) {
            cmd.add(filename);
        }

        return new Executor(cmd, new File(getDirectoryName()));
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {
        String revision = rev;
        if (rev.indexOf(':') != -1) {
            revision = rev.substring(0, rev.indexOf(':'));
        }

        try {
            ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
            String[] argv = {RepoCommand, "up", "-p", "-r", revision, basename};
            Executor executor = new Executor(Arrays.asList(argv), new File(parent),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
            executor.exec();

            byte[] buffer = new byte[32 * 1024];
            try (InputStream in = executor.getOutputStream()) {
                int len;

                while ((len = in.read(buffer)) != -1) {
                    if (len > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        } catch (Exception exp) {
            LOGGER.log(Level.WARNING, "Failed to get history", exp);
            return false;
        }

        return true;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public boolean fileHasHistory(File file) {
        // @TODO: Research how to cheaply test if a file in a given
        // CVS repo has history.  If there is a cheap test, then this
        // code can be refined, boosting performance.
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new CVSHistoryParser().parse(file, this);
    }

    @Override
    Annotation annotate(File file, String revision) throws IOException {
        ArrayList<String> cmd = new ArrayList<>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(RepoCommand);
        cmd.add("annotate");
        if (revision != null) {
            cmd.add("-r");
            cmd.add(revision);
        } else if (getBranch() != null && !getBranch().isEmpty()) {
            cmd.add("-r");
            cmd.add(getBranch());
        }
        cmd.add(file.getName());

        Executor exec = new Executor(cmd, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());
        CVSAnnotationParser parser = new CVSAnnotationParser(file.getName());
        int status = exec.exec(true, parser);

        if (status != 0) {
            LOGGER.log(Level.WARNING,
                    "Failed to get annotations for: \"{0}\" Exit code: {1}",
                    new Object[]{file.getAbsolutePath(), String.valueOf(status)});
        }

        return parser.getAnnotation();
    }

    @Override
    @Nullable
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        File rootFile = new File(getDirectoryName() + File.separator + "CVS"
                + File.separator + "Root");
        String parent = null;

        if (rootFile.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(rootFile))) {
                parent = br.readLine();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, String.format("Failed to read CVS/Root file %s", rootFile), ex);
            }
        }

        return parent;
    }
}
