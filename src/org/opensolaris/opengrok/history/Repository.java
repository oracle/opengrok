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
 * Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.OpenGrokLogger;

/**
 * An interface for an external repository. 
 *
 * @author Trond Norbye
 */
public abstract class Repository extends RepositoryInfo {

    abstract boolean fileHasHistory(File file);

    /**
     * Check if the repository supports {@code getHistory()} requests for
     * whole directories at once.
     *
     * @return {@code true} if the repository can get history for directories
     */
    abstract boolean hasHistoryForDirectories();

    /**
     * Get the history log for the specified file or directory.
     * @param file the file to get the history for
     * @return history log for file
     * @throws HistoryException on error accessing the history
     */
    abstract History getHistory(File file) throws HistoryException;

    /**
     * <p>
     * Get the history after a specified revision.
     * </p>
     *
     * <p>
     * The default implementation first fetches the full history and then
     * throws away the oldest revisions. This is not efficient, so subclasses
     * should override it in order to get good performance. Once every subclass
     * has implemented a more efficient method, the default implementation
     * should be removed and made abstract.
     * </p>
     *
     * @param file the file to get the history for
     * @param sinceRevision the revision right before the first one to return,
     * or {@code null} to return the full history
     * @return partial history for file
     * @throws HistoryException on error accessing the history
     */
    History getHistory(File file, String sinceRevision)
            throws HistoryException {

        // If we want an incremental history update and get here, warn that
        // it may be slow.
        if (sinceRevision != null) {
            Logger logger = OpenGrokLogger.getLogger();
            Level level = Level.INFO;
            logger.log(level,
                    "Incremental history retrieval is not implemented for {0}.",
                    getClass().getSimpleName());
            logger.log(level,
                    "Falling back to slower full history retrieval.");
        }

        History history = getHistory(file);

        if (sinceRevision == null) {
            return history;
        }

        List<HistoryEntry> partial = new ArrayList<HistoryEntry>();
        for (HistoryEntry entry : history.getHistoryEntries()) {
            partial.add(entry);
            if (sinceRevision.equals(entry.getRevision())) {
                // Found revision right before the first one to return.
                break;
            }
        }

        removeAndVerifyOldestChangeset(partial, sinceRevision);
        history.setHistoryEntries(partial);
        return history;
    }

    /**
     * Remove the oldest changeset from a list (assuming sorted with most
     * recent changeset first) and verify that it is the changeset we expected
     * to find there.
     *
     * @param entries a list of {@code HistoryEntry} objects
     * @param revision the revision we expect the oldest entry to have
     * @throws HistoryException if the oldest entry was not the one we expected
     */
    void removeAndVerifyOldestChangeset(List<HistoryEntry> entries,
                                        String revision)
            throws HistoryException {
        HistoryEntry entry =
                entries.isEmpty() ? null : entries.remove(entries.size() - 1);

        // TODO We should check more thoroughly that the changeset is the one
        // we expected it to be, since some SCMs may change the revision
        // numbers so that identical revision numbers does not always mean
        // identical changesets. We could for example get the cached changeset
        // and compare more fields, like author and date.
        if (entry == null || !revision.equals(entry.getRevision())) {
            throw new HistoryException("Cached revision '" + revision +
                                       "' not found in the repository");
        }
    }

    /**
     * Get an input stream that I may use to read a speciffic version of a
     * named file.
     * @param parent the name of the directory containing the file
     * @param basename the name of the file to get
     * @param rev the revision to get
     * @return An input stream containing the correct revision.
     */
    abstract InputStream getHistoryGet(
            String parent, String basename, String rev);

    /**
     * Checks whether this parser can annotate files.
     *
     * @return <code>true</code> if annotation is supported
     */
    abstract boolean fileHasAnnotation(File file);

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param revision revision of the file
     * @return an <code>Annotation</code> object
     * @throws java.io.IOException if an error occurs
     */
    abstract Annotation annotate(File file, String revision) throws IOException;

    /**
     * Create a history log cache for all of the files in this repository.
     * {@code getHistory()} is used to fetch the history for the entire
     * repository. If {@code hasHistoryForDirectories()} returns {@code false},
     * this method is a no-op.
     *
     * @param cache the cache instance in which to store the history log
     * @param sinceRevision if non-null, incrementally update the cache with
     * all revisions after the specified revision; otherwise, create the full
     * history starting with the initial revision
     *
     * @throws HistoryException on error
     */
    final void createCache(HistoryCache cache, String sinceRevision)
            throws HistoryException {
        if (!isWorking()) {
            return;
        }

        // If we don't have a directory parser, we can't create the cache
        // this way. Just give up and return.
        if (!hasHistoryForDirectories()) {
            return;
        }

        File directory = new File(getDirectoryName());

        History history;
        try {
            history = getHistory(directory, sinceRevision);
        } catch (HistoryException he) {
            if (sinceRevision == null) {
                // Failed to get full history, so fail.
                throw he;
            } else {
                // Failed to get partial history. This may have been caused
                // by changes in the revision numbers since the last update
                // (bug #14724) so we'll try to regenerate the cache from
                // scratch instead.
                OpenGrokLogger.getLogger().log(Level.INFO,
                        "Failed to get partial history. Attempting to " +
                        "recreate the history cache from scratch.", he);
                history = null;
            }
        }

        if (sinceRevision != null && history == null) {
            // Failed to get partial history, now get full history instead.
            history = getHistory(directory);
            // Got full history successfully. Clear the history cache so that
            // we can recreate it from scratch.
            cache.clear(this);
        }

        if (history != null) {
            cache.store(history, this);
        }
    }
    
    /**
     * Update the content in this repository by pulling the changes from the
     * upstream repository..
     * @throws Exception if an error occurs.
     */
    abstract void update() throws IOException;
    
    /**
     * Check if this it the right repository type for the given file.
     * 
     * @param file File to check if this is a repository for.
     * @return true if this is the correct repository for this file/directory.
     */
    abstract boolean isRepositoryFor(File file);
    
    /**
     * Returns true if this repository supports sub reporitories (a.k.a. forests).
     * 
     * @return true if this repository supports sub repositories
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    boolean supportsSubRepositories() {
        return false;
    }

    public DateFormat getDateFormat() {
        return new SimpleDateFormat(datePattern, Locale.US);
    }
}
