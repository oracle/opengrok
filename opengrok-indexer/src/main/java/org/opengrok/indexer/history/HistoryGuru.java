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
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import org.opengrok.indexer.search.DirectoryEntry;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.PathUtils;
import org.opengrok.indexer.util.Progress;
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
            } catch (CacheException he) {
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
     */
    public String getHistoryCacheInfo() {
        return historyCache == null ? "No history cache" : historyCache.getInfo();
    }

    /**
     * Get a string with information about the annotation cache.
     *
     * @return a free form text string describing the history cache instance
     */
    public String getAnnotationCacheInfo() {
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
            } catch (CacheException e) {
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
     * @param file the file to annotate
     * @param rev the revision to annotate (<code>null</code> means current revision)
     * @return file annotation, or <code>null</code>
     * @throws IOException on error
     */
    @Nullable
    public Annotation annotate(File file, @Nullable String rev) throws IOException {
        return annotate(file, rev, true);
    }

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param rev the revision to annotate (<code>null</code> means current revision)
     * @param fallback whether to fall back to repository method
     * @return file annotation, or <code>null</code>
     * @throws IOException if I/O exception occurs
     */
    @Nullable
    public Annotation annotate(File file, @Nullable String rev, boolean fallback) throws IOException {
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

        Statistics statistics = new Statistics();
        completeAnnotationWithHistory(file, annotation, repo);
        statistics.report(LOGGER, Level.FINEST, String.format("completed annotation with history for '%s'", file));

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
                    annotation.addDesc(short_rev, he.getDescription());
                    // History entries are coming from recent to older,
                    // file version should be from oldest to newer.
                    annotation.addFileVersion(short_rev, revs.size() - revsMatched);
                    revsMatched++;
                }
            }
        }
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
            throws CacheException {

        if (useHistoryCache() && historyCache.supportsRepository(repository)) {
            return historyCache.get(file, repository, withFiles);
        }

        return null;
    }

    @Nullable
    private HistoryEntry getLastHistoryEntryFromCache(File file, Repository repository) throws CacheException {

        if (useHistoryCache() && historyCache.supportsRepository(repository)) {
            return historyCache.getLastHistoryEntry(file);
        }

        return null;
    }

    /**
     * Get last {@link HistoryEntry} for a file. First, try to retrieve it from the cache.
     * If that fails, fallback to the repository method.
     * @param file file to get the history entry for
     * @param ui is the request coming from the UI
     * @param fallback whether to perform fallback to repository method if cache retrieval fails
     * @return last (newest) history entry for given file or {@code null}
     * @throws HistoryException if history retrieval failed
     */
    @Nullable
    public HistoryEntry getLastHistoryEntry(File file, boolean ui, boolean fallback) throws HistoryException {
        Statistics statistics = new Statistics();
        LOGGER.log(Level.FINEST, "started retrieval of last history entry for ''{0}''", file);
        final File dir = file.isDirectory() ? file : file.getParentFile();
        final Repository repository = getRepository(dir);

        try {
            HistoryEntry lastHistoryEntry = getLastHistoryEntryFromCache(file, repository);
            if (lastHistoryEntry != null) {
                statistics.report(LOGGER, Level.FINEST,
                        String.format("got latest history entry %s for ''%s'' from history cache",
                        lastHistoryEntry, file), "history.entry.latest");
                return lastHistoryEntry;
            }
        } catch (CacheException e) {
            LOGGER.log(Level.FINER,
                    String.format("failed to retrieve last history entry for ''%s'' in %s using history cache",
                            file, repository),
                            e.getMessage());
        }

        if (!fallback) {
            statistics.report(LOGGER, Level.FINEST,
                    String.format("cannot retrieve the last history entry for ''%s'' in %s because fallback to" +
                                    "repository method is disabled",
                            file, repository), "history.entry.latest");
            return null;
        }

        if (!isRepoHistoryEligible(repository, file, ui)) {
            statistics.report(LOGGER, Level.FINEST,
                    String.format("cannot retrieve the last history entry for ''%s'' in %s because of settings",
                    file, repository), "history.entry.latest");
            return null;
        }

        // Fallback to the repository method.
        HistoryEntry lastHistoryEntry = repository.getLastHistoryEntry(file, ui);
        statistics.report(LOGGER, Level.FINEST,
                String.format("finished retrieval of last history entry for '%s' using repository method (%s)",
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

        if (repository == null) {
            LOGGER.log(Level.WARNING, "no repository found for ''{0}''", file);
            return null;
        }

        History history;
        try {
            history = getHistoryFromCache(file, repository, withFiles);
            if (history != null) {
                return history;
            }

            if (fallback) {
                return getHistoryFromRepository(file, repository, ui);
            }
        } catch (CacheException e) {
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
                repository.isHistoryEnabled() && repository.hasHistoryForDirectories() &&
                !env.isFetchHistoryWhenNotInCache()) {
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
            } catch (CacheException e) {
                LOGGER.log(Level.FINEST, "cannot determine if history cache for ''{0}'' is fresh", file);
            }
        }

        Repository repo = getRepository(file);
        if (repo == null) {
            LOGGER.log(Level.FINEST, "cannot find repository for ''{0}}'' to check history presence", file);
            return false;
        }

        if (!repositoryHasHistory(file, repo)) {
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

    private static boolean repositoryHasHistory(File file, Repository repo) {
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

        return true;
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
        } catch (CacheException ex) {
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
        } catch (CacheException ex) {
            LOGGER.log(Level.FINE,
                    String.format("failed to get annotation cache for file '%s' to check history cache presence", file),
                    ex);
            return false;
        }
    }

    /**
     * Get the last modified times and descriptions for all files and subdirectories in the specified directory
     * and set it into the entries provided.
     * @param directory the directory whose files to check
     * @param entries list of {@link DirectoryEntry} instances
     * @return whether to fall back to file system based time stamps if the date is {@code null}
     * @throws org.opengrok.indexer.history.CacheException if history cannot be retrieved
     */
    public boolean fillLastHistoryEntries(File directory, List<DirectoryEntry> entries) throws CacheException {

        if (!env.isUseHistoryCacheForDirectoryListing()) {
            LOGGER.log(Level.FINEST, "using history cache to retrieve last modified times for ''{0}}'' is disabled",
                    directory);
            return true;
        }

        if (!useHistoryCache()) {
            LOGGER.log(Level.FINEST, "history cache is disabled for ''{0}'' to retrieve last modified times",
                    directory);
            return true;
        }

        Repository repository = getRepository(directory);
        if (repository == null) {
            LOGGER.log(Level.FINEST, "cannot find repository for ''{0}}'' to retrieve last modified times",
                    directory);
            return true;
        }

        // Do not use history cache for repositories with merge commits disabled as some files in the repository
        // could be introduced and changed solely via merge changesets. The call would presumably fall back
        // to file system based time stamps, however that might be confusing, so avoid that.
        if (repository.isMergeCommitsSupported() && !repository.isMergeCommitsEnabled()) {
            LOGGER.log(Level.FINEST,
                    "will not retrieve last modified times due to merge changesets disabled for ''{0}}''",
                    directory);
            return true;
        }

        return !historyCache.fillLastHistoryEntries(entries);
    }

    /**
     * Recursively search for repositories with a depth limit, add those found to the internally used map.
     * @see #putRepository(Repository)
     *
     * @param files list of directories to check if they contain a repository
     * @param allowedNesting number of levels of nested repos to allow
     * @param depth maximum scanning depth
     * @param isNested a value indicating if a parent {@link Repository} was already found above the {@code files}
     * @param progress {@link org.opengrok.indexer.util.Progress} instance
     * @return collection of added repositories
     */
    private Collection<RepositoryInfo> addRepositories(File[] files, int allowedNesting, int depth, boolean isNested,
                                                       Progress progress) {

        if (depth < 0) {
            throw new IllegalArgumentException("depth is negative");
        }

        List<RepositoryInfo> repoList = new ArrayList<>();
        PathAccepter pathAccepter = env.getPathAccepter();

        for (File file : files) {
            if (!file.isDirectory()) {
                continue;
            }

            try {
                Repository repository = null;
                try {
                    repository = RepositoryFactory.getRepository(file, CommandTimeoutType.INDEXER, isNested);
                } catch (InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                    LOGGER.log(Level.WARNING,
                            String.format("Could not create repository for '%s': could not instantiate the repository.",
                                    file), e);
                } catch (IllegalAccessException iae) {
                    LOGGER.log(Level.WARNING,
                            String.format("Could not create repository for '%s': missing access rights.", file), iae);
                    continue;
                } catch (ForbiddenSymlinkException e) {
                    LOGGER.log(Level.WARNING, "Could not create repository for ''{0}'': {1}",
                            new Object[] {file, e.getMessage()});
                    continue;
                }

                if (repository == null) {
                    if (depth == 0) {
                        // Reached maximum depth, skip looking through the children.
                        continue;
                    }

                    // Not a repository, search its sub-dirs.
                    if (pathAccepter.accept(file)) {
                        File[] subFiles = file.listFiles();
                        if (subFiles == null) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to get sub directories for ''{0}'', check access permissions.",
                                    file.getAbsolutePath());
                        } else {
                            // Recursive call to scan next depth
                            repoList.addAll(addRepositories(subFiles,
                                    allowedNesting, depth - 1, isNested, progress));
                        }
                    }
                } else {
                    LOGGER.log(Level.CONFIG, "Adding repository {0}", repository);

                    repoList.add(new RepositoryInfo(repository));
                    putRepository(repository);

                    if (allowedNesting > 0 && repository.supportsSubRepositories()) {
                        File[] subFiles = file.listFiles();
                        if (subFiles == null) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to get sub directories for ''{0}'', check access permissions.",
                                    file.getAbsolutePath());
                        } else if (depth > 0) {
                            repoList.addAll(addRepositories(subFiles,
                                    allowedNesting - 1, depth - 1, true, progress));
                        }
                    }
                }
            } catch (IOException exp) {
                LOGGER.log(Level.WARNING,
                        "Failed to get canonical path for ''{0}'': {1}",
                        new Object[]{file.getAbsolutePath(), exp.getMessage()});
                LOGGER.log(Level.WARNING, "Repository will be ignored...", exp);
            } finally {
                progress.increment();
            }
        }

        return repoList;
    }

    /**
     * Recursively search for repositories in given directories, add those found to the internally used map.
     *
     * @param files list of directories to check if they contain a repository
     * @return collection of added repositories
     */
    public Collection<RepositoryInfo> addRepositories(File[] files) {
        ExecutorService executor = env.getIndexerParallelizer().getFixedExecutor();
        List<Future<Collection<RepositoryInfo>>> futures = new ArrayList<>();
        List<RepositoryInfo> repoList = new ArrayList<>();

        try (Progress progress = new Progress(LOGGER, "directories processed for repository scan")) {
            for (File file : files) {
                /*
                 * Adjust scan depth based on source root path. Some directories can be symbolic links pointing
                 * outside source root so avoid constructing canonical paths for the computation to work.
                 */
                int levelsBelowSourceRoot;
                try {
                    String relativePath = env.getPathRelativeToSourceRoot(file);
                    levelsBelowSourceRoot = Path.of(relativePath).getNameCount();
                } catch (IOException | ForbiddenSymlinkException e) {
                    LOGGER.log(Level.WARNING, "cannot get path relative to source root for ''{0}'', " +
                            "skipping repository scan for this directory", file);
                    continue;
                }
                final int scanDepth = env.getScanningDepth() - levelsBelowSourceRoot;

                futures.add(executor.submit(() -> addRepositories(new File[]{file},
                        env.getNestingMaximum(), scanDepth, false, progress)));
            }

            futures.forEach(future -> {
                try {
                    repoList.addAll(future.get());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "failed to get results of repository scan", e);
                }
            });
        }

        LOGGER.log(Level.FINER, "Discovered repositories: {0}", repoList);

        return repoList;
    }

    /**
     * Recursively search for repositories in given directories, add those found to the internally used map.
     *
     * @param repos collection of repository paths
     * @return collection of {@link RepositoryInfo} objects
     */
    public Collection<RepositoryInfo> addRepositories(Collection<String> repos) {
        return addRepositories(repos.stream().map(File::new).toArray(File[]::new));
    }

    /**
     * Get collection of repositories used internally by HistoryGuru.
     * @return collection of {@link RepositoryInfo} objects
     */
    public Collection<RepositoryInfo> getRepositories() {
        return repositories.values().stream().map(RepositoryInfo::new).collect(Collectors.toSet());
    }

    /**
     * Store history for a file into history cache. If the related repository does not support
     * getting the history for directories, it will return right away without storing the history.
     * @param file file
     * @param history {@link History} instance
     */
    public void storeHistory(File file, History history) {
        Repository repository = getRepository(file);
        if (repository.hasHistoryForDirectories()) {
            return;
        }

        try {
            historyCache.storeFile(history, file, repository);
        } catch (HistoryException e) {
            LOGGER.log(Level.WARNING,
                    String.format("cannot create history cache for '%s' in repository %s", file, repository), e);
        }
    }

    private void createHistoryCache(Repository repository, String sinceRevision) throws CacheException, HistoryException {
        if (!repository.isHistoryEnabled()) {
            LOGGER.log(Level.INFO,
                    "Skipping history cache creation for {0} and its subdirectories", repository);
            return;
        }

        if (repository.isWorking()) {
            Statistics elapsed = new Statistics();

            LOGGER.log(Level.INFO, "Creating history cache for {0}", repository);
            repository.createCache(historyCache, sinceRevision);
            elapsed.report(LOGGER, String.format("Done history cache for %s", repository));
        } else {
            LOGGER.log(Level.WARNING,
                    "Skipping creation of history cache for {0}: Missing SCM dependencies?", repository);
        }
    }

    private Map<Repository, Optional<Exception>> createHistoryCacheReal(Collection<Repository> repositories) {
        if (repositories.isEmpty()) {
            LOGGER.log(Level.WARNING, "History cache is enabled however the list of repositories is empty. " +
                    "Either specify the repositories in configuration or let the indexer scan them.");
            return Collections.emptyMap();
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
            } catch (CacheException he) {
                LOGGER.log(Level.WARNING, String.format("Failed to retrieve latest cached revision for %s", repo), he);
            }
        }

        LOGGER.log(Level.INFO, "Creating history cache for {0} repositories", repos2process.size());
        Map<Repository, Future<Optional<Exception>>> futures = new HashMap<>();
        try (Progress progress = new Progress(LOGGER, "repository invalidation", repos2process.size())) {
            for (final Map.Entry<Repository, String> entry : repos2process.entrySet()) {
                futures.put(entry.getKey(), executor.submit(() -> {
                    try {
                        createHistoryCache(entry.getKey(), entry.getValue());
                    } catch (Exception ex) {
                        // We want to catch any exception since we are in thread.
                        LOGGER.log(Level.WARNING, "createHistoryCacheReal() got exception", ex);
                        return Optional.of(ex);
                    } finally {
                        progress.increment();
                    }
                    return Optional.empty();
                }));
            }
        }

        /*
         * Wait until the history of all repositories is done. This is necessary
         * since the next phase of generating index will need the history to
         * be ready as it is recorded in Lucene index.
         */
        Map<Repository, Optional<Exception>> results = new HashMap<>();
        for (Map.Entry<Repository, Future<Optional<Exception>>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (InterruptedException | ExecutionException ex) {
                results.put(entry.getKey(), Optional.of(ex));
            }
        }

        // The cache has been populated. Now, optimize how it is stored on
        // disk to enhance performance and save space.
        try {
            historyCache.optimize();
        } catch (CacheException he) {
            LOGGER.log(Level.WARNING, "Failed optimizing the history cache database", he);
        }
        elapsed.report(LOGGER, "Done history cache for all repositories", "indexer.history.cache");
        setHistoryIndexDone();

        return results;
    }

    /**
     * Create history cache for selected repositories.
     * For this to work the repositories have to be already present in the
     * internal map, e.g. via {@code setRepositories()} or {@code addRepositories()}.
     *
     * @param repositories list of repository paths
     * @return map of repository to optional exception
     */
    public Map<Repository, Optional<Exception>> createHistoryCache(Collection<String> repositories) {
        if (!useHistoryCache()) {
            return null;
        }
        return createHistoryCacheReal(getReposFromString(repositories));
    }

    /**
     * Clear entry for single file from history cache.
     * @param path path to the file relative to the source root
     * @param removeHistory whether to remove history cache entry for the path
     */
    public void clearHistoryCacheFile(String path, boolean removeHistory) {
        if (!useHistoryCache()) {
            return;
        }

        Repository repository = getRepository(new File(env.getSourceRootFile(), path));
        if (repository == null) {
            return;
        }

        // Repositories that do not support getting history for directories do not undergo
        // incremental history cache generation, so for these the removeHistory parameter is not honored.
        if (!repository.hasHistoryForDirectories() || removeHistory) {
            historyCache.clearFile(path);
        }
    }

    /**
     * Retrieve and store the annotation cache entry for given file.
     * @param file file object under source root. Needs to have a repository associated for the cache to be created.
     * @param latestRev latest revision of the file
     * @throws CacheException on error, otherwise the cache entry is created
     */
    public void createAnnotationCache(File file, String latestRev) throws CacheException {
        if (!useAnnotationCache()) {
            throw new CacheException(String.format("annotation cache could not be used to create cache for '%s'",
                    file), Level.FINE);
        }

        Repository repository = getRepository(file);
        if (repository == null) {
            throw new CacheException(String.format("no repository for '%s'", file), Level.FINE);
        }

        if (!repository.isWorking() || !repository.isAnnotationCacheEnabled()) {
            throw new CacheException(
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
            throw new CacheException(e);
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
     * @return map of repository to optional exception
     */
    public Map<Repository, Optional<Exception>> createHistoryCache() {
        if (!useHistoryCache()) {
            return Collections.emptyMap();
        }

        return createHistoryCacheReal(repositories.values());
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
     * @return repository object or {@code null} if not found
     */
    @Nullable
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

        try (Progress progress = new Progress(LOGGER, "repository invalidation", repos.size())) {
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
                        progress.increment();
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
        }

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
