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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /** The one and only instance of the HistoryGuru */
    private static HistoryGuru instance = new HistoryGuru();

    /** The history cache to use */
    private final HistoryCache historyCache;
    
    private static final Logger log = OpenGrokLogger.getLogger();
    private Map<String, Repository> repositories = new HashMap<String, Repository>();

    /**
     * Creates a new instance of HistoryGuru, and try to set the default
     * source control system.
     */
    private HistoryGuru() {
        historyCache = new FileHistoryCache();
    }
    
    /**
     * Get the one and only instance of the HistoryGuru
     * @return the one and only HistoryGuru instance
     */
    public static HistoryGuru getInstance()  {
        return instance;
    }
    
    /**
     * Get the <code>HistoryParser</code> to use for the specified file.
     */
    private Class<? extends HistoryParser> getHistoryParser(File file) {
        Repository repos = getRepository(file.getParentFile());
        if (repos != null && repos.fileHasHistory(file)) {
            Class<? extends HistoryParser> parser;
            parser = repos.getHistoryParser();
            if (parser != null) {
                return parser;
            }
        }
        return null;
    }

    /**
     * Annotate the specified revision of a file.
     *
     * @param file the file to annotate
     * @param rev the revision to annotate (<code>null</code> means BASE)
     * @return file annotation, or <code>null</code> if the
     * <code>HistoryParser</code> does not support annotation
     */
    public Annotation annotate(File file, String rev) throws IOException {
        Annotation ret = null;

        Repository repos = getRepository(file);
        if (repos != null) {
            ret = repos.annotate(file, rev);
        }

        return ret;
    }

    /**
     * Get the appropriate history reader for the file specified by parent and basename.
     *
     * @param file The file to get the history reader for
     * @throws HistoryException If an error occurs while getting the history
     * @return A HistorReader that may be used to read out history data for a named file
     */
    public HistoryReader getHistoryReader(File file) throws HistoryException {
        if (file.isDirectory()) {
            return getDirectoryHistoryReader(file);
        }

        Class<? extends HistoryParser> parser = getHistoryParser(file);

        if (parser != null) {
            Repository repos = getRepository(file.getParentFile());
            if (repos != null && repos.isWorking()) {
                if (!RuntimeEnvironment.getInstance().isRemoteScmSupported() && repos.isRemote()) {
                    return null;
                }
                History history = historyCache.get(file, repos);
                if (history != null) {
                    return new HistoryReader(history);
                }
            }
        }

        return null;
    }
    
    /**
     * Get the appropriate history reader for a specific directory.
     *
     * @param file The directpru to get the history reader for
     * @throws HistoryException If an error occurs while getting the history
     * @return A HistorReader that may be used to read out history data for a named file
     */
    private HistoryReader getDirectoryHistoryReader(File file)
            throws HistoryException {
        HistoryReader ret = null;
        Repository repos = getRepository(file);
        History history = historyCache.get(file, repos);
        if (history != null) {
            ret = new HistoryReader(history);
        }

        return ret;
    }

    /**
     * Get a named revision of the specified file.
     * @param parent The directory containing the file
     * @param basename The name of the file
     * @param rev The revision to get
     * @throws java.io.IOException If an error occurs while reading out the version
     * @return An InputStream containing the named revision of the file.
     */
    public InputStream getRevision(String parent, String basename, String rev) throws IOException {
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
     * @return true if the files in this directory have associated revision history
     */
    public boolean hasHistory(File file) {
        Repository repos = getRepository(file);

        boolean ret = false;

        if (repos != null) {
            ret = repos.isWorking() && repos.fileHasHistory(file);
            if (!RuntimeEnvironment.getInstance().isRemoteScmSupported() && repos.isRemote()) {
                ret = false;
            }
        }
        return ret;
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

    private void addRepositories(File[] files, List<RepositoryInfo> repos,
            IgnoredNames ignoredNames) {
        addRepositories(files, repos, ignoredNames, true);
    }

    @SuppressWarnings("PMD.ConfusingTernary")
    private void addRepositories(File[] files, List<RepositoryInfo> repos,
            IgnoredNames ignoredNames, boolean recursiveSearch) {        
        for (File file : files) {
            Repository repository = null;
            try {
                repository = RepositoryFactory.getRepository(file);
            } catch (InstantiationException ie) {
                log.log(Level.WARNING, "Could not create repoitory for '" + file + "', could not instantiate the repository.", ie);
            } catch (IllegalAccessException iae) {
                log.log(Level.WARNING, "Could not create repoitory for '" + file + "', missing access rights.", iae);
            }
            if (repository != null) {
                try {
                    String path = file.getCanonicalPath();
                    repository.setDirectoryName(path);
                    if (RuntimeEnvironment.getInstance().isVerbose()) {
                        log.log(Level.INFO, "Adding <" + repository.getClass().getName() +  "> repository: <" + path + ">");
                    }
                    
                    repos.add(new RepositoryInfo(repository));

                    // @TODO: Search only for one type of repository - the one found here
                    if (recursiveSearch && repository.supportsSubRepositories()) {
                        File subFiles[] = file.listFiles();
                        if (subFiles == null) {
                            log.log(Level.WARNING, "Failed to get sub directories for '" + file.getAbsolutePath() + "', check access permissions.");
                        } else {
                            // Search only one level down - if not: too much stat'ing for huge Mercurial repositories
                            addRepositories(subFiles, repos, ignoredNames, false); 
                        }
                    }
                    
                } catch (IOException exp) {
                    log.log(Level.WARNING, "Failed to get canonical path for " + file.getAbsolutePath() + ": " + exp.getMessage());
                    log.log(Level.WARNING, "Repository will be ignored...", exp);
                }
            } else {
                // Not a repository, search it's sub-dirs
                if (file.isDirectory() && !ignoredNames.ignore(file)) {
                    File subFiles[] = file.listFiles();
                    if (subFiles == null) {
                        log.log(Level.WARNING, "Failed to get sub directories for '" + file.getAbsolutePath() + "', check access permissions.");
                    } else {
                        addRepositories(subFiles, repos, ignoredNames);
                    }
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
                RuntimeEnvironment.getInstance().getIgnoredNames());
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
                    log.info(String.format("Update %s repository in %s", type, path));
                }

                try {
                    repository.update();
                } catch (UnsupportedOperationException e) {
                    log.warning(String.format("Skipping update of %s repository in %s: Not implemented", type, path));
                } catch (Exception e) {
                    log.log(Level.WARNING, "An error occured while updating " + path + " (" + type + ")", e);
                }
            } else {
                log.warning(String.format("Skipping update of %s repository in %s: Missing SCM dependencies?", type, path));
            }
        }
    }
    
    /**
     * Update the source the contents in the source repositories.
     * @param paths A list of files/directories to update
     */
    public void updateRepositories(List<String> paths) {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();

        ArrayList<Repository> repos = new ArrayList<Repository>();
        File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (String path : paths) {
            File f = new File(root, path);
            Repository r = getRepository(f);
            if (!repos.contains(r)) {
                repos.add(r);
            }
        }

        for (Repository repository : repos) {
            String type = repository.getClass().getSimpleName();

            if (repository.isWorking()) {
                if (verbose) {
                    log.info(String.format("Update %s repository in %s", type, repository.getDirectoryName()));
                }

                try {
                    repository.update();
                } catch (UnsupportedOperationException e) {
                    log.warning(String.format("Skipping update of %s repository in %s: Not implemented", type, repository.getDirectoryName()));
                } catch (Exception e) {
                    log.log(Level.WARNING, "An error occured while updating " + repository.getDirectoryName() + " (" + type + ")", e);
                }
            } else {
                log.warning(String.format("Skipping update of %s repository in %s: Missing SCM dependencies?", type, repository.getDirectoryName()));
            }
        }
    }

    private void createCache(Repository repository) {
        String path = repository.getDirectoryName();
        String type = repository.getClass().getSimpleName();

        if (repository.isWorking()) {
            boolean verbose = RuntimeEnvironment.getInstance().isVerbose();
            long start = System.currentTimeMillis();

            if (verbose) {
                log.log(Level.INFO, "Create historycache for " + path + " (" + type + ")");
            }

            try {
                repository.createCache(historyCache);
            } catch (Exception e) {
                log.log(Level.WARNING, "An error occured while creating cache for " + path + " (" + type + ")", e);
            }

            if (verbose) {
                long stop = System.currentTimeMillis();
                log.log(Level.INFO, "Creating historycache for " + path + " took (" + (stop - start) + "ms)");
            }
        } else {
            log.warning(String.format("Skipping creation of historycache of %s repository in %s: Missing SCM dependencies?", type, path));
        }
    }

    private void createCacheReal(List<Repository> repositories) {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (final Repository repos : repositories) {
            executor.submit(new Runnable() {
                public void run() {
                    createCache(repos);
                }
            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                // Wait forever
                // @newjdk : 999,TimeUnit.DAYS
                executor.awaitTermination(999 * 60 * 60 * 24, TimeUnit.SECONDS);
            } catch (InterruptedException exp) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Received interrupt while waiting for executor to finish", exp);
            }
        }
    }

    /**
     * Create the history cache for all of the repositories
     */
    public void createCache() {
        ArrayList<Repository> repos = new ArrayList<Repository>();
        for (Map.Entry<String, Repository> entry : repositories.entrySet()) {
            repos.add(entry.getValue());
        }
        createCacheReal(repos);
    }

    public void createCache(List<String> repositories) {
        ArrayList<Repository> repos = new ArrayList<Repository>();
        File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (String file : repositories) {
            File f = new File(root, file);
            Repository r = getRepository(f);
            if (r == null) {
                log.warning("Could not locate a repository for " + f.getAbsolutePath());
            } else {
                repos.add(r);
            }
        }
        createCacheReal(repos);
    }

    /**
     * Ensure that any file beneath a given path has a history cache
     *
     * @param file the root path to test
     * @throws HistoryException if an error occurs while accessing the
     * history cache
     * @throws java.io.IOException if an error occurs while accessing the
     *                             filesystem.
     */
    public void ensureHistoryCacheExists(File file)
            throws HistoryException, IOException {
        if (!historyCache.isUpToDate(file)) {
            createCache(getRepository(file));
        }
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
    public void invalidateRepositories(List<RepositoryInfo> repos) {
        if (repos == null || repos.isEmpty()) {
            repositories.clear();
        } else {
            Map<String, Repository> nrep = new HashMap<String, Repository>(repos.size());

            for (RepositoryInfo i : repos) {
                try {
                    Repository r = RepositoryFactory.getRepository(i);
                    if (r == null) {
                        log.warning("Failed to instanciate internal repository data for " + i.getType() + " in " + i.getDirectoryName());
                    } else {
                        nrep.put(r.getDirectoryName(), r);
                    }
                } catch (InstantiationException ex) {
                    log.log(Level.WARNING, "Could not create " + i.getType() + " for '" + i.getDirectoryName() + "', could not instantiate the repository.", ex);
                } catch (IllegalAccessException iae) {
                    log.log(Level.WARNING, "Could not create " + i.getType() + " for '" + i.getDirectoryName() + "', missing access rights.", iae);
                }
            }
            repositories = nrep;
        }
    }
}
