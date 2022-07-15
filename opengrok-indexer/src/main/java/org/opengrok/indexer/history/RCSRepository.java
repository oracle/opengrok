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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.Executor;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.LazilyInstantiate;

/**
 * Access to an RCS repository.
 */
public class RCSRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RCSRepository.class);

    private static final long serialVersionUID = 1L;

    /**
     * This property name is used to obtain the command to get annotation for this repository.
     */
    private static final String CMD_BLAME_PROPERTY_KEY = "org.opengrok.indexer.history.RCS.blame";
    /**
     * The command to use to get annotation if none was given explicitly.
     */
    private static final String CMD_BLAME_FALLBACK = "blame";

    /**
     * This is a static replacement for 'working' field. Effectively, check if hg is working once in a JVM
     * instead of calling it for every MercurialRepository instance.
     */
    private static final Supplier<Boolean> BLAME_IS_WORKING = LazilyInstantiate.using(RCSRepository::isBlameWorking);

    public RCSRepository() {
        type = "RCS";

        ignoredDirs.add("RCS");
    }

    private static boolean isBlameWorking() {
        String repoCommand = getCommand(MercurialRepository.class, CMD_BLAME_PROPERTY_KEY, CMD_BLAME_FALLBACK);
        return checkCmd(repoCommand);
    }

    @Override
    public boolean isWorking() {
        if (working == null) {
            working = BLAME_IS_WORKING.get();
            ensureCommand(CMD_BLAME_PROPERTY_KEY, CMD_BLAME_FALLBACK);
        }
        return working;
    }

    @Override
    boolean fileHasHistory(File file) {
        return getRCSFile(file) != null;
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {
        try {
            File file = new File(parent, basename);
            File rcsFile = getRCSFile(file);
            try (InputStream in = new RCSget(rcsFile.getPath(), rev)) {
                copyBytes(out::write, in);
            }
            return true;
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE,
                    "Failed to retrieve revision " + rev + " of " + basename, ioe);
            return false;
        }
    }

    @Override
    boolean fileHasAnnotation(File file) {
        return fileHasHistory(file);
    }

    @Override
    Annotation annotate(File file, String revision) throws IOException {
        List<String> argv = new ArrayList<>();
        ensureCommand(CMD_BLAME_PROPERTY_KEY, CMD_BLAME_FALLBACK);

        argv.add(RepoCommand);
        if (revision != null) {
            argv.add("-r");
            argv.add(revision);
        }
        argv.add(file.getName());

        Executor executor = new Executor(argv, file.getParentFile(),
                RuntimeEnvironment.getInstance().getInteractiveCommandTimeout());

        RCSAnnotationParser annotator = new RCSAnnotationParser(file);
        executor.exec(true, annotator);

        return annotator.getAnnotation();
    }

    /**
     * Wrap a {@code Throwable} in an {@code IOException} and return it.
     */
    static IOException wrapInIOException(String message, Throwable t) {
        return new IOException(message + ": " + t.getMessage(), t);
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        File rcsDir = new File(file, "RCS");
        if (!rcsDir.isDirectory()) {
            return false;
        }

        // If there is at least one entry with the ',v' suffix,
        // consider this a RCS repository.
        String[] list = rcsDir.list((dir, name) -> {
            // Technically we should check whether the entry is a file
            // however this would incur additional I/O. The pattern
            // should be enough.
            return name.matches(".*,v");
        });

        return (list.length > 0);
    }

    /**
     * Get a {@code File} object that points to the file that contains
     * RCS history for the specified file.
     *
     * @param file the file whose corresponding RCS file should be found
     * @return the file which contains the RCS history, or {@code null} if it
     * cannot be found
     */
    File getRCSFile(File file) {
        File dir = new File(file.getParentFile(), "RCS");
        String baseName = file.getName();
        File rcsFile = new File(dir, baseName + ",v");
        return rcsFile.exists() ? rcsFile : null;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return false;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new RCSHistoryParser().parse(file, this);
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        return null;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) throws IOException {
        return null;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        return null;
    }
}
