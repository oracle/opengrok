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
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.Configuration.RemoteSCM;
import org.opengrok.indexer.configuration.OpenGrokThreadFactory;
import org.opengrok.indexer.configuration.PathAccepter;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.PathUtils;
import org.opengrok.indexer.util.Statistics;

/**
 * The HistoryGuru is used to implement an transparent layer to the various
 * source control systems.
 *
 * @author Chandan
 */
public final class HistoryGuru {

    private static final Logger LOGGER = LoggerFactory.getLogger(HistoryGuru.class);

    /**
     * The one and only instance of the HistoryGuru.
     */
    private static final HistoryGuru INSTANCE = new HistoryGuru();

    private final RuntimeEnvironment env;

    /**
     * The history cache to use.
     */
    private final HistoryCache historyCache;

    /**
     * The annotation cache to use.
     */
    private final AnnotationCache annotationCache;

    /**
     * Map of repositories, with {@code DirectoryName} as key.
     */
    private final Map<String, Repository> repositories = new ConcurrentHashMap<>();

    /**
     * Set of repository roots (using ConcurrentHashMap but a throwaway value)
     * with parent of {@code DirectoryName} as key.
     */
    private final Map<String, String> repositoryRoots = new ConcurrentHashMap<>();

    /**
     * Interface to perform repository lookup for a given file path and HistoryGuru state.
     */
    private final RepositoryLookup repositoryLookup;

    private boolean historyIndexDone = false;

    public void setHistoryIndexDone() {
        historyIndexDone = true;
    }

    public boolean isHistoryIndexDone() {
        return historyIndexDone;
    }

    /**
     * Creates a new instance of HistoryGuru. Initialize cache objects.
     */
    private HistoryGuru() {
        env = RuntimeEnvironment.getInstance();

        this.historyCache = initializeHistoryCache();
        this.annotationCache = initializeAnnotationCache();

        repositoryLookup = RepositoryLookup.cached();
    }

    /**
     * Set annotation cache to its default implementation.
     * @return {@link AnnotationCache} instance or {@code null} on error
     */
    @VisibleForTesting
    @Nullable
    static AnnotationCache initializeAnnotationCache() {
        AnnotationCache annotationCacheResult;

        // The annotation cache is initialized regardless the value of global setting
        // RuntimeEnvironment.getInstance().isAnnotationCacheEnabled() to allow for per-project/repository override.
        annotationCacheResult = new FileAnnotationCache();

        try {
            annotationCacheResult.initialize();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize the annotation cache", e);
            // Failed to initialize, run without annotation cache.
            annotationCacheResult = null;
        }

        return annotationCacheResult;
    }

    /**
     * Set history cache to its default implementation.
     * @return {@link HistoryCache} instance
     */
    private HistoryCache initializeHistoryCache() {
        HistoryCache historyCacheResult = null;
        if (env.useHistoryCache()) {
            historyCacheResult = new FileHistoryCache();

            try {
                historyCacheResult.initialize();
            } catch (HistoryException he) {
                LOGGER.log(Level.WARNING, "Failed to initialize the history cache", he);
                // Failed to initialize, run without a history cache.
                historyCacheResult = null;
            }
        }
        return historyCacheResult;
    }

    /**
     * Get the one and only instance of the HistoryGuru.
     *
     * @return the one and only HistoryGuru instance
     */
    public static HistoryGuru getInstance() {
        return INSTANCE;
    }

    /**
     * Return whether cache should be used for the history log.
     *
     * @return {@code true} if the history cache has been enabled and initialized, {@code false} otherwise
     */
    private boolean useHistoryCache() {
        return historyCache != null;
    }

    /**
     * Return whether cache should be used for annotations.
     *
     * @return {@code true} if the annotation cache has been enabled and
     * initialized, {@code false} otherwise
     */
    private boolean useAnnotationCache() {
        return annotationCache != null;
    }

    /**
     * Get a string with information about the history cache.
     *
     * @return a free form text string describing the history cache instance
     * @throws HistoryException if an error occurred while getting the info
     */
    public String getHistoryCacheInfo() throws HistoryException {
        return historyCache == null ? "No history cache" : historyCache.getInfo();
    }

    /**
     * Get a string with information about the annotation cache.
     *
     * @return a free form text string describing the history cache instance
     * @throws HistoryException if an error occurred while getting the info
     */
    public String getAnnotationCacheInfo() throws HistoryException {
        return annotationCache == null ? "No annotation cache" : annotationCache.getInfo();
    }

