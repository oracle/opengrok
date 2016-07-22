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
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.authorization.AuthorizationFramework;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.Filter;
import org.opensolaris.opengrok.index.IgnoredNames;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.IOUtils;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;


/**
 * The RuntimeEnvironment class is used as a placeholder for the current
 * configuration this execution context (classloader) is using.
 */
public final class RuntimeEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeEnvironment.class);

    private Configuration configuration;
    private final ThreadLocal<Configuration> threadConfig;
    private static final RuntimeEnvironment instance = new RuntimeEnvironment();
    private static ExecutorService historyExecutor = null;
    private static ExecutorService historyRenamedExecutor = null;
    private static ExecutorService searchExecutor = null;

    private final Map<Project, List<RepositoryInfo>> repository_map = new TreeMap<>();
    private final Map<Project, Set<Group>> project_group_map = new TreeMap<>();

    /* Get thread pool used for top-level repository history generation. */
    public static synchronized ExecutorService getHistoryExecutor() {
        if (historyExecutor == null) {
            int num = Runtime.getRuntime().availableProcessors();
            String total = System.getProperty("org.opensolaris.opengrok.history.NumCacheThreads");
            if (total != null) {
                try {
                    num = Integer.valueOf(total);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Failed to parse the number of "
                            + "cache threads to use for cache creation", t);
                }
            }

            historyExecutor = Executors.newFixedThreadPool(num,
                    new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("history-handling-" + thread.getId());
                    return thread;
                }
            });
        }

        return historyExecutor;
    }

    /* Get thread pool used for history generation of renamed files. */
    public static synchronized ExecutorService getHistoryRenamedExecutor() {
        if (historyRenamedExecutor == null) {
            int num = Runtime.getRuntime().availableProcessors();
            String total = System.getProperty("org.opensolaris.opengrok.history.NumCacheRenamedThreads");
            if (total != null) {
                try {
                    num = Integer.valueOf(total);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Failed to parse the number of "
                            + "cache threads to use for cache creation of renamed files", t);
                }
            }

            historyRenamedExecutor = Executors.newFixedThreadPool(num,
                    new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("renamed-handling-" + thread.getId());
                    return thread;
                }
            });
        }

        return historyRenamedExecutor;
    }

    /* Get thread pool used for multi-project searches. */
    public synchronized ExecutorService getSearchExecutor() {
        if (searchExecutor == null) {
            searchExecutor = Executors.newFixedThreadPool(
                this.getMaxSearchThreadCount(),
                new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setName("search-" + thread.getId());
                    return thread;
                }
            });
        }

        return searchExecutor;
    }

    public static synchronized void freeHistoryExecutor() {
        historyExecutor = null;
    }

    public static synchronized void destroyRenamedHistoryExecutor() throws InterruptedException {
        if (historyRenamedExecutor != null) {
            historyRenamedExecutor.shutdown();
            // All the jobs should be completed by now however for testing
            // we would like to make sure the threads are gone.
            historyRenamedExecutor.awaitTermination(1, TimeUnit.MINUTES);
            historyRenamedExecutor = null;
        }
    }

    /**
     * Get the one and only instance of the RuntimeEnvironment
     *
     * @return the one and only instance of the RuntimeEnvironment
     */
    public static RuntimeEnvironment getInstance() {
        return instance;
    }

    /**
     * Creates a new instance of RuntimeEnvironment. Private to ensure a
     * singleton pattern.
     */
    private RuntimeEnvironment() {
        configuration = new Configuration();
        threadConfig = new ThreadLocal<Configuration>() {
            @Override
            protected Configuration initialValue() {
                return configuration;
            }
        };
    }

    private String getCanonicalPath(String s) {
        try {
            File file = new File(s);
            if (!file.exists()) {
                return s;
            }
            return file.getCanonicalPath();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get canonical path", ex);
            return s;
        }
    }

    public int getScanningDepth() {
        return threadConfig.get().getScanningDepth();
    }

    public void setScanningDepth(int scanningDepth) {
        threadConfig.get().setScanningDepth(scanningDepth);
    }

    public int getCommandTimeout() {
        return threadConfig.get().getCommandTimeout();
    }

    public void setCommandTimeout(int timeout) {
        threadConfig.get().setCommandTimeout(timeout);
    }

    /**
     * Get the path to the where the index database is stored
     *
     * @return the path to the index database
     */
    public String getDataRootPath() {
        return threadConfig.get().getDataRoot();
    }

    /**
     * Get a file representing the index database
     *
     * @return the index database
     */
    public File getDataRootFile() {
        File ret = null;
        String file = getDataRootPath();
        if (file != null) {
            ret = new File(file);
        }

        return ret;
    }

    /**
     * Set the path to where the index database is stored
     *
     * @param dataRoot the index database
     */
    public void setDataRoot(String dataRoot) {
        threadConfig.get().setDataRoot(getCanonicalPath(dataRoot));
    }

    /**
     * Get the path to where the sources are located
     *
     * @return path to where the sources are located
     */
    public String getSourceRootPath() {
        return configuration.getSourceRoot();
    }

    /**
     * Get a file representing the directory where the sources are located
     *
     * @return A file representing the directory where the sources are located
     */
    public File getSourceRootFile() {
        File ret = null;
        String file = getSourceRootPath();
        if (file != null) {
            ret = new File(file);
        }

        return ret;
    }

    /**
     * Specify the source root
     *
     * @param sourceRoot the location of the sources
     */
    public void setSourceRoot(String sourceRoot) {
        configuration.setSourceRoot(getCanonicalPath(sourceRoot));
    }

    /**
     * Returns a path relative to source root. This would just be a simple
     * substring operation, except we need to support symlinks outside the
     * source root.
     *
     * @param file A file to resolve
     * @param stripCount Number of characters past source root to strip
     * @throws IOException If an IO error occurs
     * @throws FileNotFoundException If the file is not relative to source root
     * @return Path relative to source root
     */
    public String getPathRelativeToSourceRoot(File file, int stripCount) throws IOException {
        String canonicalPath = file.getCanonicalPath();
        String sourceRoot = getSourceRootPath();
        if (canonicalPath.startsWith(sourceRoot)) {
            return canonicalPath.substring(sourceRoot.length() + stripCount);
        }
        for (String allowedSymlink : getAllowedSymlinks()) {
            String allowedTarget = new File(allowedSymlink).getCanonicalPath();
            if (canonicalPath.startsWith(allowedTarget)) {
                return canonicalPath.substring(allowedTarget.length()
                        + stripCount);
            }
        }
        throw new FileNotFoundException("Failed to resolve [" + canonicalPath
                + "] relative to source root [" + sourceRoot + "]");
    }

    /**
     * Do we have projects?
     *
     * @return true if we have projects
     */
    public boolean hasProjects() {
        List<Project> proj = getProjects();
        return (proj != null && !proj.isEmpty());
    }

    /**
     * Get all of the projects
     *
     * @return a list containing all of the projects (may be null)
     */
    public List<Project> getProjects() {
        return threadConfig.get().getProjects();
    }

    /**
     * Set the list of the projects
     *
     * @param projects the list of projects to use
     */
    public void setProjects(List<Project> projects) {
        populateGroups(getGroups(), projects);
        threadConfig.get().setProjects(projects);
    }

    /**
     * Do we have groups?
     *
     * @return true if we have groups
     */
    public boolean hasGroups() {
        return (getGroups() != null && !getGroups().isEmpty());
    }

    /**
     * Get all of the groups
     *
     * @return a set containing all of the groups (may be null)
     */
    public Set<Group> getGroups() {
        return threadConfig.get().getGroups();
    }

    /**
     * Set the list of the groups
     *
     * @param groups the set of groups to use
     */
    public void setGroups(Set<Group> groups) {
        populateGroups(groups, getProjects());
        threadConfig.get().setGroups(groups);
    }

    /**
     * Register this thread in the thread/configuration map (so that all
     * subsequent calls to the RuntimeEnvironment from this thread will use the
     * same configuration
     *
     * @return this instance
     */
    public RuntimeEnvironment register() {
        threadConfig.set(configuration);
        return this;
    }

    /**
     * Returns constructed project - repositories map.
     *
     * @return the map
     * @see #generateProjectRepositoriesMap
     */
    public Map<Project, List<RepositoryInfo>> getProjectRepositoriesMap() {
        return repository_map;
    }

    /**
     * Get the context name of the web application
     *
     * @return the web applications context name
     */
    public String getUrlPrefix() {
        return threadConfig.get().getUrlPrefix();
    }

    /**
     * Set the web context name
     *
     * @param urlPrefix the web applications context name
     */
    public void setUrlPrefix(String urlPrefix) {
        threadConfig.get().setUrlPrefix(urlPrefix);
    }

    /**
     * Get the name of the ctags program in use
     *
     * @return the name of the ctags program in use
     */
    public String getCtags() {
        return threadConfig.get().getCtags();
    }

    /**
     * Specify the CTags program to use
     *
     * @param ctags the ctags program to use
     */
    public void setCtags(String ctags) {
        threadConfig.get().setCtags(ctags);
    }

    public int getCachePages() {
        return threadConfig.get().getCachePages();
    }

    public void setCachePages(int cachePages) {
        threadConfig.get().setCachePages(cachePages);
    }

    public int getHitsPerPage() {
        return threadConfig.get().getHitsPerPage();
    }

    public void setHitsPerPage(int hitsPerPage) {
        threadConfig.get().setHitsPerPage(hitsPerPage);
    }

    // cache these tests instead of reruning them for every call
    private transient Boolean exCtagsFound;
    private transient Boolean isUniversalCtagsVal;

    /**
     * Validate that I have a Exuberant ctags program I may use
     *
     * @return true if success, false otherwise
     */
    public boolean validateExuberantCtags() {
        if (exCtagsFound == null) {
            Executor executor = new Executor(new String[]{getCtags(), "--version"});
            executor.exec(false);
            String output = executor.getOutputString();
            boolean isUnivCtags = output!=null?output.contains("Universal Ctags"):false;
            if (output == null || (!output.contains("Exuberant Ctags") && !isUnivCtags)) {
                LOGGER.log(Level.SEVERE, "Error: No Exuberant Ctags found in PATH !\n"
                        + "(tried running " + "{0}" + ")\n"
                        + "Please use option -c to specify path to a good "
                        + "Exuberant Ctags program.\n"
                        + "Or set it in java property "
                        + "org.opensolaris.opengrok.analysis.Ctags", getCtags());
                exCtagsFound = false;
            } else {
                if (isUnivCtags) {
                    isUniversalCtagsVal = true;
                }
                exCtagsFound = true;
            }
        }
        return exCtagsFound;
    }

    /**
     * Are we using Universal ctags?
     *
     * @return true if we are using Universal ctags
     */
    public boolean isUniversalCtags() {
        if (isUniversalCtagsVal == null) {
            isUniversalCtagsVal = false;
            Executor executor = new Executor(new String[]{getCtags(), "--version"});

            executor.exec(false);
            String output = executor.getOutputString();
            if (output.contains("Universal Ctags")) {
                isUniversalCtagsVal = true;
            }
        }
        return isUniversalCtagsVal;
    }

    /**
     * Get the max time a SMC operation may use to avoid being cached
     *
     * @return the max time
     */
    public int getHistoryReaderTimeLimit() {
        return threadConfig.get().getHistoryCacheTime();
    }

    /**
     * Specify the maximum time a SCM operation should take before it will be
     * cached (in ms)
     *
     * @param historyReaderTimeLimit the max time in ms before it is cached
     */
    public void setHistoryReaderTimeLimit(int historyReaderTimeLimit) {
        threadConfig.get().setHistoryCacheTime(historyReaderTimeLimit);
    }

    /**
     * Is history cache currently enabled?
     *
     * @return true if history cache is enabled
     */
    public boolean useHistoryCache() {
        return threadConfig.get().isHistoryCache();
    }

    /**
     * Specify if we should use history cache or not
     *
     * @param useHistoryCache set false if you do not want to use history cache
     */
    public void setUseHistoryCache(boolean useHistoryCache) {
        threadConfig.get().setHistoryCache(useHistoryCache);
    }

    /**
     * Should the history cache be stored in a database instead of in XML files?
     *
     * @return {@code true} if the cache should be stored in a database
     */
    public boolean storeHistoryCacheInDB() {
        return threadConfig.get().isHistoryCacheInDB();
    }

    /**
     * Set whether the history cache should be stored in a database.
     *
     * @param store {@code true} if the cache should be stored in a database
     */
    public void setStoreHistoryCacheInDB(boolean store) {
        threadConfig.get().setHistoryCacheInDB(store);
    }

    /**
     * Should we generate HTML or not during the indexing phase
     *
     * @return true if HTML should be generated during the indexing phase
     */
    public boolean isGenerateHtml() {
        return threadConfig.get().isGenerateHtml();
    }

    /**
     * Specify if we should generate HTML or not during the indexing phase
     *
     * @param generateHtml set this to true to pregenerate HTML
     */
    public void setGenerateHtml(boolean generateHtml) {
        threadConfig.get().setGenerateHtml(generateHtml);
    }

    /**
     * Set if we should compress the xref files or not
     *
     * @param compressXref set to true if the generated html files should be
     * compressed
     */
    public void setCompressXref(boolean compressXref) {
        threadConfig.get().setCompressXref(compressXref);
    }

    /**
     * Are we using compressed HTML files?
     *
     * @return {@code true} if the html-files should be compressed.
     */
    public boolean isCompressXref() {
        return threadConfig.get().isCompressXref();
    }

    public boolean isQuickContextScan() {
        return threadConfig.get().isQuickContextScan();
    }

    public void setQuickContextScan(boolean quickContextScan) {
        threadConfig.get().setQuickContextScan(quickContextScan);
    }

    public List<RepositoryInfo> getRepositories() {
        return threadConfig.get().getRepositories();
    }

    /**
     * Set the map of external SCM repositories
     *
     * @param repositories the repositories to use
     */
    public void setRepositories(List<RepositoryInfo> repositories) {
        threadConfig.get().setRepositories(repositories);
    }

    /**
     * Set the project that is specified to be the default project to use. The
     * default project is the project you will search (from the web application)
     * if the page request didn't contain the cookie..
     *
     * @param defaultProject The default project to use
     */
    public void setDefaultProject(Project defaultProject) {
        threadConfig.get().setDefaultProject(defaultProject);
    }

    /**
     * Get the project that is specified to be the default project to use. The
     * default project is the project you will search (from the web application)
     * if the page request didn't contain the cookie..
     *
     * @return the default project (may be null if not specified)
     */
    public Project getDefaultProject() {
        return threadConfig.get().getDefaultProject();
    }

    /**
     *
     * @return at what size (in MB) we should flush the buffer
     */
    public double getRamBufferSize() {
        return threadConfig.get().getRamBufferSize();
    }

    /**
     * Set the size of buffer which will determine when the docs are flushed to
     * disk. Specify size in MB please. 16MB is default note that this is per
     * thread (lucene uses 8 threads by default in 4.x)
     *
     * @param ramBufferSize the size(in MB) when we should flush the docs
     */
    public void setRamBufferSize(double ramBufferSize) {
        threadConfig.get().setRamBufferSize(ramBufferSize);
    }

    public void setPluginDirectory(String pluginDirectory) {
        threadConfig.get().setPluginDirectory(pluginDirectory);
    }

    public String getPluginDirectory() {
        return threadConfig.get().getPluginDirectory();
    }

    /**
     * Is the verbosity flag turned on?
     *
     * @return true if we can print extra information
     */
    public boolean isVerbose() {
        return threadConfig.get().isVerbose();
    }

    /**
     * Set the verbosity flag (to add extra debug information in output)
     *
     * @param verbose new value
     */
    public void setVerbose(boolean verbose) {
        threadConfig.get().setVerbose(verbose);
    }

    /**
     * Is the progress print flag turned on?
     *
     * @return true if we can print per project progress %
     */
    public boolean isPrintProgress() {
        return threadConfig.get().isPrintProgress();
    }

    /**
     * Set the printing of progress % flag (user convenience)
     *
     * @param printP new value
     */
    public void setPrintProgress(boolean printP) {
        threadConfig.get().setPrintProgress(printP);
    }

    /**
     * Specify if a search may start with a wildcard. Note that queries that
     * start with a wildcard will give a significant impact on the search
     * performance.
     *
     * @param allowLeadingWildcard set to true to activate (disabled by default)
     */
    public void setAllowLeadingWildcard(boolean allowLeadingWildcard) {
        threadConfig.get().setAllowLeadingWildcard(allowLeadingWildcard);
    }

    /**
     * Is leading wildcards allowed?
     *
     * @return true if a search may start with a wildcard
     */
    public boolean isAllowLeadingWildcard() {
        return threadConfig.get().isAllowLeadingWildcard();
    }

    public IgnoredNames getIgnoredNames() {
        return threadConfig.get().getIgnoredNames();
    }

    public void setIgnoredNames(IgnoredNames ignoredNames) {
        threadConfig.get().setIgnoredNames(ignoredNames);
    }

    public Filter getIncludedNames() {
        return threadConfig.get().getIncludedNames();
    }

    public void setIncludedNames(Filter includedNames) {
        threadConfig.get().setIncludedNames(includedNames);
    }

    /**
     * Returns the user page for the history listing
     *
     * @return the URL string fragment preceeding the username
     */
    public String getUserPage() {
        return threadConfig.get().getUserPage();
    }

    /**
     * Get the client command to use to access the repository for the given
     * fully qualified classname.
     *
     * @param clazzName name of the targeting class
     * @return {@code null} if not yet set, the client command otherwise.
     */
    public String getRepoCmd(String clazzName) {
        return threadConfig.get().getRepoCmd(clazzName);
    }

    /**
     * Set the client command to use to access the repository for the given
     * fully quallified classname.
     *
     * @param clazzName name of the targeting class. If {@code null} this method
     * does nothing.
     * @param cmd the client command to use. If {@code null} the corresponding
     * entry for the given clazzName get removed.
     * @return the client command previously set, which might be {@code null}.
     */
    public String setRepoCmd(String clazzName, String cmd) {
        return threadConfig.get().setRepoCmd(clazzName, cmd);
    }

    /**
     * Sets the user page for the history listing
     *
     * @param userPage the URL fragment preceeding the username from history
     */
    public void setUserPage(String userPage) {
        threadConfig.get().setUserPage(userPage);
    }

    /**
     * Returns the user page suffix for the history listing
     *
     * @return the URL string fragment following the username
     */
    public String getUserPageSuffix() {
        return threadConfig.get().getUserPageSuffix();
    }

    /**
     * Sets the user page suffix for the history listing
     *
     * @param userPageSuffix the URL fragment following the username from
     * history
     */
    public void setUserPageSuffix(String userPageSuffix) {
        threadConfig.get().setUserPageSuffix(userPageSuffix);
    }

    /**
     * Returns the bug page for the history listing
     *
     * @return the URL string fragment preceeding the bug ID
     */
    public String getBugPage() {
        return threadConfig.get().getBugPage();
    }

    /**
     * Sets the bug page for the history listing
     *
     * @param bugPage the URL fragment preceeding the bug ID
     */
    public void setBugPage(String bugPage) {
        threadConfig.get().setBugPage(bugPage);
    }

    /**
     * Returns the bug regex for the history listing
     *
     * @return the regex that is looked for in history comments
     */
    public String getBugPattern() {
        return threadConfig.get().getBugPattern();
    }

    /**
     * Sets the bug regex for the history listing
     *
     * @param bugPattern the regex to search history comments
     */
    public void setBugPattern(String bugPattern) {
        threadConfig.get().setBugPattern(bugPattern);
    }

    /**
     * Returns the review(ARC) page for the history listing
     *
     * @return the URL string fragment preceeding the review page ID
     */
    public String getReviewPage() {
        return threadConfig.get().getReviewPage();
    }

    /**
     * Sets the review(ARC) page for the history listing
     *
     * @param reviewPage the URL fragment preceeding the review page ID
     */
    public void setReviewPage(String reviewPage) {
        threadConfig.get().setReviewPage(reviewPage);
    }

    /**
     * Returns the review(ARC) regex for the history listing
     *
     * @return the regex that is looked for in history comments
     */
    public String getReviewPattern() {
        return threadConfig.get().getReviewPattern();
    }

    /**
     * Sets the review(ARC) regex for the history listing
     *
     * @param reviewPattern the regex to search history comments
     */
    public void setReviewPattern(String reviewPattern) {
        threadConfig.get().setReviewPattern(reviewPattern);
    }

    public String getWebappLAF() {
        return threadConfig.get().getWebappLAF();
    }

    public void setWebappLAF(String laf) {
        threadConfig.get().setWebappLAF(laf);
    }

    public Configuration.RemoteSCM getRemoteScmSupported() {
        return threadConfig.get().getRemoteScmSupported();
    }

    public void setRemoteScmSupported(Configuration.RemoteSCM supported) {
        threadConfig.get().setRemoteScmSupported(supported);
    }

    public boolean isOptimizeDatabase() {
        return threadConfig.get().isOptimizeDatabase();
    }

    public void setOptimizeDatabase(boolean optimizeDatabase) {
        threadConfig.get().setOptimizeDatabase(optimizeDatabase);
    }

    public boolean isUsingLuceneLocking() {
        return threadConfig.get().isUsingLuceneLocking();
    }

    public void setUsingLuceneLocking(boolean useLuceneLocking) {
        threadConfig.get().setUsingLuceneLocking(useLuceneLocking);
    }

    public boolean isIndexVersionedFilesOnly() {
        return threadConfig.get().isIndexVersionedFilesOnly();
    }

    public void setIndexVersionedFilesOnly(boolean indexVersionedFilesOnly) {
        threadConfig.get().setIndexVersionedFilesOnly(indexVersionedFilesOnly);
    }

    public boolean isTagsEnabled() {
        return threadConfig.get().isTagsEnabled();
    }

    public void setTagsEnabled(boolean tagsEnabled) {
        threadConfig.get().setTagsEnabled(tagsEnabled);
    }

    public boolean isScopesEnabled() {
        return threadConfig.get().isScopesEnabled();
    }

    public void setScopesEnabled(boolean scopesEnabled) {
        threadConfig.get().setScopesEnabled(scopesEnabled);
    }

    public boolean isFoldingEnabled() {
        return threadConfig.get().isFoldingEnabled();
    }

    public void setFoldingEnabled(boolean foldingEnabled) {
        threadConfig.get().setFoldingEnabled(foldingEnabled);
    }

    public Date getDateForLastIndexRun() {
        return threadConfig.get().getDateForLastIndexRun();
    }

    public String getDatabaseDriver() {
        return threadConfig.get().getDatabaseDriver();
    }

    public void setDatabaseDriver(String databaseDriver) {
        threadConfig.get().setDatabaseDriver(databaseDriver);
    }

    public String getDatabaseUrl() {
        return threadConfig.get().getDatabaseUrl();
    }

    public void setDatabaseUrl(String databaseUrl) {
        threadConfig.get().setDatabaseUrl(databaseUrl);
    }

    public String getCTagsExtraOptionsFile() {
        return threadConfig.get().getCTagsExtraOptionsFile();
    }

    public void setCTagsExtraOptionsFile(String filename) {
        threadConfig.get().setCTagsExtraOptionsFile(filename);
    }

    public Set<String> getAllowedSymlinks() {
        return threadConfig.get().getAllowedSymlinks();
    }

    public void setAllowedSymlinks(Set<String> allowedSymlinks) {
        threadConfig.get().setAllowedSymlinks(allowedSymlinks);
    }

    /**
     * Return whether e-mail addresses should be obfuscated in the xref.
     * @return if we obfuscate emails
     */
    public boolean isObfuscatingEMailAddresses() {
        return threadConfig.get().isObfuscatingEMailAddresses();
    }

    /**
     * Set whether e-mail addresses should be obfuscated in the xref.
     * @param obfuscate should we obfuscate emails?
     */
    public void setObfuscatingEMailAddresses(boolean obfuscate) {
        threadConfig.get().setObfuscatingEMailAddresses(obfuscate);
    }

    /**
     * Should status.jsp print internal settings, like paths and database URLs?
     *
     * @return {@code true} if status.jsp should show the configuration,
     * {@code false} otherwise
     */
    public boolean isChattyStatusPage() {
        return threadConfig.get().isChattyStatusPage();
    }

    /**
     * Set whether status.jsp should print internal settings.
     *
     * @param chatty {@code true} if internal settings should be printed,
     * {@code false} otherwise
     */
    public void setChattyStatusPage(boolean chatty) {
        threadConfig.get().setChattyStatusPage(chatty);
    }

    public void setFetchHistoryWhenNotInCache(boolean nofetch) {
        threadConfig.get().setFetchHistoryWhenNotInCache(nofetch);
    }

    public boolean isFetchHistoryWhenNotInCache() {
        return threadConfig.get().isFetchHistoryWhenNotInCache();
    }

    public void setHandleHistoryOfRenamedFiles(boolean enable) {
        threadConfig.get().setHandleHistoryOfRenamedFiles(enable);
    }

    public boolean isHandleHistoryOfRenamedFiles() {
        return threadConfig.get().isHandleHistoryOfRenamedFiles();
    }

    public void setRevisionMessageCollapseThreshold(int threshold) {
        threadConfig.get().setRevisionMessageCollapseThreshold(threshold);
    }

    public int getRevisionMessageCollapseThreshold() {
        return threadConfig.get().getRevisionMessageCollapseThreshold();
    }

    public void setMaxSearchThreadCount(int count) {
        threadConfig.get().setMaxSearchThreadCount(count);
    }

    public int getMaxSearchThreadCount() {
        return threadConfig.get().getMaxSearchThreadCount();
    }

    /**
     * Read an configuration file and set it as the current configuration.
     *
     * @param file the file to read
     * @throws IOException if an error occurs
     */
    public void readConfiguration(File file) throws IOException {
        setConfiguration(Configuration.read(file));
    }

    /**
     * Write the current configuration to a file
     *
     * @param file the file to write the configuration into
     * @throws IOException if an error occurs
     */
    public void writeConfiguration(File file) throws IOException {
        threadConfig.get().write(file);
    }

    /**
     * Write the current configuration to a socket
     *
     * @param host the host address to receive the configuration
     * @param port the port to use on the host
     * @throws IOException if an error occurs
     */
    public void writeConfiguration(InetAddress host, int port) throws IOException {
        try (Socket sock = new Socket(host, port);
                XMLEncoder e = new XMLEncoder(sock.getOutputStream())) {
            e.writeObject(threadConfig.get());
        }
    }

    protected void writeConfiguration() throws IOException {
        writeConfiguration(configServerSocket.getInetAddress(), configServerSocket.getLocalPort());
    }

    /**
     * Generate a TreeMap of projects with corresponding repository information.
     *
     * Project with some repository information is considered as a repository
     * otherwise it is just a simple project.
     */
    private void generateProjectRepositoriesMap() throws IOException {
        repository_map.clear();
        for (RepositoryInfo r : configuration.getRepositories()) {
            Project proj;
            String repoPath;

            repoPath = getPathRelativeToSourceRoot(
                    new File(r.getDirectoryName()), 0);

            if ((proj = Project.getProject(repoPath)) != null) {
                List<RepositoryInfo> values = repository_map.get(proj);
                if (values == null) {
                    values = new ArrayList<>();
                    repository_map.put(proj, values);
                }
                values.add(r);
            }
        }
    }

    /**
     * Classifies projects and puts them in their groups.
     */
    private void populateGroups(Set<Group> groups, List<Project> projects) {
        if (projects == null || groups == null) {
            return;
        }
        for (Project project : projects) {
            // filterProjects only groups which match project's description
            Set<Group> copy = new TreeSet<>(groups);
            copy.removeIf(new Predicate<Group>() {
                @Override
                public boolean test(Group g) {
                    return !g.match(project);
                }
            });

            // add project to the groups
            for (Group group : copy) {
                if (repository_map.get(project) == null) {
                    group.addProject(project);
                } else {
                    group.addRepository(project);
                }
                project.addGroup(group);
            }
        }
    }
    
    /**
     * Sets the configuration and performs necessary actions.
     *
     * Mainly it classifies the projects in their groups and generates project -
     * repositories map
     *
     * @param configuration what configuration to use
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
        try {
            generateProjectRepositoriesMap();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot generate project - repository map", ex);
        }
        populateGroups(getGroups(), getProjects());
        register();
        HistoryGuru.getInstance().invalidateRepositories(
                configuration.getRepositories());
    }

    public void setConfiguration(Configuration configuration, List<String> subFileList) {
        this.configuration = configuration;
        try {
            generateProjectRepositoriesMap();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot generate project - repository map", ex);
        }
        populateGroups(getGroups(), getProjects());
        register();
        HistoryGuru.getInstance().invalidateRepositories(
                configuration.getRepositories(), subFileList);
    }

    public Configuration getConfiguration() {
        return this.threadConfig.get();
    }
    private ServerSocket configServerSocket;

    /**
     * Try to stop the configuration listener thread
     */
    public void stopConfigurationListenerThread() {
        IOUtils.close(configServerSocket);
    }

    /**
     * Start a thread to listen on a socket to receive new configurations to
     * use.
     *
     * @param endpoint The socket address to listen on
     * @return true if the endpoint was available (and the thread was started)
     */
    public boolean startConfigurationListenerThread(SocketAddress endpoint) {
        boolean ret = false;

        try {
            configServerSocket = new ServerSocket();
            configServerSocket.bind(endpoint);
            ret = true;
            final ServerSocket sock = configServerSocket;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 13);
                    while (!sock.isClosed()) {
                        try (Socket s = sock.accept();
                                BufferedInputStream in = new BufferedInputStream(s.getInputStream())) {
                            bos.reset();
                            LOGGER.log(Level.FINE, "OpenGrok: Got request from {0}",
                                    s.getInetAddress().getHostAddress());
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                bos.write(buf, 0, len);
                            }
                            buf = bos.toByteArray();
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.log(Level.FINE, "new config:{0}", new String(buf));
                            }
                            Object obj;
                            try (XMLDecoder d = new XMLDecoder(new ByteArrayInputStream(buf))) {
                                obj = d.readObject();
                            }

                            if (obj instanceof Configuration) {
                                //force timestamp to update itself upon new config arrival
                                ((Configuration) obj).refreshDateForLastIndexRun();
                                setConfiguration((Configuration) obj);
                                LOGGER.log(Level.INFO, "Configuration updated: {0}",
                                        configuration.getSourceRoot());
                            }
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Error reading config file: ", e);
                        } catch (RuntimeException e) {
                            LOGGER.log(Level.SEVERE, "Error parsing config file: ", e);
                        }
                    }
                }
            }, "conigurationListener");
            t.start();
        } catch (UnknownHostException ex) {
            LOGGER.log(Level.FINE, "Problem resolving sender: ", ex);
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "I/O error when waiting for config: ", ex);
        }

        if (!ret && configServerSocket != null) {
            IOUtils.close(configServerSocket);
        }

        return ret;
    }

    private Thread watchDogThread;
    private WatchService watchDogWatcher;
    public static final int THREAD_SLEEP_TIME = 2000;

    /**
     * Starts a watch dog service for a directory. It automatically reloads the
     * AuthorizationFramework if there was a change.
     *
     * You can control start of this service by context-parameter in web.xml
     * param-name: enableAuthorizationWatchDog
     *
     * @param directory root directory for plugins
     */
    public void startWatchDogService(File directory) {
        if (directory == null || !directory.isDirectory() || !directory.canRead()) {
            LOGGER.log(Level.INFO, "Watch dog cannot be started - invalid directory: {0}", directory);
            return;
        }
        watchDogThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    watchDogWatcher = FileSystems.getDefault().newWatchService();
                    Path dir = Paths.get(directory.getAbsolutePath());

                    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                            // attach monitor
                            d.register(watchDogWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                            return CONTINUE;
                        }
                    });
                    
                    LOGGER.log(Level.INFO, "Watch dog started {0}", directory);
                    while (!Thread.currentThread().isInterrupted()) {
                        final WatchKey key;
                        try {
                            key = watchDogWatcher.take();
                        } catch (ClosedWatchServiceException x) {
                            break;
                        }
                        boolean reload = false;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            final WatchEvent.Kind<?> kind = event.kind();

                            if (kind == ENTRY_CREATE) {
                                reload = true;
                            } else if (kind == ENTRY_DELETE) {
                                reload = true;
                            } else if (kind == ENTRY_MODIFY) {
                                reload = true;
                            }
                        }
                        if (reload) {
                            Thread.sleep(THREAD_SLEEP_TIME); // experimental wait if file is being written right now
                            AuthorizationFramework.getInstance().reload();
                        }
                        if (!key.reset()) {
                            break;
                        }
                    }
                } catch (InterruptedException | IOException ex) {
                    Thread.currentThread().interrupt();
                }
                LOGGER.log(Level.INFO, "Watchdog finishing (exiting)");
            }
        }, "watchDogService");
        watchDogThread.start();
    }

    /**
     * Stops the watch dog service.
     */
    public void stopWatchDogService() {
        if (watchDogWatcher != null) {
            try {
                watchDogWatcher.close();
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, "Cannot close WatchDogService: ", ex);
            }
        }
        if (watchDogThread != null) {
            watchDogThread.interrupt();
            try {
                watchDogThread.join();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.INFO, "Cannot join WatchDogService thread: ", ex);
            }
        }
    }
}
