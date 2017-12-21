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
  * Copyright (c) 2006, 2017, Oracle and/or its affiliates. All rights reserved.
  * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
  */
package org.opensolaris.opengrok.configuration;

import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensolaris.opengrok.authorization.AuthorizationFramework;
import org.opensolaris.opengrok.authorization.AuthorizationStack;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.Filter;
import org.opensolaris.opengrok.index.IgnoredNames;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.IOUtils;
import org.opensolaris.opengrok.util.XmlEofInputStream;
import org.opensolaris.opengrok.web.Statistics;
import org.opensolaris.opengrok.web.Util;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.opensolaris.opengrok.configuration.Configuration.makeXMLStringAsConfiguration;

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

    private final Map<Project, List<RepositoryInfo>> repository_map = new ConcurrentHashMap<>();
    private final Map<Project, Set<Group>> project_group_map = new TreeMap<>();
    private final Map<String, SearcherManager> searcherManagerMap = new ConcurrentHashMap<>();

    private String configHost;
    private int configPort;

    public static final String MESSAGES_MAIN_PAGE_TAG = "main";
    /*
    initial capacity - default 16
    initial load factor - default 0.75f
    initial concurrency level - number of concurrently updating threads (default 16)
        - just two (the timer, configuration listener) so set it to small value
    */
    private final ConcurrentMap<String, SortedSet<Message>> tagMessages = new ConcurrentHashMap<>(16, 0.75f, 5);
    private int messagesInTheSystem = 0;

    private Statistics statistics = new Statistics();
    
    private static IndexTimestamp indexTime = new IndexTimestamp();

    /**
     * Instance of authorization framework.
     */
    private AuthorizationFramework authFramework;

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
     * singleton anti-pattern.
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

    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public void setLastEditedDisplayMode(boolean lastEditedDisplayMode) {
        threadConfig.get().setLastEditedDisplayMode(lastEditedDisplayMode);
    }

    public boolean isLastEditedDisplayMode() {
        return threadConfig.get().isLastEditedDisplayMode();
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
     * @throws IOException If an IO error occurs
     * @throws FileNotFoundException If the file is not relative to source root
     * @return Path relative to source root
     */
    public String getPathRelativeToSourceRoot(File file) throws IOException {
        return getPathRelativeToSourceRoot(file, 0);
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
        
        if(sourceRoot == null){
            throw new FileNotFoundException("Source Root Not Found");
        }
        
        if (canonicalPath.startsWith(sourceRoot)) {
            return canonicalPath.substring(sourceRoot.length() + stripCount);
        }
        for (String allowedSymlink : getAllowedSymlinks()) {
            String allowedTarget = new File(allowedSymlink).getCanonicalPath();
            if (canonicalPath.startsWith(allowedTarget)) {
                return allowedSymlink.substring(sourceRoot.length()) +
                       canonicalPath.substring(allowedTarget.length()
                        + stripCount);
            }
        }
        throw new FileNotFoundException("Failed to resolve [" + canonicalPath
                + "] relative to source root [" + sourceRoot + "]");
    }

    /**
     * Do we have any projects ?
     *
     * @return true if we have projects
     */
    public boolean hasProjects() {
        return (this.isProjectsEnabled() && getProjects().size() > 0);
    }

    /**
     * Get list of projects.
     *
     * @return a list containing all of the projects
     */
    public List<Project> getProjectList() {
        return new ArrayList<Project>(threadConfig.get().getProjects().values());
    }

    /**
     * Get project map.
     *
     * @return a Map with all of the projects
     */
    public Map<String,Project> getProjects() {
        return threadConfig.get().getProjects();
    }

    /**
     * Get names of all projects.
     *
     * @return a list containing names of all projects.
     */
    public List<String> getProjectNames() {
        return getProjectList().stream().
            map(Project::getName).collect(Collectors.toList());
    }

    /**
     * Set the list of the projects
     *
     * @param projects the map of projects to use
     */
    public void setProjects(Map<String,Project> projects) {
        if (projects != null) {
            populateGroups(getGroups(), new TreeSet<Project>(projects.values()));
        }
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
        populateGroups(groups, new TreeSet<Project>(getProjects().values()));
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

    /**
     * Get the name of the mandoc program in use
     *
     * @return the name of the mandoc program in use or {@code null}
     */
    public String getMandoc() {
        return threadConfig.get().getMandoc();
    }

    /**
     * Specify the mandoc program to use
     *
     * @param value the mandoc program to use or {@code null}
     */
    public void setMandoc(String value) {
        threadConfig.get().setMandoc(value);
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

    // cache these tests instead of rerunning them for every call
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
     * Set the list of repositories.
     *
     * @param repositories the repositories to use
     */
    public void setRepositories(List<RepositoryInfo> repositories) {
        threadConfig.get().setRepositories(repositories);
    }
    
    public void removeRepositories() {
        threadConfig.get().setRepositories(null);
    }
    
    /**
     * Search through the directory for repositories and use the result to replace
     * the lists of repositories in both RuntimeEnvironment/Configuration and HistoryGuru.
     *
     * @param dir the root directory to start the search in
     */
    public void setRepositories(String dir) {
        List<RepositoryInfo> repos = new ArrayList<>(HistoryGuru.getInstance().
                addRepositories(new File[]{new File(dir)},
                RuntimeEnvironment.getInstance().getIgnoredNames()));
        RuntimeEnvironment.getInstance().setRepositories(repos);
    }

    /**
     * Add repositories to the list.
     * @param repositories 
     */
    public void addRepositories(List<RepositoryInfo> repositories) {
        threadConfig.get().addRepositories(repositories);
    }

    /**
     * Set the projects that are specified to be the default projects to use.
     * The default projects are the projects you will search (from the web
     * application) if the page request didn't contain the cookie..
     *
     * @param defaultProject The default project to use
     */
    public void setDefaultProjects(Set<Project> defaultProject) {
        threadConfig.get().setDefaultProjects(defaultProject);
    }

    /**
     * Get the projects that are specified to be the default projects to use.
     * The default projects are the projects you will search (from the web
     * application) if the page request didn't contain the cookie..
     *
     * @return the default projects (may be null if not specified)
     */
    public Set<Project> getDefaultProjects() {
        return threadConfig.get().getDefaultProjects();
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

    public boolean isAuthorizationWatchdog() {
        return threadConfig.get().isAuthorizationWatchdogEnabled();
    }

    public void setAuthorizationWatchdog(boolean authorizationWatchdogEnabled) {
        threadConfig.get().setAuthorizationWatchdogEnabled(authorizationWatchdogEnabled);
    }

    public AuthorizationStack getPluginStack() {
        return threadConfig.get().getPluginStack();
    }

    public void setPluginStack(AuthorizationStack pluginStack) {
        threadConfig.get().setPluginStack(pluginStack);
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
     * fully qualified classname.
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

    public boolean isProjectsEnabled() {
        return threadConfig.get().isProjectsEnabled();
    }

    public void setProjectsEnabled(boolean projectsEnabled) {
        threadConfig.get().setProjectsEnabled(projectsEnabled);
    }

    public boolean isFoldingEnabled() {
        return threadConfig.get().isFoldingEnabled();
    }

    public void setFoldingEnabled(boolean foldingEnabled) {
        threadConfig.get().setFoldingEnabled(foldingEnabled);
    }

    public Date getDateForLastIndexRun() {
        return indexTime.getDateForLastIndexRun();
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

    public int getCurrentIndexedCollapseThreshold() {
        return threadConfig.get().getCurrentIndexedCollapseThreshold();
    }

    public void setCurrentIndexedCollapseThreshold(int currentIndexedCollapseThreshold) {
        threadConfig.get().getCurrentIndexedCollapseThreshold();
    }

    public int getGroupsCollapseThreshold() {
        return threadConfig.get().getGroupsCollapseThreshold();
    }

    // The config host/port are not necessary to be present in the configuration
    // (so that when -U option of the indexer is omitted, the config will not
    // be sent to the webapp) so store them only in the RuntimeEnvironment.
    public void setConfigHost(String host) {
        configHost = host;
    }

    public String getConfigHost() {
        return configHost;
    }

    public void setConfigPort(int port) {
        configPort = port;
    }

    public int getConfigPort() {
        return configPort;
    }

    public boolean isHistoryEnabled() {
        return threadConfig.get().isHistoryEnabled();
    }

    public void setHistoryEnabled(boolean flag) {
        threadConfig.get().setHistoryEnabled(flag);
    }

    public boolean getDisplayRepositories() {
        return threadConfig.get().getDisplayRepositories();
    }

    public void setDisplayRepositories(boolean flag) {
        threadConfig.get().setDisplayRepositories(flag);
    }

    public boolean getListDirsFirst() {
        return threadConfig.get().getListDirsFirst();
    }

    public void setListDirsFirst(boolean flag) {
        threadConfig.get().setListDirsFirst(flag);
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
    public void writeConfiguration(String host, int port) throws IOException {
        Message m = Message.createMessage("config");
        m.addTag("setconf");
        m.addTag("reindex");
        m.setText(configuration.getXMLRepresentationAsString());
        try {
            m.validate();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        m.write(host, port);
    }

    public void writeConfiguration(InetAddress hostAddr, int port) throws IOException {
        writeConfiguration(hostAddr.getHostAddress(), port);
    }

    protected void writeConfiguration() throws IOException {
        writeConfiguration(configServerSocket.getInetAddress(), configServerSocket.getLocalPort());
    }

    /**
     * Send message to webapp to refresh SearcherManagers for given projects.
     * This is used for partial reindex.
     *
     * @param subFiles list of directories to refresh corresponding SearcherManagers
     * @param host the host address to receive the configuration
     * @param port the port to use on the host
     * @throws IOException if an error occurs
     */
    public void signalTorefreshSearcherManagers(List<String> subFiles, String host, int port) throws IOException {
        Message m = Message.createMessage("refresh");
        for (String proj : subFiles) {
            // subFile entries start with path separator so get basename
            // to convert them to project names.
            m.addTag(new File(proj).getName());
        }
        m.write(host, port);
    }

    /**
     * Generate a TreeMap of projects with corresponding repository information.
     *
     * Project with some repository information is considered as a repository
     * otherwise it is just a simple project.
     */
    private void generateProjectRepositoriesMap() throws IOException {
        repository_map.clear();
        for (RepositoryInfo r : getRepositories()) {
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
     * @param groups groups to update
     * @param projects projects to classify
     */
    public void populateGroups(Set<Group> groups, Set<Project> projects) {
        if (projects == null || groups == null) {
            return;
        }
        for (Project project : projects) {
            // filterProjects only groups which match project's description
            Set<Group> copy = Group.matching(project, groups);

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
        setConfiguration(configuration, null);
    }

    public void setConfiguration(Configuration configuration, List<String> subFileList) {
        this.configuration = configuration;
        // HistoryGuru constructor uses environment properties so register()
        // needs to be called first.
        // Another case where the singleton anti-pattern bites us in the back.
        register();
        
        HistoryGuru histGuru = HistoryGuru.getInstance();
        
        try {
            generateProjectRepositoriesMap();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot generate project - repository map", ex);
        }
        
        populateGroups(getGroups(), new TreeSet<>(getProjects().values()));
        
        // Set the working repositories in HistoryGuru.
        if (subFileList != null) {
            histGuru.invalidateRepositories(
                configuration.getRepositories(), subFileList);
        } else {
            histGuru.invalidateRepositories(
                configuration.getRepositories());
        }
        // The invalidation of repositories above might have excluded some
        // repositories in HistoryGuru so the configuration needs to reflect that.
        configuration.setRepositories(new ArrayList<>(histGuru.getRepositories()));
    }

    public Configuration getConfiguration() {
        return this.threadConfig.get();
    }

    private Timer expirationTimer;

    private static SortedSet<Message> emptyMessageSet(SortedSet<Message> toRet) {
        return toRet == null ? new TreeSet<>() : toRet;
    }

    /**
     * Get the default set of messages for the main tag.
     *
     * @return set of messages
     */
    public SortedSet<Message> getMessages() {
        if (expirationTimer == null) {
            expireMessages();
        }
        return emptyMessageSet(tagMessages.get(MESSAGES_MAIN_PAGE_TAG));
    }

    /**
     * Get the set of messages for the arbitrary tag
     *
     * @param tag the message tag
     * @return set of messages
     */
    public SortedSet<Message> getMessages(String tag) {
        if (expirationTimer == null) {
            expireMessages();
        }
        return emptyMessageSet(tagMessages.get(tag));
    }

    /**
     * Add a message to the application.
     * Also schedules a expiration timer to remove this message after its expiration.
     *
     * @param m the message
     */
    public void addMessage(Message m) {
        if (!canAcceptMessage(m)) {
            return;
        }

        if (expirationTimer == null) {
            expireMessages();
        }

        boolean added = false;
        for (String tag : m.getTags()) {
            if (!tagMessages.containsKey(tag)) {
                tagMessages.put(tag, new ConcurrentSkipListSet<>());
            }
            if (tagMessages.get(tag).add(m)) {
                messagesInTheSystem++;
                added = true;
            }
        }

        if (added) {
            if (expirationTimer != null) {
                expirationTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        expireMessages();
                    }
                }, new Date(m.getExpiration().getTime() + 10));
            }
        }
    }

    /**
     * Immediately remove all messages in the application.
     */
    public void removeAllMessages() {
        tagMessages.clear();
        messagesInTheSystem = 0;
    }

    /**
     * Remove all messages containing at least on of the tags.
     *
     * @param tags set of tags
     */
    public void removeAnyMessage(Set<String> tags) {
        removeAnyMessage(new Predicate<Message>() {
            @Override
            public boolean test(Message t) {
                return t.hasAny(tags);
            }
        });
    }

    /**
     * Remove messages which have expired.
     */
    private void expireMessages() {
        removeAnyMessage(new Predicate<Message>() {
            @Override
            public boolean test(Message t) {
                return t.isExpired();
            }
        });
    }

    /**
     * Generic function to remove any message according to the result of the
     * predicate.
     *
     * @param predicate the testing predicate
     */
    private void removeAnyMessage(Predicate<Message> predicate) {
        int size;
        for (Map.Entry<String, SortedSet<Message>> set : tagMessages.entrySet()) {
            size = set.getValue().size();
            set.getValue().removeIf(predicate);
            messagesInTheSystem -= size - set.getValue().size();
        }

        tagMessages.entrySet().removeIf(new Predicate<Map.Entry<String, SortedSet<Message>>>() {
            @Override
            public boolean test(Map.Entry<String, SortedSet<Message>> t) {
                return t.getValue().isEmpty();
            }
        });
    }

    /**
     * Test if the application can receive this messages.
     *
     * @param m the message
     * @return true if it can
     */
    public boolean canAcceptMessage(Message m) {
        return messagesInTheSystem < getMessageLimit() && !m.isExpired();
    }

    /**
     * Get the maximum number of messages in the application
     *
     * @see #getMessagesInTheSystem()
     * @return the number
     */
    public int getMessageLimit() {
        return threadConfig.get().getMessageLimit();
    }

    /**
     * Set the maximum number of messages in the application
     *
     * @see #getMessagesInTheSystem()
     * @param limit the new limit
     */
    public void setMessageLimit(int limit) {
        threadConfig.get().setMessageLimit(limit);
    }

    /**
     * Return number of messages present in the hash map.
     *
     * DISCLAIMER: This is not the real number of unique messages in the
     * application because the same message is duplicated for all of the tags in
     * the map.
     *
     * This is just a cheap counter to indicate how many messages are stored in
     * total under different tags.
     *
     * Also one can bypass the counter by not calling
     * {@link #addMessage(Message)}
     *
     * @return number of messages
     */
    public int getMessagesInTheSystem() {
        if (expirationTimer == null) {
            expireMessages();
        }
        return messagesInTheSystem;
    }

    /**
     * Dump statistics in JSON format into the file specified in configuration.
     *
     * @throws IOException
     */
    public void saveStatistics() throws IOException {
        if (getConfiguration().getStatisticsFilePath() == null) {
            throw new FileNotFoundException("Statistics file is not set (null)");
        }
        saveStatistics(new File(getConfiguration().getStatisticsFilePath()));
    }

    /**
     * Dump statistics in JSON format into a file.
     *
     * @param out the output file
     * @throws IOException
     */
    public void saveStatistics(File out) throws IOException {
        if (out == null) {
            throw new FileNotFoundException("Statistics file is not set (null)");
        }
        try (FileOutputStream ofstream = new FileOutputStream(out)) {
            saveStatistics(ofstream);
        }
    }

    /**
     * Dump statistics in JSON format into an output stream.
     *
     * @param out the output stream
     * @throws IOException
     */
    public void saveStatistics(OutputStream out) throws IOException {
        out.write(Util.statisticToJson(getStatistics()).toJSONString().getBytes());
    }

    /**
     * Load statistics from JSON file specified in configuration.
     *
     * @throws IOException
     * @throws ParseException
     */
    public void loadStatistics() throws IOException, ParseException {
        if (getConfiguration().getStatisticsFilePath() == null) {
            throw new FileNotFoundException("Statistics file is not set (null)");
        }
        loadStatistics(new File(getConfiguration().getStatisticsFilePath()));
    }

    /**
     * Load statistics from JSON file.
     *
     * @param in the file with json
     * @throws IOException
     * @throws ParseException
     */
    public void loadStatistics(File in) throws IOException, ParseException {
        if (in == null) {
            throw new FileNotFoundException("Statistics file is not set (null)");
        }
        try (FileInputStream ifstream = new FileInputStream(in)) {
            loadStatistics(ifstream);
        }
    }

    /**
     * Load statistics from an input stream.
     *
     * @param in the file with json
     * @throws IOException
     * @throws ParseException
     */
    public void loadStatistics(InputStream in) throws IOException, ParseException {
        try (InputStreamReader iReader = new InputStreamReader(in)) {
            JSONParser jsonParser = new JSONParser();
            setStatistics(Util.jsonToStatistics((JSONObject) jsonParser.parse(iReader)));
        }
    }

    /**
     * Return the authorization framework used in this environment.
     *
     * @return the framework
     */
    synchronized public AuthorizationFramework getAuthorizationFramework() {
        if (authFramework == null) {
            authFramework = new AuthorizationFramework(getPluginDirectory(), getPluginStack());
        }
        return authFramework;
    }

    /**
     * Set the authorization framework for this environment. Unload all
     * previously load plugins.
     *
     * @param fw the new framework
     */
    synchronized public void setAuthorizationFramework(AuthorizationFramework fw) {
        if (this.authFramework != null) {
            this.authFramework.removeAll();
        }
        this.authFramework = fw;
    }

    private ServerSocket configServerSocket;

    /**
     * Try to stop the configuration listener thread
     */
    public void stopConfigurationListenerThread() {
        IOUtils.close(configServerSocket);
    }

    private Thread configurationListenerThread;

    /**
     * Set configuration from a message. The message could have come from the
     * Indexer (in which case some extra work is needed) or is it just a request
     * to set new configuration in place.
     *
     * @param m message containing the configuration
     * @param reindex is the message result of reindex
     * @see #applyConfig(org.opensolaris.opengrok.configuration.Configuration,
     * boolean) applyConfig(config, reindex)
     */
    public void applyConfig(Message m, boolean reindex) {
        Configuration config;
        try {
            config = makeXMLStringAsConfiguration(m.getText());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Configuration decoding failed" + ex);
            return;
        }

        applyConfig(config, reindex);
    }

    /**
     * Set configuration from the incoming parameter. The configuration could
     * have come from the Indexer (in which case some extra work is needed) or
     * is it just a request to set new configuration in place.
     *
     * @param config the incoming configuration
     * @param reindex is the message result of reindex
     *
     */
    public void applyConfig(Configuration config, boolean reindex) {

        setConfiguration(config);
        LOGGER.log(Level.INFO, "Configuration updated: {0}",
                configuration.getSourceRoot());

        if (reindex) {
            // We are assuming that each update of configuration
            // means reindex. If dedicated thread is introduced
            // in the future solely for the purpose of getting
            // the event of reindex, the 2 calls below should
            // be moved there.
            refreshSearcherManagerMap();
            maybeRefreshIndexSearchers();
            // Force timestamp to update itself upon new config arrival.
            refreshDateForLastIndexRun();
        }

        // start/stop the watchdog if necessarry
        if (isAuthorizationWatchdog() && config.getPluginDirectory() != null) {
            startWatchDogService(new File(config.getPluginDirectory()));
        } else {
            stopWatchDogService();
        }

        // set the new plugin directory and reload the authorization framework
        getAuthorizationFramework().setPluginDirectory(config.getPluginDirectory());
        getAuthorizationFramework().setStack(config.getPluginStack());
        getAuthorizationFramework().reload();
    }

    public void setIndexTimestamp() throws IOException {
        indexTime.stamp();
    }

    public void refreshDateForLastIndexRun() {
        indexTime.refreshDateForLastIndexRun();
    }

    /**
     * Start a thread to listen on a socket to receive new messages.
     * The messages can contain various commands for the webapp, including
     * upload of new configuration.
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
            configurationListenerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 15);
                    while (!sock.isClosed()) {
                        try (Socket s = sock.accept();
                                BufferedInputStream in = new BufferedInputStream(new XmlEofInputStream(s.getInputStream()));
                                OutputStream output = s.getOutputStream()) {
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

                            if (obj instanceof Message) {
                                Message m = ((Message) obj);
                                handleMessage(m, output);
                            }
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Error reading config file: ", e);
                        } catch (RuntimeException e) {
                            LOGGER.log(Level.SEVERE, "Error parsing config file: ", e);
                        }
                    }
                }
            }, "configurationListener");
            configurationListenerThread.start();
        } catch (UnknownHostException ex) {
            LOGGER.log(Level.WARNING, "Problem resolving sender: ", ex);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "I/O error when waiting for config: ", ex);
        }

        if (!ret && configServerSocket != null) {
            IOUtils.close(configServerSocket);
        }

        return ret;
    }

    /**
     * Handle incoming message.
     *
     * @param m message
     * @param output output stream for errors or success
     * @throws IOException
     */
    protected void handleMessage(Message m, final OutputStream output) throws IOException {
        byte[] out;
        if (!canAcceptMessage(m)) {
            LOGGER.log(Level.WARNING, "Message dropped: {0} - too many messages in the system",
                    m.getTags());
            output.write(Message.MESSAGE_LIMIT);
        }

        try {
            out = m.apply(RuntimeEnvironment.getInstance());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    String.format("Message dropped: {0} - message error", m.getTags()),
                    ex);
            output.write(Message.MESSAGE_ERROR);
            output.write(ex.getMessage().getBytes());
            return;
        }

        LOGGER.log(Level.FINER, "Message received: {0}",
                m.getTags());
        LOGGER.log(Level.FINER, "Messages in the system: {0}",
                getMessagesInTheSystem());

        output.write(Message.MESSAGE_OK);
        if (out != null) {
            output.write(out);
        }
    }

    private Thread watchDogThread;
    private WatchService watchDogWatcher;
    public static final int THREAD_SLEEP_TIME = 2000;

    /**
     * Starts a watch dog service for a directory. It automatically reloads the
     * AuthorizationFramework if there was a change in <b>real-time</b>.
     * Suitable for plugin development.
     *
     * You can control start of this service by a configuration parameter
     * {@link Configuration#authorizationWatchdogEnabled}
     *
     * @param directory root directory for plugins
     */
    public void startWatchDogService(File directory) {
        stopWatchDogService();
        if (directory == null || !directory.isDirectory() || !directory.canRead()) {
            LOGGER.log(Level.INFO, "Watch dog cannot be started - invalid directory: {0}", directory);
            return;
        }
        LOGGER.log(Level.INFO, "Starting watchdog in: {0}", directory);
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
                            LOGGER.log(Level.FINEST, "Watchdog registering {0}", d);
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
                            getAuthorizationFramework().reload();
                        }
                        if (!key.reset()) {
                            break;
                        }
                    }
                } catch (InterruptedException | IOException ex) {
                    LOGGER.log(Level.FINEST, "Watchdog finishing (exiting)", ex);
                    Thread.currentThread().interrupt();
                }
                LOGGER.log(Level.FINER, "Watchdog finishing (exiting)");
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
                LOGGER.log(Level.WARNING, "Cannot close WatchDogService: ", ex);
            }
        }
        if (watchDogThread != null) {
            watchDogThread.interrupt();
            try {
                watchDogThread.join();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.WARNING, "Cannot join WatchDogService thread: ", ex);
            }
        }
        LOGGER.log(Level.INFO, "Watchdog stoped");
    }

    public void startExpirationTimer() {
        if (expirationTimer != null) {
            stopExpirationTimer();
        }
        expirationTimer = new Timer("expirationThread");
        expireMessages();
    }

    /**
     * Stops the watch dog service.
     */
    public void stopExpirationTimer() {
        if (expirationTimer != null) {
            expirationTimer.cancel();
            expirationTimer = null;
        }
    }

    private Thread indexReopenThread;

    private void maybeRefreshSearcherManager(SearcherManager sm) {
        try {
            sm.maybeRefresh();
        }  catch (AlreadyClosedException ex) {
            // This is a case of removed project.
            // See refreshSearcherManagerMap() for details.
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "maybeRefresh failed", ex);
        }
    }

    public void maybeRefreshIndexSearchers(Set<String> projects) {
        for (String proj : projects) {
            if (searcherManagerMap.containsKey(proj)) {
                maybeRefreshSearcherManager(searcherManagerMap.get(proj));
            }
        }
    }

    public void maybeRefreshIndexSearchers() {
        for (Map.Entry<String, SearcherManager> entry : searcherManagerMap.entrySet()) {
            maybeRefreshSearcherManager(entry.getValue());
        }
    }

    /**
     * Get IndexSearcher for given project.
     * Each IndexSearcher is born from a SearcherManager object. There is
     * one SearcherManager for every project.
     * This schema makes it possible to reuse IndexSearcher/IndexReader objects
     * so the heavy lifting (esp. system calls) performed in FSDirectory
     * and DirectoryReader happens only once for a project.
     * The caller has to make sure that the IndexSearcher is returned back
     * to the SearcherManager. This is done with returnIndexSearcher().
     * The return of the IndexSearcher should happen only after the search
     * result data are read fully.
     *
     * @param proj project
     * @return SearcherManager for given project
     */
    public SuperIndexSearcher getIndexSearcher(String proj) throws IOException {
        SearcherManager mgr = searcherManagerMap.get(proj);
        SuperIndexSearcher searcher = null;

        if (mgr == null) {
            File indexDir = new File(getDataRootPath(), IndexDatabase.INDEX_DIR);

            try {
                Directory dir = FSDirectory.open(new File(indexDir, proj).toPath());
                mgr = new SearcherManager(dir, new ThreadpoolSearcherFactory());
                searcherManagerMap.put(proj, mgr);
                searcher = (SuperIndexSearcher) mgr.acquire();
                searcher.setSearcherManager(mgr);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                    "cannot construct IndexSearcher for project " + proj, ex);
            }
        } else {
            searcher = (SuperIndexSearcher) mgr.acquire();
            searcher.setSearcherManager(mgr);
        }

        return searcher;
    }

    /**
     * After new configuration is put into place, the set of projects might
     * change so we go through the SearcherManager objects and close those where
     * the corresponding project is no longer present.
     */
    public void refreshSearcherManagerMap() {
        ArrayList<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, SearcherManager> entry : searcherManagerMap.entrySet()) {
            // If a project is gone, close the corresponding SearcherManager
            // so that it cannot produce new IndexSearcher objects.
            if (!getProjectNames().contains(entry.getKey())) {
                try {
                    LOGGER.log(Level.FINE,
                        "closing SearcherManager for project" + entry.getKey());
                    entry.getValue().close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE,
                        "cannot close SearcherManager for project" + entry.getKey(), ex);
                }
                toRemove.add(entry.getKey());
            }
        }

        for (String proj : toRemove) {
            searcherManagerMap.remove(proj);
        }
    }

    /**
     * Return collection of IndexReader objects as MultiReader object
     * for given list of projects.
     * The caller is responsible for releasing the IndexSearcher objects
     * so we add them to the map.
     *
     * @param projects list of projects
     * @param searcherList each SuperIndexSearcher produced will be put into this list
     * @return MultiReader for the projects
     */
    public MultiReader getMultiReader(SortedSet<String> projects,
        ArrayList<SuperIndexSearcher> searcherList) {

        IndexReader[] subreaders = new IndexReader[projects.size()];
        int ii = 0;

        // TODO might need to rewrite to Project instead of
        // String , need changes in projects.jspf too
        for (String proj : projects) {
            try {
                SuperIndexSearcher searcher = RuntimeEnvironment.getInstance().getIndexSearcher(proj);
                subreaders[ii++] = searcher.getIndexReader();
                searcherList.add(searcher);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                    "cannot get IndexReader for project " + proj, ex);
                return null;
            } catch (NullPointerException ex) {
                LOGGER.log(Level.SEVERE,
                    "cannot get IndexReader for project " + proj, ex);
                return null;
            }
        }
        MultiReader multiReader = null;
        try {
            multiReader = new MultiReader(subreaders, true);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                "cannot construct MultiReader for set of projects", ex);
        }
        return multiReader;
    }
}
