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
import java.util.logging.Level;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * The HistoryGuru is used to implement an transparent layer to the various
 * source control systems.
 *
 * @author Chandan
 */
public class HistoryGuru {
    /** The one and only instance of the HistoryGuru */
    private static HistoryGuru instance = new HistoryGuru();

    /** The history cache to use */
    private HistoryCache historyCache;

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
     * @throws Exception In an error occurs while creating the annotations
     */
    public Annotation annotate(File file, String rev) throws Exception {
        Repository repos = getRepository(file);
        if (repos != null) {
            return repos.annotate(file, rev);
        }

        return null;
    }

    /**
     * Get the appropriate history reader for the file specified by parent and basename.
     *
     * @param file The file to get the history reader for
     * @throws java.io.IOException If an error occurs while trying to access the filesystem
     * @return A HistorReader that may be used to read out history data for a named file
     */
    public HistoryReader getHistoryReader(File file) throws IOException {
        if (file.isDirectory()) {
            return getDirectoryHistoryReader(file);
        }

        Class<? extends HistoryParser> parser = getHistoryParser(file);

        if (parser != null) {
            try {
                Repository repos = getRepository(file.getParentFile());
                History history = historyCache.get(file, repos);
                if (history == null) {
                    return null;
                }
                return new HistoryReader(history);
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception e) {
                throw new IOException("Error while constructing HistoryReader", e);
            }
        }

        return null;
    }
    
    /**
     * Get the appropriate history reader for a specific directory.
     *
     * @param file The directpru to get the history reader for
     * @throws java.io.IOException If an error occurs while trying to access the filesystem
     * @return A HistorReader that may be used to read out history data for a named file
     */
    private HistoryReader getDirectoryHistoryReader(File file) throws IOException {
        Class<? extends HistoryParser> parser = null;
        Repository repos = getRepository(file);
        if (repos != null) {
            parser = repos.getDirectoryHistoryParser();
        }

        if (parser == null) {
            // I did not find a match for the specified system. Use the default directory reader
            parser = DirectoryHistoryParser.class;
            repos = null;
        }

        if (parser != null) {
            try {
                History history = historyCache.get(file, repos);
                if (history != null) {
                    return new HistoryReader(history);
                }
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception e) {
                throw new IOException("Error while constructing HistoryReader", e);
            }
        }

        return null;
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
        Repository rep = getRepository(new File(parent));
        if (rep != null) {
            return rep.getHistoryGet(parent, basename, rev);
        }
        return null;
    }
    
    /**
     * Does this directory contain files with source control information?
     * @param file The name of the directory
     * @return true if the files in this directory have associated revision history
     */
    public boolean hasHistory(File file) {
        Repository repos = getRepository(file);
        return repos != null && repos.fileHasHistory(file);
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
            if (repos != null) {
                return repos.fileHasAnnotation(file);
            }
        }
        