    /**
     * Fetch the annotation for given file from the cache or using the repository method.
     * @param file file to get the annotation for
     * @param rev revision string, specifies revision for which to get the annotation
     * @param fallback whether to fall back to repository method
     * @return {@link Annotation} instance or <code>null</code>
     * @throws IOException on error
     */
    @Nullable
    private Annotation getAnnotation(File file, @Nullable String rev, boolean fallback) throws IOException {
        Annotation annotation;

        Repository repository = getRepository(file);
        if (annotationCache != null && repository != null && repository.isAnnotationCacheEnabled()) {
            try {
                annotation = annotationCache.get(file, rev);
                if (annotation != null) {
                    return annotation;
                }
            } catch (AnnotationException e) {
                LOGGER.log(e.getLevel(), e.toString());
            }
        }

        if (!fallback) {
            LOGGER.log(Level.FINEST, "not falling back to repository to get annotation for ''{0}''", file);
            return null;
        }

        // Fall back to repository based annotation.
        // It might be possible to store the annotation to the annotation cache here, needs further thought.
        annotation = getAnnotationFromRepository(file, rev);
        if (annotation != null) {
            annotation.setRevision(LatestRevisionUtil.getLatestRevision(file));
        }

        return annotation;
    }

    /**
     * Annotate given file using repository method. Makes sure that the resulting annotation has the revision set.
     * @param file file object to generate the annotaiton for
     * @param rev revision to get the annotation for or {@code null} for latest revision of given file
     * @return annotation object
     * @throws IOException on error when getting the annotation
     */
    private Annotation getAnnotationFromRepository(File file, @Nullable String rev) throws IOException {
        if (!env.getPathAccepter().accept(file)) {
            LOGGER.log(Level.FINEST, "file ''{0}'' not accepted for annotation", file);
            return null;
        }

        Repository repository = getRepository(file);
        if (repository != null && hasAnnotation(file)) {
            return repository.annotate(file, rev);
        }

        return null;
    }

    /**
     * Wrapper for {@link #annotate(File, String, boolean)}.
     */
    @Nullable
    public Annotation annotate(File file, String rev) throws IOException {
        return annotate(file, rev, true);
    }

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param rev the revision to annotate (<code>null</code> means BASE)
     * @return file annotation, or <code>null</code> if the <code>HistoryParser</code> does not support annotation
     * @throws IOException if I/O exception occurs
     */
    @Nullable
    public Annotation annotate(File file, String rev, boolean fallback) throws IOException {
        Annotation annotation = getAnnotation(file, rev, fallback);
        if (annotation == null) {
            LOGGER.log(Level.FINEST, "no annotation for ''{0}''", file);
            return null;
        }

        Repository repo = getRepository(file);
        if (repo == null) {
            LOGGER.log(Level.FINER, "cannot get repository for file ''{0}'' to complete annotation", file);
            return null;
        }

        completeAnnotationWithHistory(file, annotation, repo);

        return annotation;
    }

    private void completeAnnotationWithHistory(File file, Annotation annotation, Repository repo) {
        History history = null;
        try {
            history = getHistory(file);
        } catch (HistoryException ex) {
            LOGGER.log(Level.WARNING, "Cannot get messages for tooltip: ", ex);
        }

        if (history != null) {
            Set<String> revs = annotation.getRevisions();
            int revsMatched = 0;
            // !!! cannot do this because of not matching rev ids (keys)
            // first is the most recent one, so we need the position of "rev"
            // until the end of the list
            //if (hent.indexOf(rev)>0) {
            //     hent = hent.subList(hent.indexOf(rev), hent.size());
            //}
            for (HistoryEntry he : history.getHistoryEntries()) {
                String hist_rev = he.getRevision();
                String short_rev = repo.getRevisionForAnnotate(hist_rev);
                if (revs.contains(short_rev)) {
                    annotation.addDesc(short_rev, "changeset: " + he.getRevision()
                            + "\nsummary: " + he.getMessage() + "\nuser: "
                            + he.getAuthor() + "\ndate: " + he.getDate());
                    // History entries are coming from recent to older,
                    // file version should be from oldest to newer.
                    annotation.addFileVersion(short_rev, revs.size() - revsMatched);
                    revsMatched++;
                }
            }
        }
    }

    /**
     * Get the appropriate history reader for given file.
     *
     * @param file The file to get the history reader for
     * @throws HistoryException If an error occurs while getting the history
     * @return A {@link HistoryReader} that may be used to read out history data for a named file
     */
    public HistoryReader getHistoryReader(File file) throws HistoryException {
        History history = getHistory(file, false);
        return history == null ? null : new HistoryReader(history);
    }

    /**
     * Get the history for the specified file.
     *
     * @param file the file to get the history for
     * @return history for the file
     * @throws HistoryException on error when accessing the history
     */
    public History getHistory(File file) throws HistoryException {
        return getHistory(file, true, false);
    }

    public History getHistory(File file, boolean withFiles) throws HistoryException {
        return getHistory(file, withFiles, false);
    }

    /**
     * Get history for the specified file (called from the web app).
     *
     * @param file the file to get the history for
     * @return history for the file
     * @throws HistoryException on error when accessing the history
     */
    public History getHistoryUI(File file) throws HistoryException {
        return getHistory(file, true, true);
    }

