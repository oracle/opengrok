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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 *
 * @author Krystof Tulinger
 */
public class AcceptHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptHelper.class);

    /**
     * Check if I should accept this file into the database. This method has the
     * same effect as the call accept(null, file).
     *
     * @see AcceptHelper#accept(Project, File)
     *
     * @param file the file to check
     * @return true if the file should be included, false otherwise
     */
    public static boolean accept(File file) {
        return accept((Project) null, file);
    }

    /**
     * Check if I should accept this file into the database
     *
     * @param project if the file is relevant to some project (may be null)
     * @param file the file to check
     * @return true if the file should be included, false otherwise
     */
    public static boolean accept(Project project, File file) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (!env.getIncludedNames().isEmpty() // we have collected some included names
                && !file.isDirectory() // the filter should not affect directory names
                && !env.getIncludedNames().match(file)) {
            return false;
        }

        String absolutePath = file.getAbsolutePath();

        if (env.getIgnoredNames().ignore(file)) {
            LOGGER.log(Level.FINER, "ignoring {0}", absolutePath);
            return false;
        }

        if (!file.canRead()) {
            LOGGER.log(Level.WARNING, "Could not read {0}", absolutePath);
            return false;
        }

        try {
            String canonicalPath = file.getCanonicalPath();
            if (!absolutePath.equals(canonicalPath)
                    && !acceptSymlink(project, file)) {

                LOGGER.log(Level.FINE, "Skipped symlink ''{0}'' -> ''{1}''",
                        new Object[]{absolutePath, canonicalPath});
                return false;
            }
            //below will only let go files and directories, anything else is considered special and is not added
            if (!file.isFile() && !file.isDirectory()) {
                LOGGER.log(Level.WARNING, "Ignored special file {0}",
                        absolutePath);
                return false;
            }
        } catch (IOException exp) {
            LOGGER.log(Level.WARNING, "Failed to resolve name: {0}",
                    absolutePath);
            LOGGER.log(Level.FINE, "Stack Trace: ", exp);
        }

        if (file.isDirectory()) {
            // always accept directories so that their files can be examined
            return true;
        }

        if (HistoryGuru.getInstance().hasHistory(file)) {
            // versioned files should always be accepted
            return true;
        }

        // this is an unversioned file, check if it should be indexed
        return !env.isIndexVersionedFilesOnly();
    }

    /**
     * Check if this file should be accepted. This method has the same effect as
     * the call accept(null, parent, file).
     *
     * @see AcceptHelper#accept(Project, File, File)
     * @param parent parent directory of the file
     * @param file the file to check
     * @return true if the file should be included, false otherwise
     */
    public static boolean accept(File parent, File file) {
        return accept(null, parent, file);
    }

    /**
     * Check if this file should be accepted.
     *
     * @param project if the file is relevant to some project (may be null)
     * @param parent parent directory of the file
     * @param file the file to check
     * @return true if the file should be included, false otherwise
     */
    public static boolean accept(Project project, File parent, File file) {
        try {
            File f1 = parent.getCanonicalFile();
            File f2 = file.getCanonicalFile();
            if (f2.equals(f1)) {
                LOGGER.log(Level.INFO, "Skipping links to itself...: link '{1}' -> '{0}'",
                        new Object[]{parent.getAbsolutePath(), file.getAbsolutePath()});
                return false;
            }

            // Now, let's verify that it's not a link back up the chain...
            File t1 = f1;
            while ((t1 = t1.getParentFile()) != null) {
                if (f2.equals(t1)) {
                    LOGGER.log(Level.INFO, "Skipping links to parent...: link '{1}' -> '{0}'",
                            new Object[]{t1.getAbsolutePath(), file.getAbsolutePath()});
                    return false;
                }
            }

            return accept(project, file);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to resolve name: parent '{0}' file '{1}'",
                    new Object[]{parent.getAbsolutePath(), file.getAbsolutePath()});
        }
        return false;
    }

    /**
     * Check if I should accept the path containing a symlink. This method has
     * the same effect as the call acceptSymlink(null, file).
     *
     * @see AcceptHelper#acceptSymlink(Project, File)
     * @param file the file symlink
     * @return true if the file should be accepted, false otherwise
     * @throws java.io.IOException
     */
    public static boolean acceptSymlink(File file) throws IOException {
        return acceptSymlink(null, file);
    }

    /**
     * Check if I should accept the path containing a symlink.
     *
     * @param project if the file is relevant to some project (may be null)
     * @param file the file symlink
     * @return true if the file should be accepted, false otherwise
     * @throws java.io.IOException
     */
    public static boolean acceptSymlink(Project project, File file) throws IOException {
        String absolutePath = file.getAbsolutePath(), canonicalPath = file.getCanonicalPath();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // Always accept local symlinks
        if (isLocal(project, canonicalPath)) {
            return true;
        }

        for (String allowedSymlink : env.getAllowedSymlinks()) {
            if (absolutePath.startsWith(allowedSymlink)) {
                String allowedTarget = new File(allowedSymlink).getCanonicalPath();
                if (canonicalPath.startsWith(allowedTarget)
                        && absolutePath.substring(allowedSymlink.length()).equals(canonicalPath.substring(allowedTarget.length()))) {
                    // accept a file which is under the allowed (and dereferenced) symlink
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a file is local to the current project. If we don't have
     * projects, check if the file is in the source root.
     *
     * @param project a project (may be null)
     * @param path the path to a file
     * @return true if the file is local to the current repository
     */
    public static boolean isLocal(Project project, String path) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String srcRoot = env.getSourceRootPath();

        if (path.startsWith(srcRoot)) {
            if (env.hasProjects()) {
                if (project != null
                        && project.equals(Project.getProject(path.substring(srcRoot.length())))) {
                    // File is under the current project, so it's local.
                    return true;
                }
            } else {
                // File is under source root, and we don't have projects, so
                // consider it local.
                return true;
            }
        }

        return false;
    }
}
