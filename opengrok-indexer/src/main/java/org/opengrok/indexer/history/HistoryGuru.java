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
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Configuration.RemoteSCM;
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
     * Map of repositories, with {@code DirectoryName} as key.
     */
    private final Map<String, Repository> repositories = new ConcurrentHashMap<>();

    /**
     * Set of repository roots (using ConcurrentHashMap but a throwaway value)
     * with parent of {@code DirectoryName} as key.
     */
    private final Map<String, String> repositoryRoots = new ConcurrentHashMap<>();

    /**
     * Creates a new instance of HistoryGuru, and try to set the default source
     * control system.
     */
    private HistoryGuru() {
        env = RuntimeEnvironment.getInstance();

        HistoryCache cache = null;
        if (env.useHistoryCache()) {
            cache = new FileHistoryCache();

            try {
                cache.initialize();
            } catch (HistoryException he) {
                LOGGER.log(Level.WARNING,
                        "Failed to initialize the history cache", he);
                // Failed to initialize, run without a history cache
                cache = null;
            }
        }
        historyCache = cache;
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
     * Return whether or not a cache should be used for the history log.
     *
     * @return {@code true} if the history cache has been enabled and
     * initialized, {@code false} otherwise
     */
    private boolean useCache() {
        return historyCache != null;
    }

    /**
     * Get a string with information about the history cache.
     *
     * @return a free form text string describing the history cache instance
     * @throws HistoryException if an error occurred while getting the info
     */
    public String getCacheInfo() throws HistoryException {
        return historyCache == null ? "No cache" : historyCache.getInfo();
    }

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param rev the revision to annotate (<code>null</code> means BASE)
     * @return file annotation, or <code>null</code> if the
     * <code>HistoryParser</code> does not support annotation
     * @throws IOException if I/O exception occurs
     */
    public Annotation annotate(File file, String rev) throws IOException {
        Annotation ret = null;

        Repository repo = getRepository(file);
        if (repo != null) {
            ret = repo.annotate(file, rev);
            History hist = null;
            try {
                hist = repo.getHistory(file);
            } catch (HistoryException ex) {
                LOGGER.log(Level.FINEST,
                        "Cannot get messages for tooltip: ", ex);
            }
            if (hist != null && ret != null) {
                Set<String> revs = ret.getRevisions();
                int revsMatched = 0;
             // !!! cannot do this because of not matching rev ids (keys)
                // first is the most recent one, so we need the position of "rev"
                // until the end of the list
                //if (hent.indexOf(rev)>0) {
                //     hent = hent.subList(hent.indexOf(rev), hent.size());
                //}
                for (HistoryEntry he : hist.getHistoryEntries()) {
                    String hist_rev = he.getRevision();
                    String short_rev = repo.getRevisionForAnnotate(hist_rev);
                    if (revs.contains(short_rev)) {
                        ret.addDesc(short_rev, "changeset: " + he.getRevision()
                                + "\nsummary: " + he.getMessage() + "\nuser: "
                                + he.getAuthor() + "\ndate: " + he.getDate());
                         // History entries are coming from recent to older,
                         // file version should be from oldest to newer.
                        ret.addFileVersion(short_rev, revs.size() - revsMatched);
                        revsMatched++;
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Get the appropriate history reader for given file.
     *
     * @param file The file to get the history reader for
     * @throws HistoryException If an error occurs while getting the history
     * @return A HistorReader that may be used to read out history data for a
     * named file
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
     * Get the history for the specified file.
     *
     * @param file the file to get the history for
     * @param withFiles whether or not the returned history should contain a
     * list of files touched by each changeset (the file list may be skipped if
     * false, but it doesn't have to)
     * @param ui called from the webapp
     * @return history for the file
     * @throws HistoryException on error when accessing the history
     */
    public History getHistory(File file, boolean withFiles, boolean ui)
            throws HistoryException {
        final File dir = file.isDirectory() ? file : file.getParentFile();
        final Repository repo = getRepository(dir);

        RemoteSCM rscm = env.getRemoteScmSupported();
        boolean doRemote = (ui && (rscm == RemoteSCM.UIONLY))
                || (rscm == RemoteSCM.ON)
                || (ui || ((rscm == RemoteSCM.DIRBASED) && (repo != null) && repo.hasHistoryForDirectories()));

        if (repo != null && repo.isHistoryEnabled() && repo.isWorking() && repo.fileHasHistory(file)
                && (!repo.isRemote() || doRemote)) {

            if (useCache() && historyCache.supportsRepository(repo)) {
                try {
                    return historyCache.get(file, repo, withFiles);
                } catch (ForbiddenSymlinkException ex) {
                    LOGGER.log(Level.FINER, ex.getMessage());
                    return null;
                }
            }
            return repo.getHistory(file);
        }

        return null;
    }

    /**
     * Gets a named revision of the specified file into the specified target.
     *
     * @param target a require target file
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @return {@code true} if content was found
     * @throws java.io.IOException if an I/O error occurs
     */
    public boolean getRevision(File target, String parent, String basename,
            String rev) throws IOException {

        Repository repo = getRepository(new File(parent));
        return repo != null && repo.getHistoryGet(target, parent,
                basename, rev);
    }

    /**
     * Get a named revision of the specified file.
     *
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @return An InputStream containing the named revision of the file.
     */
    public InputStream getRevision(String parent, String basename, String rev) {
        InputStream ret = null;

        Repository repo = getRepository(new File(parent));
        if (repo != null) {
            ret = repo.getHistoryGet(parent, basename, rev);
        }
        return ret;
    }

    /**
     * Does this directory contain files with source control information?
     *
     * @param file The name of the directory
     * @return true if the files in this directory have associated revision
     * history
     */
    public boolean hasHistory(File file) {
        Repository repo = getRepository(file);

        if (repo == null) {
            return false;
        }

        // This should return true for Annotate view.
        return repo.isWorking() && repo.fileHasHistory(file)
                && ((env.getRemoteScmSupported() == RemoteSCM.ON)
                || (env.getRemoteScmSupported() == RemoteSCM.UIONLY)
                || (env.getRemoteScmSupported() == RemoteSCM.DIRBASED)
                || !repo.isRemote());
    }

    /**
     * Does the history cache contain entry for this directory ?
     * @param file file object
     * @return true if there is cache, false otherwise
     */
    public boolean hasCacheForFile(File file) {
        if (!useCache()) {
            return false;
        }

        try {
            return historyCache.hasCacheForFile(file);
        } catch (HistoryException ex) {
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
        if (!file.isDirectory()) {
            Repository repo = getRepository(file);
            if (repo != null && repo.isWorking()) {
                return repo.fileHasAnnotation(file);
            }
        }

        return false;
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
    public Map<String, Date> getLastModifiedTimes(File directory)
            throws HistoryException {

        Repository repository = getRepository(directory);

        if (repository != null && useCache()) {
            return historyCache.getLastModifiedTimes(directory, repository);
        }

        return Collections.emptyMap();
    }

    /**
     * recursively search for repositories with a depth limit, add those found
     * to the internally used map.
     *
     * @param files list of files to check if they contain a repository
     * @param allowedNesting number of levels of nested repos to allow
     * @param depth current depth - using global scanningDepth - one can limit
     * this to improve scanning performance
     * @param isNested a value indicating if a parent {@link Repository} was
     * already found above the {@code files}
     * @return collection of added repositories
     */
    private Collection<RepositoryInfo> addRepositories(File[] files,
            int allowedNesting, int depth, boolean isNested) {

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
                    // Not a repository, search its sub-dirs.
                    if (pathAccepter.accept(file)) {
                        File[] subFiles = file.listFiles();
                        if (subFiles == null) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to get sub directories for ''{0}'', " +
                                    "check access permissions.",
                                    file.getAbsolutePath());
                        } else if (depth <= env.getScanningDepth()) {
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
                LOGGER.log(Level.WARNING, "failed to get results of repository scan");
            }
        });

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

        return addRepositories(repos.stream().
                map(r -> new File(r)).
                collect(Collectors.toList()).toArray(new File[0]));
    }

    /**
     * Get collection of repositories used internally by HistoryGuru.
     * @return collection of repositories
     */
    public Collection<RepositoryInfo> getRepositories() {
        return repositories.values().stream().
                map(ri -> new RepositoryInfo(ri)).collect(Collectors.toSet());
    }

    private void createCache(Repository repository, String sinceRevision) {
        String path = repository.getDirectoryName();
        String type = repository.getClass().getSimpleName();

        if (!repository.isHistoryEnabled()) {
            LOGGER.log(Level.INFO,
                    "Skipping history cache creation of {0} repository in {1} and its subdirectories",
                    new Object[]{type, path});
            return;
        }
        
        if (repository.isWorking()) {
            Statistics elapsed = new Statistics();

            LOGGER.log(Level.INFO, "Creating historycache for {0} ({1}) {2} renamed file handling",
                    new Object[]{path, type, repository.isHandleRenamedFiles() ? "with" : "without"});

            try {
                repository.createCache(historyCache, sinceRevision);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "An error occurred while creating cache for " + path + " ("
                        + type + ")", e);
            }

            elapsed.report(LOGGER, "Done historycache for " + path);
        } else {
            LOGGER.log(Level.WARNING,
                    "Skipping creation of historycache of {0} repository in {1}: Missing SCM dependencies?",
                    new Object[]{type, path});
        }
    }

    private void createCacheReal(Collection<Repository> repositories) {
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

        LOGGER.log(Level.INFO, "Creating historycache for {0} repositories",
                repos2process.size());
        final CountDownLatch latch = new CountDownLatch(repos2process.size());
        for (final Map.Entry<Repository, String> entry : repos2process.entrySet()) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        createCache(entry.getKey(), entry.getValue());
                    } catch (Exception ex) {
                        // We want to catch any exception since we are in thread.
                        LOGGER.log(Level.WARNING, "createCacheReal() got exception", ex);
                    } finally {
                        latch.countDown();
                    }
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
        historyCache.setHistoryIndexDone();
    }

    /**
     * Create history cache for selected repositories.
     * For this to work the repositories have to be already present in the
     * internal map, e.g. via {@code setRepositories()} or {@code addRepositories()}.
     *
     * @param repositories list of repository paths
     */
    public void createCache(Collection<String> repositories) {
        if (!useCache()) {
            return;
        }
        createCacheReal(getReposFromString(repositories));
    }

    /**
     * Remove history data for a list of repositories.
     * Note that this just deals with the data, the map used by HistoryGuru
     * will be left intact.
     *
     * @param repositories list of repository paths relative to source root
     * @return list of repository paths that were found and their history data removed
     */
    public List<String> clearCache(Collection<String> repositories) {
        List<String> clearedRepos = new ArrayList<>();

        if (!useCache()) {
            return clearedRepos;
        }

        for (Repository r : getReposFromString(repositories)) {
            try {
                historyCache.clear(r);
                clearedRepos.add(r.getDirectoryName());
                LOGGER.log(Level.INFO,
                        "History cache for {0} cleared.", r.getDirectoryName());
            } catch (HistoryException e) {
                LOGGER.log(Level.WARNING,
                        "Clearing history cache for repository {0} failed: {1}",
                        new Object[]{r.getDirectoryName(), e.getLocalizedMessage()});
            }
        }

        return clearedRepos;
    }

    /**
     * Clear entry for single file from history cache.
     * @param path path to the file relative to the source root
     */
    public void clearCacheFile(String path) {
        if (!useCache()) {
            return;
        }

        historyCache.clearFile(path);
    }

    /**
     * Remove history data for a list of repositories. Those that are
     * successfully cleared are removed from the internal list of repositories.
     *
     * @param repositories list of repository paths relative to source root
     */
    public void removeCache(Collection<String> repositories) {
        if (!useCache()) {
            return;
        }

        List<String> repos = clearCache(repositories);
        removeRepositories(repos);
    }

    /**
     * Create the history cache for all of the repositories.
     */
    public void createCache() {
        if (!useCache()) {
            return;
        }

        createCacheReal(repositories.values());
    }

    /**
     * Lookup repositories from list of repository paths.
     * @param repositories paths to repositories relative to source root
     * @return list of repositories
     */
    private List<Repository> getReposFromString(Collection<String> repositories) {
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

    protected Repository getRepository(File path) {
        File file = path;
        Set<String> rootKeys = repositoryRoots.keySet();

        while (file != null) {
            String nextPath = file.getPath();
            for (String rootKey : rootKeys) {
                String rel;
                try {
                    rel = PathUtils.getRelativeToCanonical(nextPath, rootKey);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                        "Failed to get relative to canonical for " + nextPath,
                        e);
                    return null;
                }
                Repository repo;
                if (rel.equals(nextPath)) {
                    repo = repositories.get(nextPath);
                } else {
                    String inRootPath = Paths.get(rootKey, rel).toString();
                    repo = repositories.get(inRootPath);
                }
                if (repo != null) {
                    return repo;
                }
            }

            file = file.getParentFile();
        }

        return null;
    }

    /**
     * Remove list of repositories from the list maintained in the HistoryGuru.
     * This is much less heavyweight than {@code invalidateRepositories()}
     * since it just removes items from the map.
     * @param repos repository paths
     */
    public void removeRepositories(Collection<String> repos) {
        for (String repo : repos) {
            repositories.remove(repo);
        }

        // Re-map the repository roots.
        repositoryRoots.clear();
        List<Repository> ccopy = new ArrayList<>(repositories.values());
        ccopy.forEach(this::putRepository);
    }

    /**set
     * Set list of known repositories which match the list of directories.
     * @param repos list of repositories
     * @param dirs list of directories that might correspond to the repositories
     * @param cmdType command timeout type
     */
    public void invalidateRepositories(Collection<? extends RepositoryInfo> repos, List<String> dirs, CommandTimeoutType cmdType) {
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
            repositoryRoots.clear();
            repositories.clear();
            return;
        }

        Map<String, Repository> newrepos =
            Collections.synchronizedMap(new HashMap<>(repos.size()));
        Statistics elapsed = new Statistics();

        LOGGER.log(Level.FINE, "invalidating {0} repositories", repos.size());

        /*
         * getRepository() below does various checks of the repository
         * which involves executing commands and I/O so make the checks
         * run in parallel to speed up the process.
         */
        final CountDownLatch latch = new CountDownLatch(repos.size());
        final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("invalidate-repos-" + thread.getId());
                    return thread;
                }
        });

        for (RepositoryInfo rinfo : repos) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Repository r = RepositoryFactory.getRepository(rinfo, cmdType);
                        if (r == null) {
                            LOGGER.log(Level.WARNING,
                                    "Failed to instantiate internal repository data for {0} in {1}",
                                    new Object[]{rinfo.getType(), rinfo.getDirectoryName()});
                        } else {
                            newrepos.put(r.getDirectoryName(), r);
                        }
                    } catch (Exception ex) {
                        // We want to catch any exception since we are in thread.
                        LOGGER.log(Level.WARNING, "Could not create " + rinfo.getType()
                            + " for '" + rinfo.getDirectoryName(), ex);
                    } finally {
                        latch.countDown();
                    }
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

        repositoryRoots.clear();
        repositories.clear();
        newrepos.forEach((_key, repo) -> putRepository(repo));

        elapsed.report(LOGGER, String.format("done invalidating %d repositories", newrepos.size()),
                "history.repositories.invalidate");
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
