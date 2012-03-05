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
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * The HistoryGuru is used to implement an transparent layer to the various
 * source control systems.
 *
 * @author Chandan
 */
public final class HistoryGuru {
    private static final Logger log = OpenGrokLogger.getLogger();

    /** The one and only instance of the HistoryGuru */
    private static HistoryGuru instance = new HistoryGuru();

    /** The history cache to use */
    private final HistoryCache historyCache;

    private Map<String, Repository> repositories =
        new HashMap<String, Repository>();
    private final int scanningDepth;

    /**
     * Creates a new instance of HistoryGuru, and try to set the default
     * source control system.
     */
    private HistoryGuru() {
        HistoryCache cache = null;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        scanningDepth=env.getScanningDepth();
        if (env.useHistoryCache()) {
            if (env.storeHistoryCacheInDB()) {
                cache = new JDBCHistoryCache();
            } else {
                cache = new FileHistoryCache();
            }
            try {
                cache.initialize();
            } catch (HistoryException he) {
                log.log(Level.WARNING,
                        "Failed to initialize the history cache", he);
                // Failed to initialize, run without a history cache
                cache = null;
            }
        }
        historyCache = cache;
    }

    /**
     * Get the one and only instance of the HistoryGuru
     * @return the one and only HistoryGuru instance
     */
    public static HistoryGuru getInstance()  {
        return instance;
    }

    /**
     * Return whether or not a cache should be used for the history log.
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
     * @throws IOException
     */
    public Annotation annotate(File file, String rev) throws IOException {
        Annotation ret = null;

        Repository repos = getRepository(file);
        if (repos != null) {
            ret = repos.annotate(file, rev);
            History hist = null;
            try {
                hist = repos.getHistory(file);
            } catch (HistoryException ex) {
                Logger.getLogger(HistoryGuru.class.getName()).log(Level.FINEST,
                    "Cannot get messages for tooltip: ", ex);
            }
            if (hist != null && ret != null) {
             Set<String> revs=ret.getRevisions();
             // !!! cannot do this because of not matching rev ids (keys)
             // first is the most recent one, so we need the position of "rev"
             // until the end of the list
             //if (hent.indexOf(rev)>0) {
             //     hent = hent.subList(hent.indexOf(rev), hent.size());
             //}
             for (HistoryEntry he : hist.getHistoryEntries()) {
                String cmr=he.getRevision();
                //TODO this is only for mercurial, for other SCMs it might also
                // be a problem, we need to revise how we shorten the rev # for
                // annotate
                String[] brev=cmr.split(":");
                if (revs.contains(brev[0])) {
                    ret.addDesc(brev[0], "changeset: "+he.getRevision()
                        +"\nsummary: "+he.getMessage()+"\nuser: "
                        +he.getAuthor()+"\ndate: "+he.getDate());
                }
             }
            }
        }

        return ret;
    }

    /**
     * Get the appropriate history reader for the file specified by parent and
     * basename.
     *
     * @param file The file to get the history reader for
     * @throws HistoryException If an error occurs while getting the history
     * @return A HistorReader that may be used to read out history data for a
     *  named file
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
        return getHistory(file, true);
    }

    /**
     * Get the history for the specified file.
     *
     * @param file the file to get the history for
     * @param withFiles whether or not the returned history should contain
     * a list of files touched by each changeset (the file list may be skipped
     * if false, but it doesn't have to)
     * @return history for the file
     * @throws HistoryException on error when accessing the history
     */
    public History getHistory(File file, boolean withFiles)
            throws HistoryException {
        final File dir = file.isDirectory() ? file : file.getParentFile();
        final Repository repos = getRepository(dir);

        History history = null;

        if (repos != null && repos.isWorking() && repos.fileHasHistory(file)
            && (!repos.isRemote() || RuntimeEnvironment.getInstance()
                .isRemoteScmSupported()))
        {
            if (useCache() && historyCache.supportsRepository(repos)) {
                history = historyCache.get(file, repos, withFiles);
            } else {
                history = repos.getHistory(file);
            }
        }

        return history;
    }