    /**
     * The idea is that some repositories require reaching out to remote server whenever
     * a history operation is done. Sometimes this is unwanted and this method decides that.
     * This should be consulted before the actual repository operation, i.e. not when fetching
     * history from a cache since that is inherently local operation.
     * @param repo repository
     * @param file file to decide the operation for
     * @param ui whether coming from UI
     * @return whether to perform the history operation
     */
    boolean isRepoHistoryEligible(Repository repo, File file, boolean ui) {
        RemoteSCM rscm = env.getRemoteScmSupported();
        boolean doRemote = (ui && (rscm == RemoteSCM.UIONLY))
                || (rscm == RemoteSCM.ON)
                || (ui || ((rscm == RemoteSCM.DIRBASED) && (repo != null) && repo.hasHistoryForDirectories()));

        return (repo != null && repo.isHistoryEnabled() && repo.isWorking() && repo.fileHasHistory(file)
                && (!repo.isRemote() || doRemote));
    }

    @Nullable
    private History getHistoryFromCache(File file, Repository repository, boolean withFiles)
            throws HistoryException, ForbiddenSymlinkException {

        if (useHistoryCache() && historyCache.supportsRepository(repository)) {
            return historyCache.get(file, repository, withFiles);
        }

        return null;
    }

    /**
     * Get last {@link HistoryEntry} for a file. First, try to retrieve it from the cache.
     * If that fails, fallback to the repository method.
     * @param file file to get the history entry for
     * @param ui is the request coming from the UI
     * @return last (newest) history entry for given file or {@code null}
     * @throws HistoryException if history retrieval failed
     */
    @Nullable
    public HistoryEntry getLastHistoryEntry(File file, boolean ui) throws HistoryException {
        Statistics statistics = new Statistics();
        LOGGER.log(Level.FINEST, "started retrieval of last history entry for ''{0}''", file);
        final File dir = file.isDirectory() ? file : file.getParentFile();
        final Repository repository = getRepository(dir);

        History history;
        try {
            history = getHistoryFromCache(file, repository, false);
            if (history != null) {
                HistoryEntry lastHistoryEntry = history.getLastHistoryEntry();
                if (lastHistoryEntry != null) {
                    LOGGER.log(Level.FINEST, "got latest history entry {0} for ''{1}'' from history cache",
                            new Object[]{lastHistoryEntry, file});
                    return lastHistoryEntry;
                }
            }
        } catch (ForbiddenSymlinkException e) {
            LOGGER.log(Level.FINER, e.getMessage());
            return null;
        }

        if (!isRepoHistoryEligible(repository, file, ui)) {
            LOGGER.log(Level.FINER, "cannot retrieve the last history entry for ''{0}'' in {1} because of settings",
                    new Object[]{file, repository});
            return null;
        }

        // Fallback to the repository method.
        HistoryEntry lastHistoryEntry = repository.getLastHistoryEntry(file, ui);
        if (lastHistoryEntry != null) {
            LOGGER.log(Level.FINEST, "got latest history entry {0} for ''{1}'' using repository {2}",
                    new Object[]{lastHistoryEntry, file, repository});
        }
        statistics.report(LOGGER, Level.FINEST,
                String.format("finished retrieval of last history entry for '%s' (%s)",
                        file, lastHistoryEntry != null ? "success" : "fail"), "history.entry.latest");
        return lastHistoryEntry;
    }

    public History getHistory(File file, boolean withFiles, boolean ui) throws HistoryException {
        return getHistory(file, withFiles, ui, true);
    }

    /**
     * Get the history for the specified file. The history cache is tried first, then the repository.
     *
     * @param file the file to get the history for
     * @param withFiles whether the returned history should contain a
     * list of files touched by each changeset (the file list may be skipped if false, but it doesn't have to)
     * @param ui called from the webapp
     * @param fallback fall back to fetching the history from the repository
     *                 if it cannot be retrieved from history cache
     * @return history for the file or <code>null</code>
     * @throws HistoryException on error when accessing the history
     */
    @Nullable
    public History getHistory(File file, boolean withFiles, boolean ui, boolean fallback) throws HistoryException {

        final File dir = file.isDirectory() ? file : file.getParentFile();
        final Repository repository = getRepository(dir);

        History history;
        try {
            history = getHistoryFromCache(file, repository, withFiles);
            if (history != null) {
                return history;
            }

            if (fallback) {
                return getHistoryFromRepository(file, repository, ui);
            }
        } catch (ForbiddenSymlinkException e) {
            LOGGER.log(Level.FINER, e.getMessage());
        }

        return null;
    }