        return false;
    }

    private void addRepositories(File[] files, Map<String, Repository> repos,
            IgnoredNames ignoredNames) {
        addRepositories(files, repos, ignoredNames, true);
    }

    private void addRepositories(File[] files, Map<String, Repository> repos,
            IgnoredNames ignoredNames, boolean recursiveSearch) {

        if (files == null) {
            return;
        }
        
        for (File file : files) {
            Repository repository = null;
            try {
                repository = RepositoryFactory.getRepository(file);
            } catch (InstantiationException ie) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Could not create repoitory for '" + file + "', could not instantiate the repository.", ie);
            } catch (IllegalAccessException iae) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Could not create repoitory for '" + file + "', missing access rights.", iae);
            }
            if (repository != null) {
                try {
                    String path = file.getCanonicalPath();
                    repository.setDirectoryName(path);
                    if (RuntimeEnvironment.getInstance().isVerbose()) {
                        OpenGrokLogger.getLogger().log(Level.INFO, "Adding <" + repository.getClass().getName() +  "> repository: <" + path + ">");
                    }
                    addRepository(repository, path, repos);

                    // TODO: Search only for one type of repository - the one found here
                    if (recursiveSearch && repository.supportsSubRepositories()) {
                        File subFiles[] = file.listFiles();
                        if (subFiles != null) {
                            // Search only one level down - if not: too much stat'ing for huge Mercurial repositories
                            addRepositories(subFiles, repos, ignoredNames, false); 
                        } else {
                            OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to get sub directories for '" + file.getAbsolutePath() + "', check access permissions.");
                        }
                    }
                    
                } catch (IOException exp) {
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to get canonical path for " + file.getAbsolutePath() + ": " + exp.getMessage());
                    OpenGrokLogger.getLogger().log(Level.WARNING, "Repository will be ignored...", exp);
                }
            } else {
                // Not a repository, search it's sub-dirs
                if (file.isDirectory() && !ignoredNames.ignore(file)) {
                    File subFiles[] = file.listFiles();
                    if (subFiles != null) {
                        addRepositories(subFiles, repos, ignoredNames);
                    } else {
                        OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to get sub directories for '" + file.getAbsolutePath() + "', check access permissions.");
                    }
                }
            }
        }        
    }
    
    private void addRepository(Repository rep, String path, Map<String, Repository> repos) {
        repos.put(path, rep);
    }

    /**
     * Search through the all of the directories and add all of the source
     * repositories found.
     * 
     * @param dir the root directory to start the search in.
     */
    public void addRepositories(String dir) {
        Map<String, Repository> repos = new HashMap<String, Repository>();
        addRepositories(new File[] {new File(dir)}, repos, 
                RuntimeEnvironment.getInstance().getIgnoredNames());
        RuntimeEnvironment.getInstance().setRepositories(repos);
    }

    /**
     * Update the source the contents in the source repositories.
     */
    public void updateRepositories() {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();
        
        for (Map.Entry<String, Repository> entry : RuntimeEnvironment.getInstance().getRepositories().entrySet()) {
            Repository repository = entry.getValue();
            
            String path = entry.getKey();
            String type = repository.getClass().getSimpleName();
            
            if (verbose) {
                OpenGrokLogger.getLogger().log(Level.INFO, "Update " + type + " repository in " + path);
            }
            
            try {
                repository.update();
            } catch (Exception e) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while updating " + path + " (" + type + ")", e);
            }
        }
    }
    
    private void createCache(Repository repository) {
        boolean verbose = RuntimeEnvironment.getInstance().isVerbose();
        String path = repository.getDirectoryName();
        String type = repository.getClass().getSimpleName();
        long start = System.currentTimeMillis();
        
        if (verbose) {
            OpenGrokLogger.getLogger().log(Level.INFO, "Create historycache for " + path + " (" + type + ")");
        }

        try {
            repository.createCache(historyCache);
        } catch (Exception e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "An error occured while creating cache for " + path + " (" + type + ")", e);
        }
        long stop = System.currentTimeMillis();
        if (verbose) {
            OpenGrokLogger.getLogger().log(Level.INFO, "Creating historycache for " + path + " took (" + (stop - start) + "ms)");
        }   
    }
    
    /**
     * Create the history cache for all of the repositories
     */
    public void createCache() {
        boolean threading = System.getProperty("org.opensolaris.opengrok.history.threads", null) != null;
        ArrayList<Thread> threads = new ArrayList<Thread>();
        
        for (Map.Entry<String, Repository> entry : RuntimeEnvironment.getInstance().getRepositories().entrySet()) {
            if (threading) {
                final Repository repos = entry.getValue();
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        createCache(repos);
                    }                    
                });
                t.start();
                threads.add(t);
            } else {
                createCache(entry.getValue());
            }
        }
        // Wait for all threads to finish
        while (threads.size() > 0) {
            for (Thread t : threads) {
                if (!t.isAlive()) {
                    try {
                        t.join();
                        threads.remove(t);
                        break;
                    } catch (InterruptedException ex) {
                    }
                } 
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

    public void createCache(List<String> repositories) {
        boolean threading = System.getProperty("org.opensolaris.opengrok.history.threads", null) != null;
        ArrayList<Thread> threads = new ArrayList<Thread>();
        File root = RuntimeEnvironment.getInstance().getSourceRootFile();
        for (String file : repositories) {
            final Repository repos = getRepository(new File(root, file));
            if (repos != null) {
                if (threading) {
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            createCache(repos);
                        }
                    });
                    t.start();
                    threads.add(t);
                } else {
                    createCache(repos);
                }
            }
        }
        
        // Wait for all threads to finish
        while (threads.size() > 0) {
            for (Thread t : threads) {
                if (!t.isAlive()) {
                    try {
                        t.join();
                        threads.remove(t);
                        break;
                    } catch (InterruptedException ex) {
                    }
                } 
            }    
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

    
    private Repository getRepository(File path) {
        Map<String, Repository> repos = RuntimeEnvironment.getInstance().getRepositories();
        
        try {
            path = path.getCanonicalFile();
        } catch (IOException e) {
            OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to get canonical path for " + path, e);
            return null;
        }
        while (path != null) {
            try {
                Repository r = repos.get(path.getCanonicalPath());
                if (r != null) {
                    return r;
                }
            } catch (IOException e) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Failed to get canonical path for " + path, e);
            }
            path = path.getParentFile();
        }

        return null;
    }    
}