    /**
     * Get a named revision of the specified file.
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @return An InputStream containing the named revision of the file.
     */
    public InputStream getRevision(String parent, String basename, String rev)
    {
        InputStream ret = null;

        Repository rep = getRepository(new File(parent));
        if (rep != null) {
            ret = rep.getHistoryGet(parent, basename, rev);
        }
        return ret;
    }

    /**
     * Does this directory contain files with source control information?
     * @param file The name of the directory
     * @return true if the files in this directory have associated revision
     * history
     */
    public boolean hasHistory(File file) {
        Repository repos = getRepository(file);

        return repos == null
            ? false
            : repos.isWorking() && repos.fileHasHistory(file)
                && (RuntimeEnvironment.getInstance().isRemoteScmSupported()
                    || !repos.isRemote());
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
            Repository repos = getRepository(file);
            if (repos != null && repos.isWorking()) {
                return repos.fileHasAnnotation(file);
            }
        }

        return false;
    }

    private void addRepositories(File[] files, Collection<RepositoryInfo> repos,
            IgnoredNames ignoredNames, int depth)
    {
        addRepositories(files, repos, ignoredNames, true, depth);
    }

    /**
     * recursivelly search for repositories with a depth limit
     * @param files list of files to check if they contain a repo
     * @param repos list of found repos
     * @param ignoredNames what files to ignore
     * @param recursiveSearch whether to use recursive search
     * @param depth current depth - using global scanningDepth - one can limit
     *  this to improve scanning performance
     */
    private void addRepositories(File[] files, Collection<RepositoryInfo> repos,
            IgnoredNames ignoredNames, boolean recursiveSearch, int depth) {
        for (File file : files) {
            Repository repository = null;
            try {
                repository = RepositoryFactory.getRepository(file);
            } catch (InstantiationException ie) {
                log.log(Level.WARNING, "Could not create repoitory for '"
                    + file + "', could not instantiate the repository.", ie);
            } catch (IllegalAccessException iae) {
                log.log(Level.WARNING, "Could not create repoitory for '"
                    + file + "', missing access rights.", iae);
            }
            if (repository == null) {
                // Not a repository, search it's sub-dirs
                if (file.isDirectory() && !ignoredNames.ignore(file)) {
                    File subFiles[] = file.listFiles();
                    if (subFiles == null) {
                        log.log(Level.WARNING,
                            "Failed to get sub directories for '"
                            + file.getAbsolutePath()
                            + "', check access permissions.");
                    } else if (depth<=scanningDepth) {
                        addRepositories(subFiles, repos, ignoredNames, depth+1);
                    }
                }
            } else {
                try {
                    String path = file.getCanonicalPath();
                    repository.setDirectoryName(path);
                    if (RuntimeEnvironment.getInstance().isVerbose()) {
                        log.log(Level.CONFIG, "Adding <{0}> repository: <{1}>",
                            new Object[]{repository.getClass().getName(), path});
                    }

                    repos.add(new RepositoryInfo(repository));

                    // @TODO: Search only for one type of repository - the one found here
                    if (recursiveSearch && repository.supportsSubRepositories()) {
                        File subFiles[] = file.listFiles();
                        if (subFiles == null) {
                            log.log(Level.WARNING,
                                "Failed to get sub directories for '"
                                + file.getAbsolutePath()
                                + "', check access permissions.");
                        } else if (depth<=scanningDepth) {
                            // Search only one level down - if not: too much
                            // stat'ing for huge Mercurial repositories
                            addRepositories(subFiles, repos, ignoredNames,
                                false, depth+1);
                        }
                    }

                } catch (IOException exp) {
                    log.log(Level.WARNING, "Failed to get canonical path for "
                        + file.getAbsolutePath() + ": " + exp.getMessage());
                    log.log(Level.WARNING, "Repository will be ignored...", exp);
                }
            }
        }
    }

    /**
     * Search through the all of the directories and add all of the source
     * repositories found.
     *
     * @param dir the root directory to start the search in.
     */
    public void addRepositories(String dir) {
        List<RepositoryInfo> repos = new ArrayList<RepositoryInfo>();
        addRepositories(new File[] {new File(dir)}, repos,
                RuntimeEnvironment.getInstance().getIgnoredNames(),0);
        RuntimeEnvironment.getInstance().setRepositories(repos);
        invalidateRepositories(repos);
    }

    /**
     * Update the source the contents in the source repositories.
     */
    public void updateRepositories() {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();

        for (Map.Entry<String, Repository> entry : repositories.entrySet()) {
            Repository repository = entry.getValue();

            String path = entry.getKey();
            String type = repository.getClass().getSimpleName();

            if (repository.isWorking()) {
                if (verbose) {
                    log.info(String.format("Update %s repository in %s",
                        type, path));
                }

                try {
                    repository.update();
                } catch (UnsupportedOperationException e) {
                    log.warning(String.format("Skipping update of %s repository"
                        + " in %s: Not implemented", type, path));
                } catch (Exception e) {
                    log.log(Level.WARNING, "An error occured while updating "
                        + path + " (" + type + ")", e);
                }
            } else {
                log.warning(String.format("Skipping update of %s repository in "
                    + "%s: Missing SCM dependencies?", type, path));
            }
        }
    }

    /**
     * Update the source the contents in the source repositories.
     * @param paths A list of files/directories to update
     */
    public void updateRepositories(Collection<String> paths) {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();

        List<Repository> repos = getReposFromString(paths);

        for (Repository repository : repos) {
            String type = repository.getClass().getSimpleName();

            if (repository.isWorking()) {
                if (verbose) {
                    log.info(String.format("Update %s repository in %s", type,
                        repository.getDirectoryName()));
                }

                try {
                    repository.update();
                } catch (UnsupportedOperationException e) {
                    log.warning(String.format("Skipping update of %s repository"
                        + " in %s: Not implemented", type,
                        repository.getDirectoryName()));
                } catch (Exception e) {
                    log.log(Level.WARNING, "An error occured while updating "
                        + repository.getDirectoryName() + " (" + type + ")", e);
                }
            } else {
                log.warning(String.format("Skipping update of %s repository in"
                    + " %s: Missing SCM dependencies?", type,
                    repository.getDirectoryName()));
            }
        }
    }

    private void createCache(Repository repository, String sinceRevision) {
        if (!useCache()) {
            return;
        }

        String path = repository.getDirectoryName();
        String type = repository.getClass().getSimpleName();

        if (repository.isWorking()) {
            boolean verbose = RuntimeEnvironment.getInstance().isVerbose();
            long start = System.currentTimeMillis();

            if (verbose) {
                log.log(Level.INFO, "Create historycache for {0} ({1})",
                    new Object[]{path, type});
            }

            try {
                repository.createCache(historyCache, sinceRevision);
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "An error occured while creating cache for " + path + " ("
                    + type + ")", e);
            }

            if (verbose) {
                long stop = System.currentTimeMillis();
                log.log(Level.INFO, "Creating historycache for {0} took ({1}ms)",
                    new Object[]{path, String.valueOf(stop - start)});
            }
        } else {
            log.log(Level.WARNING, "Skipping creation of historycache of "
                + type + " repository in " + path + ": Missing SCM dependencies?");
        }
    }

    private void createCacheReal(Collection<Repository> repositories) {
        int num = Runtime.getRuntime().availableProcessors() * 2;
        String total = System.getProperty("org.opensolaris.opengrok.history.NumCacheThreads");
        if (total != null) {
            try {
                num = Integer.valueOf(total);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Failed to parse the number of cache threads to use for cache creation", t);
            }
        }
        ExecutorService executor = Executors.newFixedThreadPool(num);

        for (final Repository repos : repositories) {
            final String latestRev;
            try {
                latestRev = historyCache.getLatestCachedRevision(repos);
            } catch (HistoryException he) {
                log.log(Level.WARNING,
                        String.format(
                        "Failed to retrieve latest cached revision for %s",
                        repos.getDirectoryName()), he);
                continue;
            }
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    createCache(repos, latestRev);
                }
            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                // Wait forever
                executor.awaitTermination(999,TimeUnit.DAYS);
            } catch (InterruptedException exp) {
                OpenGrokLogger.getLogger().log(Level.WARNING,
                    "Received interrupt while waiting for executor to finish", exp);
            }
        }

        // The cache has been populated. Now, optimize how it is stored on
        // disk to enhance performance and save space.
        try {
            historyCache.optimize();
        } catch (HistoryException he) {
            OpenGrokLogger.getLogger().log(Level.WARNING,
                    "Failed optimizing the history cache database", he);
        }
    }

    public void createCache(Collection<String> repositories) {
        if (!useCache()) {
            return;
        }
        createCacheReal(getReposFromString(repositories));
    }

    public void removeCache(Collection<String> repositories) throws HistoryException {
        List<Repository> repos = getReposFromString(repositories);
        HistoryCache cache = historyCache;
        if (cache == null) {
            if (RuntimeEnvironment.getInstance().storeHistoryCacheInDB()) {
                cache = new JDBCHistoryCache();
                cache.initialize();
            } else {
                cache = new FileHistoryCache();
            }
        }
        for (Repository r : repos) {
            try {
                cache.clear(r);
                log.info("History cache for " + r.getDirectoryName() + " cleared.");
            } catch (HistoryException e) {
                log.warning("Clearing history cache for repository " +
                    r.getDirectoryName() + " failed: " + e.getLocalizedMessage());
            }
        }
        invalidateRepositories(repos);
    }

    /**
     * Create the history cache for all of the repositories
     */
    public void createCache() {
        if (!useCache()) {
            return;
        }

        createCacheReal(repositories.values());
    }

    private List<Repository> getReposFromString(Collection<String> repositories) {
        ArrayList<Repository> repos = new ArrayList<Repository>();
        File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (String file : repositories) {
            File f = new File(root, file);
            Repository r = getRepository(f);
            if (r == null) {
                log.log(Level.WARNING, "Could not locate a repository for {0}",
                    f.getAbsolutePath());
            } else if (!repos.contains(r)){
                repos.add(r);
            }
        }
        return repos;
    }

    /**
     * Ensure that we have a directory in the cache. If it's not there, fetch
     * its history and populate the cache. If it's already there, and the
     * cache is able to tell how recent it is, attempt to update it to the
     * most recent revision.
     *
     * @param file the root path to test
     * @throws HistoryException if an error occurs while accessing the
     * history cache
     */
    public void ensureHistoryCacheExists(File file) throws HistoryException {
        if (!useCache()) {
            return;
        }

        Repository repository = getRepository(file);

        if (repository == null) {
            // no repository -> no history :(
            return;
        }

        String sinceRevision = null;

        if (historyCache.hasCacheForDirectory(file, repository)) {
            sinceRevision = historyCache.getLatestCachedRevision(repository);
            if (sinceRevision == null) {
                // Cache already exists, but we don't know how recent it is,
                // so don't do anything.
                return;
            }
        }

        // Create cache from the beginning if it doesn't exist, or update it
        // incrementally otherwise.
        createCache(getRepository(file), sinceRevision);
    }

    protected Repository getRepository(File path) {
        Map<String, Repository> repos = repositories;

        File file = path;
        try {
            file = path.getCanonicalFile();
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to get canonical path for " + path, e);
            return null;
        }
        while (file != null) {
            Repository r = repos.get(file.getAbsolutePath());
            if (r != null) {
                return r;
            }
            file = file.getParentFile();
        }

        return null;
    }

    /**
     * Invalidate the current list of known repositories!
     *
     * @param repos The new repositories
     */
    public void invalidateRepositories(Collection<? extends RepositoryInfo> repos)
    {
        if (repos == null || repos.isEmpty()) {
            repositories.clear();
        } else {
            Map<String, Repository> nrep =
                new HashMap<String, Repository>(repos.size());
            for (RepositoryInfo i : repos) {
                try {
                    Repository r = RepositoryFactory.getRepository(i);
                    if (r == null) {
                        log.log(Level.WARNING,
                            "Failed to instanciate internal repository data for "
                            + i.getType() + " in " + i.getDirectoryName());
                    } else {
                        nrep.put(r.getDirectoryName(), r);
                    }
                } catch (InstantiationException ex) {
                    log.log(Level.WARNING, "Could not create " + i.getType()
                        + " for '" + i.getDirectoryName()
                        + "', could not instantiate the repository.", ex);
                } catch (IllegalAccessException iae) {
                    log.log(Level.WARNING, "Could not create " + i.getType()
                        + " for '" + i.getDirectoryName()
                        + "', missing access rights.", iae);
                }
            }
            repositories = nrep;
        }
    }
}