    @Nullable
    private History getHistoryFromRepository(File file, Repository repository, boolean ui) throws HistoryException {

        if (!isRepoHistoryEligible(repository, file, ui)) {
            LOGGER.log(Level.FINEST, "''{0}'' in {1} is not eligible for history",
                    new Object[]{file, repository});
            return null;
        }

        /*
         * Some mirrors of repositories which are capable of fetching history
         * for directories may contain lots of files untracked by given SCM.
         * For these it would be waste of time to get their history
         * since the history of all files in this repository should have been
         * fetched in the first phase of indexing.
         */
        if (env.isIndexer() && isHistoryIndexDone() &&
                repository.isHistoryEnabled() && repository.hasHistoryForDirectories()) {
            LOGGER.log(Level.FINE, "not getting the history for ''{0}'' in repository {1} as the it supports "
                    + "history for directories",
                    new Object[]{file, repository});
            return null;
        }

        if (!env.getPathAccepter().accept(file)) {
            LOGGER.log(Level.FINEST, "file ''{0}'' not accepted for history", file);
            return null;
        }

        History history;
        try {
            history = repository.getHistory(file);
        } catch (UnsupportedOperationException e) {
            // In this case, we've found a file for which the SCM has no history
            // An example is a non-SCCS file somewhere in an SCCS-controlled workspace.
            LOGGER.log(Level.FINEST, "repository {0} does not have history for ''{1}''",
                    new Object[]{repository, file});
            return null;
        }

        return history;
    }

    /**
     * Gets a named revision of the specified file into the specified target file.
     *
     * @param target a require target file
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @return {@code true} if content was found
     * @throws java.io.IOException if an I/O error occurs
     */
    public boolean getRevision(File target, String parent, String basename, String rev) throws IOException {
        Repository repo = getRepository(new File(parent));
        return repo != null && repo.getHistoryGet(target, parent, basename, rev);
    }

    /**
     * Get a named revision of the specified file.
     *
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @return An InputStream containing the named revision of the file.
     */
    @Nullable
    public InputStream getRevision(String parent, String basename, String rev) {
        Repository repo = getRepository(new File(parent));
        if (repo == null) {
            LOGGER.log(Level.FINEST, "cannot find repository for ''{0}'' to get revision", parent);
            return null;
        }

        return repo.getHistoryGet(parent, basename, rev);
    }

    /**
     * @param file File object
     * @return whether it is possible to retrieve history for the file in any way
     */
    public boolean hasHistory(File file) {
        // If there is a cache entry that is fresh, there is no need to check the repository,
        // as the cache entry will be preferred in getHistory(), barring any time sensitive issues (TOUTOC).
        if (hasHistoryCacheForFile(file)) {
            try {
                if (historyCache.isUpToDate(file)) {
                    return true;
                }
            } catch (HistoryException | ForbiddenSymlinkException e) {
                LOGGER.log(Level.FINEST, "cannot determine if history cache for ''{0}'' is fresh", file);
            }
        }

        Repository repo = getRepository(file);
        if (repo == null) {
            LOGGER.log(Level.FINEST, "cannot find repository for ''{0}}'' to check history presence", file);
            return false;
        }

        if (!repo.isWorking()) {
            LOGGER.log(Level.FINEST, "repository {0} for ''{1}'' is not working to check history presence",
                    new Object[]{repo, file});
            return false;
        }

        if (!repo.isHistoryEnabled()) {
            LOGGER.log(Level.FINEST, "repository {0} for ''{1}'' does not have history enabled " +
                            "to check history presence", new Object[]{repo, file});
            return false;
        }

        if (!repo.fileHasHistory(file)) {
            LOGGER.log(Level.FINEST, "''{0}'' in repository {1} does not have history to check history presence",
                    new Object[]{file, repo});
            return false;
        }

        // This should return true for Annotate view.
        Configuration.RemoteSCM globalRemoteSupport = env.getRemoteScmSupported();
        boolean remoteSupported = ((globalRemoteSupport == RemoteSCM.ON)
                || (globalRemoteSupport == RemoteSCM.UIONLY)
                || (globalRemoteSupport == RemoteSCM.DIRBASED)
                || !repo.isRemote());

        if (!remoteSupported) {
            LOGGER.log(Level.FINEST, "not eligible to display history for ''{0}'' as repository {1} is remote " +
                    "and the global setting is {2}", new Object[]{file, repo, globalRemoteSupport});
        }

        return remoteSupported;
    }

    /**
     * @param file file object
     * @return if there is history cache entry for the file
     */
    public boolean hasHistoryCacheForFile(File file) {
        if (!useHistoryCache()) {
            LOGGER.log(Level.FINEST, "history cache is off for ''{0}'' to check history cache presence", file);
            return false;
        }

        try {
            return historyCache.hasCacheForFile(file);
        } catch (HistoryException ex) {
            LOGGER.log(Level.FINE,
                    String.format("failed to get history cache for file '%s' to check history cache presence", file),
                    ex);
            return false;
        }
    }

