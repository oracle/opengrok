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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import org.apache.commons.jrcs.diff.PatchFailedException;
import org.apache.commons.jrcs.rcs.Archive;
import org.apache.commons.jrcs.rcs.InvalidFileFormatException;
import org.apache.commons.jrcs.rcs.Node;
import org.apache.commons.jrcs.rcs.ParseException;
import org.apache.commons.jrcs.rcs.Version;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * Access to an RCS repository.
 */
public class RCSRepository extends Repository {
    private static final long serialVersionUID = 1L;

    public RCSRepository() {
        working = Boolean.TRUE;
        type = "RCS";
    }

    @Override
    boolean fileHasHistory(File file) {
        return getRCSFile(file) != null;
    }

    @Override
    InputStream getHistoryGet(String parent, String basename, String rev) {
        try {
            File file = new File(parent, basename);
            File rcsFile = getRCSFile(file);
            return new RCSget(rcsFile.getPath(), rev);
        } catch (IOException ioe) {
            OpenGrokLogger.getLogger().log(Level.SEVERE, 
                    "Failed to retrieve revision " + rev + " of " + basename, ioe);
            return null;
        }
    }

    @Override
    boolean fileHasAnnotation(File file) {
        return fileHasHistory(file);
    }

    @Override
    Annotation annotate(File file, String revision) throws IOException {
        File rcsFile = getRCSFile(file);
        return rcsFile == null ? null : annotate(file, revision, rcsFile);
    }

    static Annotation annotate(File file, String revision, File rcsFile)
            throws IOException {
        try {
            Archive archive = new Archive(rcsFile.getPath());
            // If revision is null, use current revision
            Version version = revision == null ? archive.getRevisionVersion() : archive.getRevisionVersion(revision);
            // Get the revision with annotation
            archive.getRevision(version, true);
            Annotation a = new Annotation(file.getName());
            // A comment in Archive.getRevisionNodes() says that it is not
            // considered public API anymore, but it works.
            for (Node n : archive.getRevisionNodes()) {
                String rev = n.getVersion().toString();
                String author = n.getAuthor();
                a.addLine(rev, author, true);
            }
            return a;
        } catch (ParseException pe) {
            throw wrapInIOException(
                    "Parse exception annotating RCS repository", pe);
        } catch (InvalidFileFormatException iffe) {
            throw wrapInIOException(
                    "File format exception annotating RCS repository", iffe);
        } catch (PatchFailedException pfe) {
            throw wrapInIOException(
                    "Patch failed exception annotating RCS repository", pfe);
        }
    }

    /**
     * Wrap a {@code Throwable} in an {@code IOException} and return it.
     */
    static IOException wrapInIOException(String message, Throwable t) {
        // IOException's constructor takes a Throwable, but only in JDK 6
        IOException ioe = new IOException(message + ": " + t.getMessage());
        ioe.initCause(t);
        return ioe;
    }

    @Override
    void update() throws IOException {
        throw new IOException("Not supported yet.");
    }

    @Override
    boolean isRepositoryFor(File file) {
        File rcsDir = new File(file, "RCS");
        return rcsDir.isDirectory();
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
}
