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
 * Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.opengrok.indexer.logger.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a container for file system paths-related utility methods.
 */
public class PathUtils {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PathUtils.class);

    /**
     * Calls {@link #getRelativeToCanonical(Path, Path, Set, Set)}
     * with {@code path}, {@code canonical}, {@code allowedSymlinks=null}, and
     * {@code canonicalRoots=null} (to disable validation of links).
     * @param path a non-canonical (or canonical) path to compare
     * @param canonical a canonical path to compare against
     * @return a relative path determined as described -- or {@code path} if no
     * canonical relativity is found.
     * @throws IOException if an error occurs determining canonical paths
     * for portions of {@code path}
     */
    public static String getRelativeToCanonical(Path path, Path canonical)
        throws IOException {
        try {
            return getRelativeToCanonical(path, canonical, null, null);
        } catch (ForbiddenSymlinkException e) {
            // should not get here with allowedSymlinks==null
            return path.toString();
        }
    }

    /**
     * Determine a relative path comparing {@code path} to {@code canonical},
     * with an algorithm that can handle the possibility of one or more
     * symbolic links as components of {@code path}.
     * <p>
     * When {@code allowedSymlinks} is not null, any symbolic links as
     * components of {@code path} (below {@code canonical}) are required to
     * match an element of {@code allowedSymlinks} or target a canonical child
     * of an element of {@code allowedSymlinks}.
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
     * any links encountered within {@code path} and not covered by the set (or
     * whitelisted in a defined {@code canonicalRoots}) will abort the algorithm
     * @param canonicalRoots optional set of allowed canonicalRoots, so that
     * any checks done because of a defined {@code allowedSymlinks} will first
     * check against the whitelist of canonical roots and possibly short-circuit
     * the explicit validation against {@code allowedSymlinks}.
     * @return a relative path determined as described above -- or {@code path}
     * if no canonical relativity is found
     * @throws ForbiddenSymlinkException if symbolic-link checking is active
     * and it encounters an ineligible link
     * @throws InvalidPathException if path cannot be decoded
     */
    public static String getRelativeToCanonical(Path path, Path canonical,
            Set<String> allowedSymlinks, Set<String> canonicalRoots)
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
        final FileSystem fileSystem = path.getFileSystem();
        final String separator = fileSystem.getSeparator();
        String strPath = path.toString();
        strPath = strPath.replace("\\", separator);

        String strCanonical = canonical.toString();
        strCanonical = strCanonical.replace("\\", separator);
        String normCanonical = strCanonical.endsWith(separator) ?
            strCanonical : strCanonical + separator;
        Deque<String> tail = null;

        Path iterPath = fileSystem.getPath(strPath);
        while (iterPath != null) {
            Path iterCanon;
            try {
                iterCanon = iterPath.toRealPath();
            } catch (IOException e) {
                iterCanon = iterPath.normalize().toAbsolutePath();
            }

            // optional symbolic-link check
            if (Objects.nonNull(allowedSymlinks) && Files.isSymbolicLink(iterPath) &&
                !isWhitelisted(iterCanon.toString(), canonicalRoots) &&
                !isAllowedSymlink(iterCanon, allowedSymlinks)) {
                    String format = String.format("%1$s is prohibited symlink", iterPath);
                    LOGGER.finest(format);
                    throw new ForbiddenSymlinkException(format);
            }

            String rel = null;
            if (iterCanon.startsWith(normCanonical)) {
                rel = fileSystem.getPath(normCanonical).relativize(iterCanon).toString();
            } else if (normCanonical.equals(iterCanon + separator)) {
                rel = "";
            }
            if (rel != null) {
                if (tail != null) {
                    while (!tail.isEmpty()) {
                        rel = Paths.get(rel, tail.pop()).toString();
                    }
                }
                return rel;
            }

            if (tail == null) {
                tail = new LinkedList<>();
            }
            tail.push(Optional.ofNullable(iterPath.getFileName()).map(Path::toString).orElse(""));
            iterPath = iterPath.getParent();
        }

        // `path' is not found to be relative to `canonical', so return as is.
        return path.toString();
    }

    private static boolean isAllowedSymlink(Path canonicalFile,
        Set<String> allowedSymlinks) {
        final FileSystem fileSystem = canonicalFile.getFileSystem();
        String canonicalFileStr = canonicalFile.toString();
        for (String allowedSymlink : allowedSymlinks) {
            String canonicalLink;
            try {
                canonicalLink = fileSystem.getPath(allowedSymlink).toRealPath().toString();
            } catch (IOException e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(String.format("unresolvable symlink: %s",
                        allowedSymlink));
                }
                continue;
            }
            if (canonicalFileStr.equals(canonicalLink) ||
                canonicalFile.startsWith(canonicalLink + fileSystem.getSeparator())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWhitelisted(String canonical, Set<String> canonicalRoots) {
        if (canonicalRoots != null) {
            for (String canonicalRoot : canonicalRoots) {
                if (canonical.startsWith(canonicalRoot)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Private to enforce static. */
    private PathUtils() {
    }
}