    /**
     * Check if we can annotate the specified file.
     *
     * @param file the file to check
     * @return <code>true</code> if the file is under version control and the
     * version control system supports annotation
     */
    public boolean hasAnnotation(File file) {
        if (file.isDirectory()) {
            LOGGER.log(Level.FINEST, "no annotations for directories (''{0}'') to check annotation presence",
                    file);
            return false;
        }

        Repository repo = getRepository(file);
        if (repo == null) {
            LOGGER.log(Level.FINEST, "cannot find repository for ''{0}'' to check annotation presence", file);
            return false;
        }

        if (!repo.isWorking()) {
            LOGGER.log(Level.FINEST, "repository {0} for ''{1}'' is not working to check annotation presence",
                    new Object[]{repo, file});
            return false;
        }

        return repo.fileHasAnnotation(file);
    }

    /**
     * @param file file object
     * @return if there is annotation cache entry for the file
     */
    public boolean hasAnnotationCacheForFile(File file) {
        if (!useAnnotationCache()) {
            LOGGER.log(Level.FINEST, "annotation cache is off for ''{0}'' to check history cache presence", file);
            return false;
        }

        try {
            return annotationCache.hasCacheForFile(file);
        } catch (HistoryException ex) {
            LOGGER.log(Level.FINE,
                    String.format("failed to get annotation cache for file '%s' to check history cache presence", file),
                    ex);
            return false;
        }
    }

    /**
     * Get the last modified times for all files and subdirectories in the
     * specified directory.
     *
     * @param directory the directory whose files to check
     * @return a map from file names to modification times for the files that
     * the history cache has information about
     * @throws org.opengrok.indexer.history.HistoryException if history cannot be retrieved
     */
    public Map<String, Date> getLastModifiedTimes(File directory) throws HistoryException {

        Repository repository = getRepository(directory);
        if (repository == null) {
            LOGGER.log(Level.FINEST, "cannot find repository for ''{0}}'' to retrieve last modified times",
                    directory);
            return Collections.emptyMap();
        }

        if (!useHistoryCache()) {
            LOGGER.log(Level.FINEST, "history cache is disabled for ''{0}'' to retrieve last modified times",
                    directory);
            return Collections.emptyMap();
        }

        return historyCache.getLastModifiedTimes(directory, repository);
    }

