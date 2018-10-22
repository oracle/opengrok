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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Represents a container for file system paths-related utility methods.
 */
public class PathUtils {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PathUtils.class);

    /**
     * Calls
     * {@link #getRelativeToCanonical(java.lang.String, java.lang.String, java.util.Set)}
     * with {@code path}, {@code canonical}, and {@code allowedSymlinks=null}
     * (to disable validation of links).
     * @param path a non-canonical (or canonical) path to compare
     * @param canonical a canonical path to compare against
     * @return a relative path determined as described for
     * {@link #getRelativeToCanonical(java.lang.String, java.lang.String, java.util.Set)}
     * when {@code allowedSymlinks==null} -- or {@code path} if no canonical
     * relativity is found.
     * @throws IOException if an error occurs determining canonical paths
     * for portions of {@code path}
     */
    public static String getRelativeToCanonical(String path, String canonical)
        throws IOException {
        try {
            return getRelativeToCanonical(path, canonical, null);
        } catch (ForbiddenSymlinkException e) {
            // should not get here with allowedSymlinks==null
            return path;
        }
    }

    /**
     * Determine a relative path comparing {@code path} to {@code canonical},
     * with an algorithm that can handle the possibility of one or more
     * symbolic links as components of {@code path}.
     * <p>
     * When {@code allowedSymlinks} is not null, any symbolic links as
     * components of {@code path} (below {@code canonical}) are required to
     * match an element of {@code allowedSymlinks}.
     * <p>
     * E.g., with {@code path="/var/opengrok/src/proj_a"} and
     * {@code canonical="/private/var/opengrok/src"} where /var is linked to
     * /private/var and where /var/opengrok/src/proj_a is linked to /proj/a,
     * the function will return {@code "proj_a"} as a relative path.
     * <p>
     * The algorithm will have evaluated canonical paths upward from
     * (non-canonical) /var/opengrok/src/proj_a (a.k.a. /proj/a) to find a
     * canonical similarity at /var/opengrok/src (a.k.a.
     * /private/var/opengrok/src).
     * @param path a non-canonical (or canonical) path to compare
     * @param canonical a canonical path to compare against
     * @param allowedSymlinks optional set of allowed symbolic links, so that
     * any links encountered within {@code path} and not covered by the set
     * will abort the algorithm
     * @return a relative path determined as described above -- or {@code path}
     * if no canonical relativity is found
     * @throws IOException if an error occurs determining canonical paths
     * for portions of {@code path}
     * @throws ForbiddenSymlinkException if symbolic-link checking is active
     * and it encounters an ineligible link
     * @throws InvalidPathException if path cannot be decoded
     */
    public static String getRelativeToCanonical(String path, String canonical,
        Set<String> allowedSymlinks)
            throws IOException, ForbiddenSymlinkException, InvalidPathException {

        if (path.equals(canonical)) {
            return "";
        }

        // The following fixup of \\ is really to allow
        // IndexDatabaseTest.testGetDefinitions() to succeed on Linux or macOS.
        // That test has an assertion that operation is the "same for windows
        // delimiters" and passes a path with backslashes. On Windows, the
        // following fixup would not be needed, since File and Paths recognize
        // backslash as a delimiter. On Linux and macOS, any backslash needs to
        // be normalized.
        path = path.replace('\\', File.separatorChar);
        canonical = canonical.replace('\\', File.separatorChar);
        String normCanonical = canonical.endsWith(File.separator) ?
            canonical : canonical + File.separator;
        Deque<String> tail = null;

        File iterPath = new File(path);
        while (iterPath != null) {
            String iterCanon = iterPath.getCanonicalPath();

            // optional symbolic-link check
            if (allowedSymlinks != null) {
                String iterOriginal = iterPath.getPath();
                if (Files.isSymbolicLink(Paths.get(iterOriginal)) &&
                    !isAllowedSymlink(iterCanon, allowedSymlinks)) {
                    String format = String.format("%1$s is prohibited symlink",
                        iterOriginal);
                    LOGGER.finest(format);
                    throw new ForbiddenSymlinkException(format);
                }
            }

            String rel = null;
            if (iterCanon.startsWith(normCanonical)) {
                rel = iterCanon.substring(normCanonical.length());
            } else if (normCanonical.equals(iterCanon + File.separator)) {
                rel = "";
            }
            if (rel != null) {
                if (tail != null) {
                    while (tail.size() > 0) {
                        rel = Paths.get(rel, tail.pop()).toString();
                    }
                }
                return rel;
            }

            if (tail == null) {
                tail = new LinkedList<>();
            }
            tail.push(iterPath.getName());
            iterPath = iterPath.getParentFile();
        }

        // `path' is not found to be relative to `canonical', so return as is.
        return path;
    }

    private static boolean isAllowedSymlink(String canonicalFile,
        Set<String> allowedSymlinks) {
        for (String allowedSymlink : allowedSymlinks) {
            String canonicalLink;
            try {
                canonicalLink = new File(allowedSymlink).getCanonicalPath();
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(String.format("unresolvable symlink: %s",
                        allowedSymlink));
                }
                continue;
            }
            if (canonicalFile.equals(canonicalLink)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a path relative to source root. This would just be a simple
     * substring operation, except we need to support symlinks outside the
     * source root.
     *
     * @param file A file to resolve
     * @param stripCount Number of characters past source root to strip
     * @return Path relative to source root
     * @throws IOException if an IO error occurs
     * @throws FileNotFoundException if the file is not relative to source root
     * @throws ForbiddenSymlinkException if symbolic-link checking encounters
     * an ineligible link
     * @throws InvalidPathException if the path cannot be decoded
     */
    public static String getPathRelativeToSourceRoot(File file, int stripCount)
            throws IOException, ForbiddenSymlinkException, FileNotFoundException,
            InvalidPathException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String sourceRoot = env.getSourceRootPath();
        if (sourceRoot == null) {
            throw new FileNotFoundException("Source Root Not Found");
        }

        String maybeRelPath = PathUtils.getRelativeToCanonical(file.getPath(),
                sourceRoot, env.getAllowedSymlinks());
        File maybeRelFile = new File(maybeRelPath);
        if (!maybeRelFile.isAbsolute()) {
            // N.b. OpenGrok has a weird convention that
            // source-root "relative" paths must start with a '/' as they are
            // elsewhere directly appended to env.getSourceRootPath() and also
            // stored as such.
            maybeRelPath = File.separator + maybeRelPath;
            return maybeRelPath.substring(stripCount);
        }

        throw new FileNotFoundException("Failed to resolve [" + file.getPath()
                + "] relative to source root [" + sourceRoot + "]");
    }

    /** private to enforce static */
    private PathUtils() {
    }
}
