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
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import static org.opengrok.indexer.history.HistoryEntry.TAGS_SEPARATOR;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.BufferSink;
import org.opengrok.indexer.util.Executor;

import org.jetbrains.annotations.NotNull;

/**
 * An interface for an external repository.
 *
 * @author Trond Norbye
 */
public abstract class Repository extends RepositoryInfo {

    private static final long serialVersionUID = -203179700904894217L;

    private static final Logger LOGGER = LoggerFactory.getLogger(Repository.class);

    /**
     * format used for printing the date in {@code currentVersion}.
     * <p>
     * NOTE: SimpleDateFormat is not thread-safe, lock must be held when formatting
     */
    protected static final SimpleDateFormat OUTPUT_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm Z");

    /**
     * The command with which to access the external repository. Can be
     * {@code null} if the repository isn't accessed via a CLI, or if it hasn't
     * been initialized by {@link #ensureCommand} yet.
     */
    protected String RepoCommand;

    protected final List<String> ignoredFiles;

    protected final List<String> ignoredDirs;

    /**
     * List of &lt;revision, tags&gt; pairs for repositories which display tags
     * only for files changed by the tagged commit.
     */
    protected TreeSet<TagEntry> tagList = null;

    abstract boolean fileHasHistory(File file);

    /**
     * Check if the repository supports {@code getHistory()} requests for whole
     * directories at once.
     *
     * @return {@code true} if the repository can get history for directories
     */
    @NotNull
    abstract boolean hasHistoryForDirectories();

    /**
     * Get the history log for the specified file or directory.
     *
     * @param file the file to get the history for
     * @return history log for file
     * @throws HistoryException on error accessing the history
     */
    abstract History getHistory(File file) throws HistoryException;

    public Repository() {
        super();
        ignoredFiles = new ArrayList<>();
        ignoredDirs = new ArrayList<>();
    }

    /**
     * Gets the instance's repository command, primarily for testing purposes.
     * @return null if not {@link isWorking}, or otherwise a defined command
     */
    public String getRepoCommand() {
        isWorking();
        return RepoCommand;
    }

    /**
     * <p>
     * Get the history after a specified revision.
     * <p>
     * <p>The default implementation first fetches the full history and then throws
     * away the oldest revisions. This is not efficient, so subclasses should
     * override it in order to get good performance. Once every subclass has
     * implemented a more efficient method, the default implementation should be
     * removed and made abstract.
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
            LOGGER.log(Level.WARNING,
                    "Incremental history retrieval is not implemented for {0}.",
                    getClass().getSimpleName());
            LOGGER.log(Level.WARNING,
                    "Falling back to slower full history retrieval.");
        }

        History history = getHistory(file);

        if (sinceRevision == null) {
            return history;
        }

        List<HistoryEntry> partial = new ArrayList<>();
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
     * Remove the oldest changeset from a list (assuming sorted with most recent
     * changeset first) and verify that it is the changeset we expected to find
     * there.
     *
     * @param entries a list of {@code HistoryEntry} objects
     * @param revision the revision we expect the oldest entry to have
     * @throws HistoryException if the oldest entry was not the one we expected
     */
    void removeAndVerifyOldestChangeset(List<HistoryEntry> entries,
            String revision)
            throws HistoryException {
        HistoryEntry entry
                = entries.isEmpty() ? null : entries.remove(entries.size() - 1);

        // TODO We should check more thoroughly that the changeset is the one
        // we expected it to be, since some SCMs may change the revision
        // numbers so that identical revision numbers does not always mean
        // identical changesets. We could for example get the cached changeset
        // and compare more fields, like author and date.
        if (entry == null || !revision.equals(entry.getRevision())) {
            throw new HistoryException("Cached revision '" + revision
                    + "' not found in the repository "
                    + getDirectoryName());
        }
    }