    /**
     * recursively search for repositories with a depth limit, add those found to the internally used map.
     *
     * @param files list of files to check if they contain a repository
     * @param allowedNesting number of levels of nested repos to allow
     * @param depth current depth - using global scanningDepth - one can limit this to improve scanning performance
     * @param isNested a value indicating if a parent {@link Repository} was already found above the {@code files}
     * @return collection of added repositories
     */
    private Collection<RepositoryInfo> addRepositories(File[] files, int allowedNesting, int depth, boolean isNested) {

        List<RepositoryInfo> repoList = new ArrayList<>();
        PathAccepter pathAccepter = env.getPathAccepter();

        for (File file : files) {
            if (!file.isDirectory()) {
                continue;
            }

            String path;
            try {
                path = file.getCanonicalPath();

                Repository repository = null;
                try {
                    repository = RepositoryFactory.getRepository(file, CommandTimeoutType.INDEXER, isNested);
                } catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                    LOGGER.log(Level.WARNING, "Could not create repository for '"
                            + file + "', could not instantiate the repository.", e);
                } catch (IllegalAccessException iae) {
                    LOGGER.log(Level.WARNING, "Could not create repository for '"
                            + file + "', missing access rights.", iae);
                    continue;
                } catch (ForbiddenSymlinkException e) {
                    LOGGER.log(Level.WARNING, "Could not create repository for ''{0}'': {1}",
                            new Object[] {file, e.getMessage()});
                    continue;
                }
                if (repository == null) {
                    if (depth > env.getScanningDepth()) {
                        // we reached our search max depth, skip looking through the children
                        continue;
                    }
                    // Not a repository, search its sub-dirs.
                    if (pathAccepter.accept(file)) {
                        File[] subFiles = file.listFiles();
                        if (subFiles == null) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to get sub directories for ''{0}'', " +
                                    "check access permissions.",
                                    file.getAbsolutePath());
                        } else {
                            // Recursive call to scan next depth
                            repoList.addAll(addRepositories(subFiles,
                                    allowedNesting, depth + 1, isNested));
                        }
                    }
                } else {
                    LOGGER.log(Level.CONFIG, "Adding <{0}> repository: <{1}>",
                            new Object[]{repository.getClass().getName(), path});

                    repoList.add(new RepositoryInfo(repository));
                    putRepository(repository);

                    if (allowedNesting > 0 && repository.supportsSubRepositories()) {
                        File[] subFiles = file.listFiles();
                        if (subFiles == null) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to get sub directories for ''{0}'', check access permissions.",
                                    file.getAbsolutePath());
                        } else if (depth <= env.getScanningDepth()) {
                            // Search down to a limit -- if not: too much
                            // stat'ing for huge Mercurial repositories
                            repoList.addAll(addRepositories(subFiles,
                                    allowedNesting - 1, depth + 1, true));
                        }
                    }
                }
            } catch (IOException exp) {
                LOGGER.log(Level.WARNING,
                        "Failed to get canonical path for {0}: {1}",
                        new Object[]{file.getAbsolutePath(), exp.getMessage()});
                LOGGER.log(Level.WARNING, "Repository will be ignored...", exp);
            }
        }

        return repoList;
    }

    /**
     * Recursively search for repositories in given directories, add those found
     * to the internally used repository map.
     *
     * @param files list of directories to check if they contain a repository
     * @return collection of added repositories
     */
    public Collection<RepositoryInfo> addRepositories(File[] files) {
        ExecutorService executor = env.getIndexerParallelizer().getFixedExecutor();
        List<Future<Collection<RepositoryInfo>>> futures = new ArrayList<>();
        for (File file: files) {
            futures.add(executor.submit(() -> addRepositories(new File[]{file},
                    env.getNestingMaximum(), 0, false)));
        }

        List<RepositoryInfo> repoList = new ArrayList<>();
        futures.forEach(future -> {
            try {
                repoList.addAll(future.get());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "failed to get results of repository scan", e);
            }
        });

        LOGGER.log(Level.FINER, "Discovered repositories: {0}", repoList);

        return repoList;
    }

    /**
     * Recursively search for repositories in given directories, add those found
     * to the internally used repository map.
     *
     * @param repos collection of repository paths
     * @return collection of added repositories
     */
    public Collection<RepositoryInfo> addRepositories(Collection<String> repos) {
        return addRepositories(repos.stream().map(File::new).toArray(File[]::new));
    }

    /**
     * Get collection of repositories used internally by HistoryGuru.
     * @return collection of repositories
     */
    public Collection<RepositoryInfo> getRepositories() {
        return repositories.values().stream().
                map(RepositoryInfo::new).collect(Collectors.toSet());
    }

    private void createHistoryCache(Repository repository, String sinceRevision) {
        String path = repository.getDirectoryName();
        String type = repository.getClass().getSimpleName();

        if (!repository.isHistoryEnabled()) {
            LOGGER.log(Level.INFO,
                    "Skipping history cache creation of {0} repository in ''{1}'' and its subdirectories",
                    new Object[]{type, path});
            return;
        }

        if (repository.isWorking()) {
            Statistics elapsed = new Statistics();

            LOGGER.log(Level.INFO, "Creating history cache for {0} ({1}) {2} renamed file handling",
                    new Object[]{path, type, repository.isHandleRenamedFiles() ? "with" : "without"});

            try {
                repository.createCache(historyCache, sinceRevision);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "An error occurred while creating cache for " + path + " (" + type + ")", e);
            }

            elapsed.report(LOGGER, "Done history cache for " + path);
        } else {
            LOGGER.log(Level.WARNING,
                    "Skipping creation of history cache of {0} repository in {1}: Missing SCM dependencies?",
                    new Object[]{type, path});
        }
    }

    private void createHistoryCacheReal(Collection<Repository> repositories) {
        if (repositories.isEmpty()) {
            LOGGER.log(Level.WARNING, "History cache is enabled however the list of repositories is empty. " +
                    "Either specify the repositories in configuration or let the indexer scan them.");
            return;
        }

        Statistics elapsed = new Statistics();
        ExecutorService executor = env.getIndexerParallelizer().getHistoryExecutor();
        // Since we know each repository object from the repositories
        // collection is unique, we can abuse HashMap to create a list of
        // repository,revision tuples with repository as key (as the revision
        // string does not have to be unique - surely it is not unique
        // for the initial index case).
        HashMap<Repository, String> repos2process = new HashMap<>();

        // Collect the list of <latestRev,repo> pairs first so that we
        // do not have to deal with latch decrementing in the cycle below.
        for (final Repository repo : repositories) {
            final String latestRev;

            try {
                latestRev = historyCache.getLatestCachedRevision(repo);
                repos2process.put(repo, latestRev);
            } catch (HistoryException he) {
                LOGGER.log(Level.WARNING,
                        String.format(
                                "Failed to retrieve latest cached revision for %s",
                                repo.getDirectoryName()), he);
            }
        }

        LOGGER.log(Level.INFO, "Creating history cache for {0} repositories",
                repos2process.size());
        final CountDownLatch latch = new CountDownLatch(repos2process.size());
        for (final Map.Entry<Repository, String> entry : repos2process.entrySet()) {
            executor.submit(() -> {
                try {
                    createHistoryCache(entry.getKey(), entry.getValue());
                } catch (Exception ex) {
                    // We want to catch any exception since we are in thread.
                    LOGGER.log(Level.WARNING, "createHistoryCacheReal() got exception", ex);
                } finally {
                    latch.countDown();
                }
            });
        }

        /*
         * Wait until the history of all repositories is done. This is necessary
         * since the next phase of generating index will need the history to
         * be ready as it is recorded in Lucene index.
         */
        try {
            latch.await();
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "latch exception", ex);
            return;
        }

        // The cache has been populated. Now, optimize how it is stored on
        // disk to enhance performance and save space.
        try {
            historyCache.optimize();
        } catch (HistoryException he) {
            LOGGER.log(Level.WARNING,
                    "Failed optimizing the history cache database", he);
        }
        elapsed.report(LOGGER, "Done history cache for all repositories", "indexer.history.cache");
        setHistoryIndexDone();
    }

    /**
     * Create history cache for selected repositories.
     * For this to work the repositories have to be already present in the
     * internal map, e.g. via {@code setRepositories()} or {@code addRepositories()}.
     *
     * @param repositories list of repository paths
     */
    public void createHistoryCache(Collection<String> repositories) {
        if (!useHistoryCache()) {
            return;
        }
        createHistoryCacheReal(getReposFromString(repositories));
    }

    /**
     * Clear entry for single file from history cache.
     * @param path path to the file relative to the source root
     */
    public void clearHistoryCacheFile(String path) {
        if (!useHistoryCache()) {
            return;
        }

        historyCache.clearFile(path);
    }

    /**
     * Retrieve and store the annotation cache entry for given file.
     * @param file file object under source root. Needs to have a repository associated for the cache to be created.
     * @param latestRev latest revision of the file
     * @throws AnnotationException on error, otherwise the cache entry is created
     */
    public void createAnnotationCache(File file, String latestRev) throws AnnotationException {
        if (!useAnnotationCache()) {
            throw new AnnotationException(String.format("annotation cache could not be used to create cache for '%s'",
                    file), Level.FINE);
        }

        Repository repository = getRepository(file);
        if (repository == null) {
            throw new AnnotationException(String.format("no repository for '%s'", file), Level.FINE);
        }

        if (!repository.isWorking() || !repository.isAnnotationCacheEnabled()) {
            throw new AnnotationException(
                    String.format("repository %s does not allow to create annotation cache for '%s'",
                            repository, file), Level.FINER);
        }

        LOGGER.log(Level.FINEST, "creating annotation cache for ''{0}''", file);
        try {
            Statistics statistics = new Statistics();
            Annotation annotation = getAnnotationFromRepository(file, null);
            statistics.report(LOGGER, Level.FINEST, String.format("retrieved annotation for ''%s''", file),
                    "annotation.retrieve.latency");

            if (annotation != null) {
                annotation.setRevision(latestRev);

                // Storing annotation has its own statistics.
                annotationCache.store(file, annotation);
            }
        } catch (IOException e) {
            throw new AnnotationException(e);
        }
    }

    /**
      * Clear entry for single file from annotation cache.
      * @param path path to the file relative to the source root
      */
    public void clearAnnotationCacheFile(String path) {
        if (!useAnnotationCache()) {
            return;
        }

        annotationCache.clearFile(path);
    }

    /**
     * Remove history cache data for a list of repositories. Those that are
     * successfully cleared may be removed from the internal list of repositories,
     * depending on the {@code removeRepositories} parameter.
     *
     * @param repositories list of repository objects relative to source root
     * @return list of repository names
     */
    public List<String> removeHistoryCache(Collection<RepositoryInfo> repositories) {
        if (!useHistoryCache()) {
            return List.of();
        }

        return historyCache.clearCache(repositories);
    }

    /**
     * Remove annotation cache data for a list of repositories. Those that are
     * successfully cleared may be removed from the internal list of repositories,
     * depending on the {@code removeRepositories} parameter.
     *
     * @param repositories list of repository objects relative to source root
     * @return list of repository names
     */
    public List<String> removeAnnotationCache(Collection<RepositoryInfo> repositories) {
        if (!useAnnotationCache()) {
            return List.of();
        }

        return annotationCache.clearCache(repositories);
    }

    /**
     * Create the history cache for all repositories.
     */
    public void createHistoryCache() {
        if (!useHistoryCache()) {
            return;
        }

        createHistoryCacheReal(repositories.values());
    }

    /**
     * Lookup repositories from list of repository paths.
     * @param repositories paths to repositories relative to source root
     * @return list of repositories
     */
    List<Repository> getReposFromString(Collection<String> repositories) {
        ArrayList<Repository> repos = new ArrayList<>();
        File srcRoot = env.getSourceRootFile();

        for (String file : repositories) {
            File f = new File(srcRoot, file);
            Repository r = getRepository(f);
            if (r == null) {
                LOGGER.log(Level.WARNING, "Could not locate a repository for {0}",
                        f.getAbsolutePath());
            } else if (!repos.contains(r)) {
                repos.add(r);
            }
        }

        return repos;
    }

    /**
     * Lookup repository for given file.
     * @param file file object source root
     * @return repository object
     */
    public Repository getRepository(File file) {
        return repositoryLookup.getRepository(file.toPath(), repositoryRoots.keySet(), repositories,
                PathUtils::getRelativeToCanonical);
    }

    /**
     * Remove list of repositories from the list maintained in the HistoryGuru.
     * This is much less heavyweight than {@code invalidateRepositories()}
     * since it just removes items from the map.
     * @param repos absolute repository paths
     */
    public void removeRepositories(Collection<String> repos) {
        Set<Repository> removedRepos = repos.stream().map(repositories::remove)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        repositoryLookup.repositoriesRemoved(removedRepos);
        // Re-map the repository roots.
        repositoryRoots.clear();
        List<Repository> ccopy = new ArrayList<>(repositories.values());
        ccopy.forEach(this::putRepository);
    }

    /**
     * Set list of known repositories which match the list of directories.
     * @param repos list of repositories
     * @param dirs collection of directories that might correspond to the repositories
     * @param cmdType command timeout type
     */
    public void invalidateRepositories(Collection<? extends RepositoryInfo> repos, Collection<String> dirs, CommandTimeoutType cmdType) {
        if (repos != null && !repos.isEmpty() && dirs != null && !dirs.isEmpty()) {
            List<RepositoryInfo> newrepos = new ArrayList<>();
            for (RepositoryInfo i : repos) {
                for (String dir : dirs) {
                    Path dirPath = new File(dir).toPath();
                    Path iPath = new File(i.getDirectoryName()).toPath();
                    if (iPath.startsWith(dirPath)) {
                        newrepos.add(i);
                    }
                }
            }
            repos = newrepos;
        }

        invalidateRepositories(repos, cmdType);
    }

    /**
     * Go through the list of specified repositories and determine if they
     * are valid. Those that make it through will form the new HistoryGuru
     * internal map. This means this method should be used only if dealing
     * with whole collection of repositories.
     * <br>
     * The caller is expected to reflect the new list via {@code getRepositories()}.
     * <br>
     * The processing is done via thread pool since the operation
     * is expensive (see {@code RepositoryFactory.getRepository()}).
     *
     * @param repos collection of repositories to invalidate.
     * If null or empty, the internal map of repositories will be cleared.
     * @param cmdType command timeout type
     */
    public void invalidateRepositories(Collection<? extends RepositoryInfo> repos, CommandTimeoutType cmdType) {
        if (repos == null || repos.isEmpty()) {
            clear();
            return;
        }

        Map<String, Repository> repositoryMap = Collections.synchronizedMap(new HashMap<>(repos.size()));
        Statistics elapsed = new Statistics();

        LOGGER.log(Level.FINE, "invalidating {0} repositories", repos.size());

        /*
         * getRepository() below does various checks of the repository
         * which involves executing commands and I/O so make the checks
         * run in parallel to speed up the process.
         */
        final CountDownLatch latch = new CountDownLatch(repos.size());
        int parallelismLevel;
        // Both indexer and web app startup should be as quick as possible.
        if (cmdType == CommandTimeoutType.INDEXER || cmdType == CommandTimeoutType.WEBAPP_START) {
            parallelismLevel = env.getIndexingParallelism();
        } else {
            parallelismLevel = env.getRepositoryInvalidationParallelism();
        }
        final ExecutorService executor = Executors.newFixedThreadPool(parallelismLevel,
                new OpenGrokThreadFactory("invalidate-repos-"));

        for (RepositoryInfo repositoryInfo : repos) {
            executor.submit(() -> {
                try {
                    Repository r = RepositoryFactory.getRepository(repositoryInfo, cmdType);
                    if (r == null) {
                        LOGGER.log(Level.WARNING,
                                "Failed to instantiate internal repository data for {0} in ''{1}''",
                                new Object[]{repositoryInfo.getType(), repositoryInfo.getDirectoryName()});
                    } else {
                        repositoryMap.put(r.getDirectoryName(), r);
                    }
                } catch (Exception ex) {
                    // We want to catch any exception since we are in thread.
                    LOGGER.log(Level.WARNING, "Could not create " + repositoryInfo.getType()
                        + " repository object for '" + repositoryInfo.getDirectoryName() + "'", ex);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait until all repositories are validated.
        try {
            latch.await();
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "latch exception", ex);
        }
        executor.shutdown();

        clear();
        repositoryMap.forEach((key, repo) -> putRepository(repo));

        elapsed.report(LOGGER, String.format("Done invalidating repositories (%d valid, %d working)",
                        repositoryMap.size(), repositoryMap.values().stream().
                                filter(RepositoryInfo::isWorking).collect(Collectors.toSet()).size()),
                "history.repositories.invalidate");
    }

    @VisibleForTesting
    public void clear() {
        repositoryRoots.clear();
        repositories.clear();
        repositoryLookup.clear();
    }

    /**
     * Adds the specified {@code repository} to this instance's repository map
     * and repository-root map (if not already there).
     * @param repository a defined instance
     */
    private void putRepository(Repository repository) {
        String repoDirectoryName = repository.getDirectoryName();
        File repoDirectoryFile = new File(repoDirectoryName);
        String repoDirParent = repoDirectoryFile.getParent();
        repositoryRoots.put(repoDirParent, "");
        repositories.put(repoDirectoryName, repository);
    }
}