    /**
     * Gets the contents of a specific version of a named file, and copies
     * into the specified target.
     *
     * @param target a required target file which will be overwritten
     * @param parent the name of the directory containing the file
     * @param basename the name of the file to get
     * @param rev the revision to get
     * @return {@code true} if contents were found
     * @throws java.io.IOException if an I/O error occurs
     */
    public boolean getHistoryGet(
            File target, String parent, String basename, String rev)
            throws IOException {
        try (FileOutputStream out = new FileOutputStream(target)) {
            return getHistoryGet(out::write, parent, basename, rev);
        }
    }

    /**
     * Gets an {@link InputStream} of the contents of a specific version of a
     * named file.
     * @param parent the name of the directory containing the file
     * @param basename the name of the file to get
     * @param rev the revision to get
     * @return a defined instance if contents were found; or else {@code null}
     */
    public InputStream getHistoryGet(
            String parent, String basename, String rev) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (getHistoryGet(out::write, parent, basename, rev)) {
            return new ByteArrayInputStream(out.toByteArray());
        }
        return null;
    }

    /**
     * Subclasses must override to get the contents of a specific version of a
     * named file, and copy to the specified {@code sink}.
     *
     * @param sink a defined instance
     * @param parent the name of the directory containing the file
     * @param basename the name of the file to get
     * @param rev the revision to get
     * @return a value indicating if the get was successful.
     */
    abstract boolean getHistoryGet(
            BufferSink sink, String parent, String basename, String rev);

    /**
     * Checks whether this parser can annotate files.
     *
     * @param file file to check
     * @return <code>true</code> if annotation is supported
     */
    abstract boolean fileHasAnnotation(File file);

    /**
     * Returns if this repository tags only files changed in last commit, i.e.
     * if we need to prepare list of repository-wide tags prior to creation of
     * file history entries.
     *
     * @return True if we need tag list creation prior to file parsing, false by
     * default.
     */
    boolean hasFileBasedTags() {
        return false;
    }

    TreeSet<TagEntry> getTagList() {
        return this.tagList;
    }

    /**
     * Assign tags to changesets they represent The complete list of tags must
     * be pre-built using {@code getTagList()}. Then this function squeeze all
     * tags to changesets which actually exist in the history of given file.
     * Must be implemented repository-specific.
     *
     * @see #getTagList
     * @param hist History object we want to assign tags to.
     */
    void assignTagsInHistory(History hist) {
        if (hist == null) {
            return;
        }

        if (this.getTagList() == null) {
            throw new IllegalStateException("getTagList() is null");
        }

        Iterator<TagEntry> it = this.getTagList().descendingIterator();
        TagEntry lastTagEntry = null;
        for (HistoryEntry ent : hist.getHistoryEntries()) {
            // Assign all tags created since the last revision
            // Revision in this HistoryEntry must be already specified !
            // TODO: is there better way to do this? We need to "repeat"
            //   last element returned by call to next()
            while (lastTagEntry != null || it.hasNext()) {
                if (lastTagEntry == null) {
                    lastTagEntry = it.next();
                }
                if (lastTagEntry.compareTo(ent) >= 0) {
                    if (ent.getTags() == null) {
                        ent.setTags(lastTagEntry.getTags());
                    } else {
                        ent.setTags(ent.getTags() + TAGS_SEPARATOR + lastTagEntry.getTags());
                    }
                } else {
                    break;
                }
                if (it.hasNext()) {
                    lastTagEntry = it.next();
                } else {
                    lastTagEntry = null;
                }
            }
        }
    }

    /**
     * Create internal list of all tags in this repository.
     *
     * @param directory directory of the repository
     * @param cmdType command timeout type
     */
    protected void buildTagList(File directory, CommandTimeoutType cmdType) {
        this.tagList = null;
    }
    
    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param revision revision of the file. Either {@code null} or a non-empty
     * string.
     * @return an <code>Annotation</code> object
     * @throws java.io.IOException if an error occurs
     */
    abstract Annotation annotate(File file, String revision) throws IOException;

    /**
     * Return revision for annotate view.
     *
     * @param history_revision full revision
     * @return revision string suitable for matching into annotation
     */
    protected String getRevisionForAnnotate(String history_revision) {
        return history_revision;
    }

    /**
     * Create a history log cache for all files in this repository.
     * {@code getHistory()} is used to fetch the history for the entire
     * repository. If {@code hasHistoryForDirectories()} returns {@code false},
     * this method is a no-op.
     *
     * @param cache the cache instance in which to store the history log
     * @param sinceRevision if non-null, incrementally update the cache with all
     * revisions after the specified revision; otherwise, create the full
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
            LOGGER.log(
                    Level.INFO,
                    "Skipping creation of history cache for {0}, since retrieval "
                            + "of history for directories is not implemented for this "
                            + "repository type.", getDirectoryName());
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
            }
            // Failed to get partial history. This may have been caused
            // by changes in the revision numbers since the last update
            // (bug #14724) so we'll try to regenerate the cache from
            // scratch instead.
            LOGGER.log(Level.WARNING,
                    "Failed to get partial history. Attempting to "
                    + "recreate the history cache from scratch.", he);
            history = null;
        }

        if (sinceRevision != null && history == null) {
            // Failed to get partial history, now get full history instead.
            history = getHistory(directory);
            // Got full history successfully. Clear the history cache so that
            // we can recreate it from scratch.
            cache.clear(this);
        }

        // We need to refresh list of tags for incremental reindex.
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.isTagsEnabled() && this.hasFileBasedTags()) {
            this.buildTagList(new File(this.getDirectoryName()), CommandTimeoutType.INDEXER);
        }

        if (history != null) {
            cache.store(history, this);
        }
    }

    /**
     * Check if this it the right repository type for the given file.
     *
     * @param file File to check if this is a repository for.
     * @param cmdType command timeout type
     * @return true if this is the correct repository for this file/directory.
     */
    abstract boolean isRepositoryFor(File file, CommandTimeoutType cmdType);
    
    public final boolean isRepositoryFor(File file) {
        return isRepositoryFor(file, CommandTimeoutType.INDEXER);
    }

    /**
     * Determine parent of this repository.
     */
    abstract String determineParent(CommandTimeoutType cmdType) throws IOException;
    
    /**
     * Determine parent of this repository.
     * @return parent
     * @throws java.io.IOException I/O exception
     */
    public final String determineParent() throws IOException {
        return determineParent(CommandTimeoutType.INDEXER);
    }

    /**
     * Determine branch of this repository.
     */
    abstract String determineBranch(CommandTimeoutType cmdType) throws IOException;

    /**
     * Determine branch of this repository.
     * @return branch
     * @throws java.io.IOException I/O exception
     */
    public final String determineBranch() throws IOException {
        return determineBranch(CommandTimeoutType.INDEXER);
    }
    
    /**
     * Get list of ignored files for this repository.
     * @return list of strings
     */
    public List<String> getIgnoredFiles() {
        return ignoredFiles;
    }

    /**
     * Get list of ignored directories for this repository.
     * @return list of strings
     */
    public List<String> getIgnoredDirs() {
        return ignoredDirs;
    }

    /**
     * Determine and return the current version of the repository.
     *
     * This operation is consider "heavy" so this function should not be
     * called on every web request.
     *
     * @param cmdType command timeout type
     * @return the version
     * @throws IOException if I/O exception occurred
     */
    abstract String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException;
    
    public final String determineCurrentVersion() throws IOException {
        return determineCurrentVersion(CommandTimeoutType.INDEXER);
    }

    /**
     * Returns true if this repository supports sub repositories (a.k.a.
     * forests).
     *
     * @return true if this repository supports sub repositories
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    boolean supportsSubRepositories() {
        return false;
    }

    /**
     * Subclasses can override to get a value indicating that a repository implementation is nestable.
     * @return {@code false}
     */
    boolean isNestable() {
        return false;
    }

    private DateFormat getDateFormat() {
        return new RepositoryDateFormat();
    }

    /**
     * Format the given date according to the output format.
     *
     * @param date the date
     * @return the string representing the formatted date
     * @see #OUTPUT_DATE_FORMAT
     */
    public String format(Date date) {
        synchronized (OUTPUT_DATE_FORMAT) {
            return OUTPUT_DATE_FORMAT.format(date);
        }
    }

    /**
     * Parse the given string as a date object with the repository date formats.
     *
     * @param dateString the string representing the date
     * @return the instance of a date
     * @throws ParseException when the string can not be parsed correctly
     */
    public Date parse(String dateString) throws ParseException {
        final DateFormat format = getDateFormat();
        synchronized (format) {
            return format.parse(dateString);
        }
    }

    static Boolean checkCmd(String... args) {
        Executor exec = new Executor(args);
        return exec.exec(false) == 0;
    }

    /**
     * Set the name of the external client command that should be used to access
     * the repository wrt. the given parameters. Does nothing, if this
     * repository's <var>RepoCommand</var> has already been set (i.e. has a
     * non-{@code null} value).
     *
     * @param propertyKey property key to lookup the corresponding system
     * property.
     * @param fallbackCommand the command to use, if lookup fails.
     * @return the command to use.
     * @see #RepoCommand
     */
    protected String ensureCommand(String propertyKey, String fallbackCommand) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        
        if (RepoCommand != null) {
            return RepoCommand;
        }
        
        RepoCommand = env.getRepoCmd(this.getClass().getCanonicalName());
        if (RepoCommand == null) {
            RepoCommand = System.getProperty(propertyKey, fallbackCommand);
            env.setRepoCmd(this.getClass().getCanonicalName(), RepoCommand);
        }
        
        return RepoCommand;
    }

    protected String getRepoRelativePath(final File file)
            throws IOException {

        String filename = file.getPath();
        String repoDirName = getDirectoryName();

        String abs = file.getCanonicalPath();
        if (abs.startsWith(repoDirName)) {
            if (abs.length() > repoDirName.length()) {
                filename = abs.substring(repoDirName.length() + 1);
            }
        } else {
            abs = file.getAbsolutePath();
            if (abs.startsWith(repoDirName) && abs.length() >
                repoDirName.length()) {
                filename = abs.substring(repoDirName.length() + 1);
            }
        }
        return filename;
    }

    /**
     * Copies all bytes from {@code in} to the {@code sink}.
     * @return the number of writes to {@code sink}
     */
    static int copyBytes(BufferSink sink, InputStream in) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int iterations = 0;
        int len;
        while ((len = in.read(buffer)) != -1) {
            if (len > 0) {
                ++iterations;
                sink.write(buffer, 0, len);
            }
        }
        return iterations;
    }

    static class HistoryRevResult {
        boolean success;
        int iterations;
    }

    private class RepositoryDateFormat extends DateFormat {
        private static final long serialVersionUID = -6951382723884436414L;

        private final Locale locale = Locale.ENGLISH;
        // NOTE: SimpleDateFormat is not thread-safe, lock must be held when used
        private final SimpleDateFormat[] formatters = new SimpleDateFormat[datePatterns.length];

        {
            // initialize date formatters
            for (int i = 0; i < datePatterns.length; i++) {
                formatters[i] = new SimpleDateFormat(datePatterns[i], locale);
                /*
                 * TODO: the following would be nice - but currently it
                 * could break the compatibility with some repository dates
                 */
                // formatters[i].setLenient(false);
            }
        }

        @Override
        public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public Date parse(String source) throws ParseException {
            ParseException head = null, tail = null;
            for (SimpleDateFormat formatter : formatters) {
                try {
                    return formatter.parse(source);
                } catch (ParseException ex1) {
                    /*
                     * Adding all exceptions together to get some info in
                     * the logs.
                     */
                    ex1 = new ParseException(
                            String.format("%s with format \"%s\" and locale \"%s\"",
                                    ex1.getMessage(),
                                    formatter.toPattern(),
                                    locale),
                            ex1.getErrorOffset()
                    );
                    if (head == null) {
                        head = tail = ex1;
                    } else {
                        tail.initCause(ex1);
                        tail = ex1;
                    }
                }
            }
            throw head != null ? head : new ParseException(String.format("Unparseable date: \"%s\"", source), 0);
        }

        @Override
        public Date parse(String source, ParsePosition pos) {
            throw new UnsupportedOperationException("not implemented");
        }
    }
}
