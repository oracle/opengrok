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
  * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
  * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
  */
package org.opengrok.indexer.configuration;

import static org.opengrok.indexer.configuration.Configuration.makeXMLStringAsConfiguration;
import static org.opengrok.indexer.util.ClassUtil.getFieldValue;
import static org.opengrok.indexer.util.ClassUtil.setFieldValue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.authorization.AuthorizationStack;
import org.opengrok.indexer.history.HistoryGuru;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.index.Filter;
import org.opengrok.indexer.index.IgnoredNames;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.CtagsUtil;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.PathUtils;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.Statistics;
import org.opengrok.indexer.web.messages.Message;
import org.opengrok.indexer.web.messages.MessagesContainer;
import org.opengrok.indexer.web.messages.MessagesContainer.AcceptedMessage;

/**
 * The RuntimeEnvironment class is used as a placeholder for the current
 * configuration this execution context (classloader) is using.
 */
public final class RuntimeEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeEnvironment.class);

    /** {@code "/source"} + {@link Prefix#SEARCH_R} + {@code "?"} */
    private static final String URL_PREFIX = "/source" + Prefix.SEARCH_R + "?";

    private Configuration configuration;
    private ReentrantReadWriteLock configLock;
    private static final RuntimeEnvironment instance = new RuntimeEnvironment();
    private static ExecutorService historyExecutor = null;
    private static ExecutorService historyRenamedExecutor = null;
    private static ExecutorService searchExecutor = null;

    private final Map<Project, List<RepositoryInfo>> repository_map = new ConcurrentHashMap<>();
    private final Map<String, SearcherManager> searcherManagerMap = new ConcurrentHashMap<>();

    private String configURI;
    private Statistics statistics = new Statistics();
    public IncludeFiles includeFiles = new IncludeFiles();
    private final MessagesContainer messagesContainer = new MessagesContainer();

    private static final IndexTimestamp indexTime = new IndexTimestamp();

    /**
     * Stores a transient value when
     * {@link #setCtags(java.lang.String)} is called -- i.e. the
     * value is not mediated to {@link Configuration}.
     */
    private String ctags;
    /**
     * Stores a transient value when
     * {@link #setMandoc(java.lang.String)} is called -- i.e. the
     * value is not mediated to {@link Configuration}.
     */
    private String mandoc;

    private transient File dtagsEftar = null;

    /**
     * Creates a new instance of RuntimeEnvironment. Private to ensure a
     * singleton anti-pattern.
     */
    private RuntimeEnvironment() {
        configuration = new Configuration();
        configLock = new ReentrantReadWriteLock();
        watchDog = new WatchDogService();
    }

    /** Instance of authorization framework.*/
    private AuthorizationFramework authFramework;

    /* Get thread pool used for top-level repository history generation. */
    public static synchronized ExecutorService getHistoryExecutor() {
        if (historyExecutor == null) {
            historyExecutor = Executors.newFixedThreadPool(getInstance().getHistoryParallelism(),
                    runnable -> {
                        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                        thread.setName("history-handling-" + thread.getId());
                        return thread;
                    });
        }

        return historyExecutor;
    }

    /* Get thread pool used for history generation of renamed files. */
    public static synchronized ExecutorService getHistoryRenamedExecutor() {
        if (historyRenamedExecutor == null) {
            historyRenamedExecutor = Executors.newFixedThreadPool(getInstance().getHistoryRenamedParallelism(),
                    runnable -> {
                        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                        thread.setName("renamed-handling-" + thread.getId());
                        return thread;
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

    public WatchDogService watchDog;

    private String getCanonicalPath(String s) {
        if (s == null) { return null; }
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

    /**
     * Get value of configuration field
     * @param fieldName name of the field
     * @return object value
     */
    public Object getConfigurationValue(String fieldName) {
        try {
            configLock.readLock().lock();
            return getFieldValue(configuration, fieldName);
        } catch (IOException e) {
            return null;
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Get value of configuration field
     * @param fieldName name of the field
     * @return object value
     * @throws IOException
     */
    public Object getConfigurationValueException(String fieldName) throws IOException {
        try {
            configLock.readLock().lock();
            return getFieldValue(configuration, fieldName);
        } catch (IOException e) {
            throw new IOException("getter", e);
        } finally {
            configLock.readLock().unlock();
        }
    }

    /**
     * Set configuration value
     * @param fieldName name of the field
     * @param value string value
     */
    public void setConfigurationValue(String fieldName, String value) {
        try {
            configLock.writeLock().lock();
            setFieldValue(configuration, fieldName, value);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to set value of field {}: {}", new Object[]{fieldName, e});
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Set configuration value
     * @param fieldName name of the field
     * @param value value
     */
    public void setConfigurationValue(String fieldName, Object value) {
        try {
            configLock.writeLock().lock();
            setFieldValue(configuration, fieldName, value);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to set value of field {}: {}", new Object[]{fieldName, e});
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Set configuration value
     * @param fieldName name of the field
     * @param value value
     */
    public void setConfigurationValueException(String fieldName, Object value) throws IOException {
        try {
            configLock.writeLock().lock();
            setFieldValue(configuration, fieldName, value);
        } finally {
            configLock.writeLock().unlock();
        }
    }

    /**
     * Set configuration value
     * @param fieldName name of the field
     * @param value string value
     */
    public void setConfigurationValueException(String fieldName, String value) throws IOException {
        try {
            configLock.writeLock().lock();
            setFieldValue(configuration, fieldName, value);
        } finally {
            configLock.writeLock().unlock();
        }
    }

    public int getScanningDepth() {
        return (int)getConfigurationValue("scanningDepth");
    }

    public void setScanningDepth(int scanningDepth) {
        setConfigurationValue("scanningDepth", scanningDepth);
    }

    public int getCommandTimeout() {
        return (int)getConfigurationValue("commandTimeout");
    }

    public void setCommandTimeout(int timeout) {
        setConfigurationValue("commandTimeout", timeout);
    }

    public int getInteractiveCommandTimeout() {
        return (int)getConfigurationValue("interactiveCommandTimeout");
    }

    public void setInteractiveCommandTimeout(int timeout) {
        setConfigurationValue("interactiveCommandTimeout", timeout);
    }
    
    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public void setLastEditedDisplayMode(boolean lastEditedDisplayMode) {
        setConfigurationValue("lastEditedDisplayMode", lastEditedDisplayMode);
    }

    public boolean isLastEditedDisplayMode() {
        return (boolean)getConfigurationValue("lastEditedDisplayMode");
    }

    /**
     * Get the path to the where the web application includes are stored
     *
     * @return the path to the web application include files
     */
    public String getIncludeRootPath() {
        return (String)getConfigurationValue("includeRoot");
    }

    /**
     * Set include root path
     * @param includeRoot path
     */
    public void setIncludeRoot(String includeRoot) {
        setConfigurationValue("includeRoot", getCanonicalPath(includeRoot));
    }

    /**
     * Get the path to the where the index database is stored
     *
     * @return the path to the index database
     */
    public String getDataRootPath() {
        return (String)getConfigurationValue("dataRoot");
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
        setConfigurationValue("dataRoot", getCanonicalPath(dataRoot));
    }

    /**
     * Get the path to where the sources are located
     *
     * @return path to where the sources are located
     */
    public String getSourceRootPath() {
        return (String)getConfigurationValue("sourceRoot");
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
        setConfigurationValue("sourceRoot", getCanonicalPath(sourceRoot));
    }

    /**
     * Returns a path relative to source root. This would just be a simple
     * substring operation, except we need to support symlinks outside the
     * source root.
     *
     * @param file A file to resolve
     * @return Path relative to source root
     * @throws IOException If an IO error occurs
     * @throws FileNotFoundException if the file is not relative to source root
     * @throws ForbiddenSymlinkException if symbolic-link checking encounters
     * an ineligible link
     */
    public String getPathRelativeToSourceRoot(File file)
            throws IOException, ForbiddenSymlinkException {
        return PathUtils.getPathRelativeToSourceRoot(file, 0);
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
        return new ArrayList<>(getProjects().values());
    }

    /**
     * Get project map.
     *
     * @return a Map with all of the projects
     */
    public Map<String,Project> getProjects() {
        return (Map<String,Project>)getConfigurationValue("projects");
    }

    /**
     * Get names of all projects.
     *
     * @return a list containing names of all projects.
     */
    public List<String> getProjectNames() {
        return getProjectList().stream().map(Project::getName).collect(Collectors.toList());
    }

    /**
     * Set the list of the projects
     *
     * @param projects the map of projects to use
     */
    public void setProjects(Map<String,Project> projects) {
        try {
            configLock.writeLock().lock();
            if (projects != null) {
                populateGroups(getGroups(), new TreeSet<>(projects.values()));
            }
            setConfigurationValue("projects", projects);
        } finally {
            configLock.writeLock().unlock();
        }
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
        return (Set<Group>)getConfigurationValue("groups");
    }

    /**
     * Set the list of the groups
     *
     * @param groups the set of groups to use
     */
    public void setGroups(Set<Group> groups) {
        populateGroups(groups, new TreeSet<>(getProjects().values()));
        setConfigurationValue("groups", groups);
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
     * Gets a static placeholder for the web application context name that is
     * translated to the true servlet {@code contextPath} on demand.
     * @return {@code "/source"} + {@link Prefix#SEARCH_R} + {@code "?"}
     */
    public String getUrlPrefix() {
        return URL_PREFIX;
    }

    /**
     * Gets the name of the ctags program to use: either the last value passed
     * successfully to {@link #setCtags(java.lang.String)}, or
     * {@link Configuration#getCtags()}, or the system property for
     * {@code "org.opengrok.indexer.analysis.Ctags"}, or "ctags" as a
     * default.
     * @return a defined value
     */
    public String getCtags() {
        String value;
        return ctags != null ? ctags :
                (value = (String)getConfigurationValue("ctags")) != null ? value :
                System.getProperty(CtagsUtil.SYSTEM_CTAGS_PROPERTY,"ctags");
    }

    /**
     * Sets the name of the ctags program to use, or resets to use the fallbacks
     * documented for {@link #getCtags()}.
     * <p>
     * N.b. the value is not mediated to {@link Configuration}.
     *
     * @param ctags a defined value or {@code null} to reset to use the
     * {@link Configuration#getCtags()} fallbacks
     * @see #getCtags()
     */
    public void setCtags(String ctags) {
        this.ctags = ctags;
    }

    /**
     * Gets the name of the mandoc program to use: either the last value passed
     * successfully to {@link #setMandoc(java.lang.String)}, or
     * {@link Configuration#getMandoc()}, or the system property for
     * {@code "org.opengrok.indexer.analysis.Mandoc"}, or {@code null} as a
     * default.
     * @return a defined instance or {@code null}
     */
    public String getMandoc() {
        String value;
        return mandoc != null ? mandoc : (value =
                (String)getConfigurationValue("mandoc")) != null ? value :
            System.getProperty("org.opengrok.indexer.analysis.Mandoc");
    }

    /**
     * Sets the name of the mandoc program to use, or resets to use the
     * fallbacks documented for {@link #getMandoc()}.
     * <p>
     * N.b. the value is not mediated to {@link Configuration}.
     *
     * @param value a defined value or {@code null} to reset to use the
     * {@link Configuration#getMandoc()} fallbacks
     * @see #getMandoc()
     */
    public void setMandoc(String value) {
        this.mandoc = value;
    }

    public int getCachePages() {
        return (int)getConfigurationValue("cachePages");
    }

    public void setCachePages(int cachePages) {
        setConfigurationValue("cachePages", cachePages);
    }

    public int getHitsPerPage() {
        return (int)getConfigurationValue("hitsPerPage");
    }

    public void setHitsPerPage(int hitsPerPage) {
        setConfigurationValue("hitsPerPage", hitsPerPage);
    }

    private transient Boolean ctagsFound;

    /**
     * Validate that there is a Universal ctags program.
     *
     * @return true if success, false otherwise
     */
    public boolean validateUniversalCtags() {
        if (ctagsFound == null) {
            if (!CtagsUtil.validate(getCtags())) {
                ctagsFound = false;
            } else {
                ctagsFound = true;
            }
        }
        return ctagsFound;
    }

    /**
     * Get the max time a SCM operation may use to avoid being cached
     *
     * @return the max time
     */
    public int getHistoryReaderTimeLimit() {
        return (int)getConfigurationValue("historyCacheTime");
    }

    /**
     * Specify the maximum time a SCM operation should take before it will be
     * cached (in ms)
     *
     * @param historyReaderTimeLimit the max time in ms before it is cached
     */
    public void setHistoryReaderTimeLimit(int historyReaderTimeLimit) {
        setConfigurationValue("historyCacheTime", historyReaderTimeLimit);
    }

    /**
     * Is history cache currently enabled?
     *
     * @return true if history cache is enabled
     */
    public boolean useHistoryCache() {
        return (boolean)getConfigurationValue("historyCache");
    }

    /**
     * Specify if we should use history cache or not
     *
     * @param useHistoryCache set false if you do not want to use history cache
     */
    public void setUseHistoryCache(boolean useHistoryCache) {
        setConfigurationValue("historyCache", useHistoryCache);
    }

    /**
     * Should we generate HTML or not during the indexing phase
     *
     * @return true if HTML should be generated during the indexing phase
     */
    public boolean isGenerateHtml() {
        return (boolean)getConfigurationValue("generateHtml");
    }

    /**
     * Specify if we should generate HTML or not during the indexing phase
     *
     * @param generateHtml set this to true to pregenerate HTML
     */
    public void setGenerateHtml(boolean generateHtml) {
        setConfigurationValue("generateHtml", generateHtml);
    }

    /**
     * Set if we should compress the xref files or not
     *
     * @param compressXref set to true if the generated html files should be
     * compressed
     */
    public void setCompressXref(boolean compressXref) {
        setConfigurationValue("compressXref", compressXref);
    }

    /**
     * Are we using compressed HTML files?
     *
     * @return {@code true} if the html-files should be compressed.
     */
    public boolean isCompressXref() {
        return (boolean)getConfigurationValue("compressXref");
    }

    public boolean isQuickContextScan() {
        return (boolean)getConfigurationValue("quickContextScan");
    }

    public void setQuickContextScan(boolean quickContextScan) {
        setConfigurationValue("quickContextScan", quickContextScan);
    }

    public List<RepositoryInfo> getRepositories() {
        return (List<RepositoryInfo>)getConfigurationValue("repositories");
    }

    /**
     * Set the list of repositories.
     *
     * @param repositories the repositories to use
     */
    public void setRepositories(List<RepositoryInfo> repositories) {
        setConfigurationValue("repositories", repositories);
    }
    
    public void removeRepositories() {
        try {
            configLock.writeLock().lock();
            configuration.setRepositories(null);
        } finally {
            configLock.writeLock().unlock();
        }
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
     * @param repositories list of repositories
     */
    public void addRepositories(List<RepositoryInfo> repositories) {
        Lock writeLock = configLock.writeLock();
        try {
            writeLock.lock();
            configuration.addRepositories(repositories);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Set the projects that are specified to be the default projects to use.
     * The default projects are the projects you will search (from the web
     * application) if the page request didn't contain the cookie..
     *
     * @param defaultProjects The default project to use
     */
    public void setDefaultProjects(Set<Project> defaultProjects) {
        setConfigurationValue("defaultProjects", defaultProjects);
    }

    /**
     * Get the projects that are specified to be the default projects to use.
     * The default projects are the projects you will search (from the web
     * application) if the page request didn't contain the cookie..
     *
     * @return the default projects (may be null if not specified)
     */
    public Set<Project> getDefaultProjects() {
        Set<Project> projects = (Set<Project>)getConfigurationValue("defaultProjects");
        if (projects == null) {
            return null;
        }
        return Collections.unmodifiableSet(projects);
    }

    /**
     *
     * @return at what size (in MB) we should flush the buffer
     */
    public double getRamBufferSize() {
        return (double)getConfigurationValue("ramBufferSize");
    }

    /**
     * Set the size of buffer which will determine when the docs are flushed to
     * disk. Specify size in MB please. 16MB is default note that this is per
     * thread (lucene uses 8 threads by default in 4.x)
     *
     * @param ramBufferSize the size(in MB) when we should flush the docs
     */
    public void setRamBufferSize(double ramBufferSize) {
        setConfigurationValue("ramBufferSize", ramBufferSize);
    }

    public void setPluginDirectory(String pluginDirectory) {
        setConfigurationValue("pluginDirectory", pluginDirectory);
    }

    public String getPluginDirectory() {
        return (String)getConfigurationValue("pluginDirectory");
    }

    public boolean isAuthorizationWatchdog() {
        return (boolean)getConfigurationValue("authorizationWatchdogEnabled");
    }

    public void setAuthorizationWatchdog(boolean authorizationWatchdogEnabled) {
        setConfigurationValue("authorizationWatchdogEnabled", authorizationWatchdogEnabled);
    }

    public AuthorizationStack getPluginStack() {
        return (AuthorizationStack)getConfigurationValue("pluginStack");
    }

    public void setPluginStack(AuthorizationStack pluginStack) {
        setConfigurationValue("pluginStack", pluginStack);
    }

    /**
     * Is the progress print flag turned on?
     *
     * @return true if we can print per project progress %
     */
    public boolean isPrintProgress() {
        return (boolean)getConfigurationValue("printProgress");
    }

    /**
     * Set the printing of progress % flag (user convenience)
     *
     * @param printProgress new value
     */
    public void setPrintProgress(boolean printProgress) {
        setConfigurationValue("printProgress", printProgress);
    }

    /**
     * Specify if a search may start with a wildcard. Note that queries that
     * start with a wildcard will give a significant impact on the search
     * performance.
     *
     * @param allowLeadingWildcard set to true to activate (disabled by default)
     */
    public void setAllowLeadingWildcard(boolean allowLeadingWildcard) {
        setConfigurationValue("allowLeadingWildcard", allowLeadingWildcard);
    }

    /**
     * Is leading wildcards allowed?
     *
     * @return true if a search may start with a wildcard
     */
    public boolean isAllowLeadingWildcard() {
        return (boolean)getConfigurationValue("allowLeadingWildcard");
    }

    public IgnoredNames getIgnoredNames() {
        return (IgnoredNames)getConfigurationValue("ignoredNames");
    }

    public void setIgnoredNames(IgnoredNames ignoredNames) {
        setConfigurationValue("ignoredNames", ignoredNames);
    }

    public Filter getIncludedNames() {
        return (Filter)getConfigurationValue("includedNames");
    }

    public void setIncludedNames(Filter includedNames) {
        setConfigurationValue("includedNames", includedNames);
    }

    /**
     * Returns the user page for the history listing
     *
     * @return the URL string fragment preceeding the username
     */
    public String getUserPage() {
        return (String)getConfigurationValue("userPage");
    }

    /**
     * Get the client command to use to access the repository for the given
     * fully qualified classname.
     *
     * @param clazzName name of the targeting class
     * @return {@code null} if not yet set, the client command otherwise.
     */
    public String getRepoCmd(String clazzName) {
        Lock readLock = configLock.readLock();
        String cmd = null;
        try {
            readLock.lock();
            cmd = configuration.getRepoCmd(clazzName);
        } finally {
            readLock.unlock();
        }

        return cmd;
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
        Lock writeLock = configLock.writeLock();
        try {
            writeLock.lock();
            configuration.setRepoCmd(clazzName, cmd);
        } finally {
            writeLock.unlock();
        }

        return cmd;
    }

    /**
     * Sets the user page for the history listing
     *
     * @param userPage the URL fragment preceeding the username from history
     */
    public void setUserPage(String userPage) {
        setConfigurationValue("userPage", userPage);
    }

    /**
     * Returns the user page suffix for the history listing
     *
     * @return the URL string fragment following the username
     */
    public String getUserPageSuffix() {
        return (String)getConfigurationValue("userPageSuffix");
    }

    /**
     * Sets the user page suffix for the history listing
     *
     * @param userPageSuffix the URL fragment following the username from
     * history
     */
    public void setUserPageSuffix(String userPageSuffix) {
        setConfigurationValue("userPageSuffix", userPageSuffix);
    }

    /**
     * Returns the bug page for the history listing
     *
     * @return the URL string fragment preceeding the bug ID
     */
    public String getBugPage() {
        return (String)getConfigurationValue("bugPage");
    }

    /**
     * Sets the bug page for the history listing
     *
     * @param bugPage the URL fragment preceeding the bug ID
     */
    public void setBugPage(String bugPage) {
        setConfigurationValue("bugPage", bugPage);
    }

    /**
     * Returns the bug regex for the history listing
     *
     * @return the regex that is looked for in history comments
     */
    public String getBugPattern() {
        return (String)getConfigurationValue("bugPattern");
    }

    /**
     * Sets the bug regex for the history listing
     *
     * @param bugPattern the regex to search history comments
     * @throws IOException
     */
    public void setBugPattern(String bugPattern) throws IOException {
        setConfigurationValueException("bugPattern", bugPattern);
    }

    /**
     * Returns the review(ARC) page for the history listing
     *
     * @return the URL string fragment preceeding the review page ID
     */
    public String getReviewPage() {
        return (String)getConfigurationValue("reviewPage");
    }

    /**
     * Sets the review(ARC) page for the history listing
     *
     * @param reviewPage the URL fragment preceeding the review page ID
     */
    public void setReviewPage(String reviewPage) {
        setConfigurationValue("reviewPage", reviewPage);
    }

    /**
     * Returns the review(ARC) regex for the history listing
     *
     * @return the regex that is looked for in history comments
     */
    public String getReviewPattern() {
        return (String)getConfigurationValue("reviewPattern");
    }

    /**
     * Sets the review(ARC) regex for the history listing
     *
     * @param reviewPattern the regex to search history comments
     * @throws IOException
     */
    public void setReviewPattern(String reviewPattern) throws IOException {
        setConfigurationValueException("reviewPattern", reviewPattern);
    }

    public String getWebappLAF() {
        return (String)getConfigurationValue("webappLAF");
    }

    public void setWebappLAF(String laf) {
        setConfigurationValue("webappLAF", laf);
    }

    public Configuration.RemoteSCM getRemoteScmSupported() {
        return (Configuration.RemoteSCM)getConfigurationValue("remoteScmSupported");
    }

    public void setRemoteScmSupported(Configuration.RemoteSCM supported) {
        setConfigurationValue("remoteScmSupported", supported);
    }

    public boolean isOptimizeDatabase() {
        return (boolean)getConfigurationValue("optimizeDatabase");
    }

    public void setOptimizeDatabase(boolean optimizeDatabase) {
        setConfigurationValue("optimizeDatabase", optimizeDatabase);
    }

    public LuceneLockName getLuceneLocking() {
        return (LuceneLockName)getConfigurationValue("luceneLocking");
    }

    public boolean isIndexVersionedFilesOnly() {
        return (boolean)getConfigurationValue("indexVersionedFilesOnly");
    }

    public void setIndexVersionedFilesOnly(boolean indexVersionedFilesOnly) {
        setConfigurationValue("indexVersionedFilesOnly", indexVersionedFilesOnly);
    }

    /**
     * Gets the value of {@link Configuration#getIndexingParallelism()} -- or
     * if zero, then as a default gets the number of available processors.
     * @return a natural number &gt;= 1
     */
    public int getIndexingParallelism() {
        int parallelism = (int)getConfigurationValue("indexingParallelism");
        return parallelism < 1 ? Runtime.getRuntime().availableProcessors() :
            parallelism;
    }

    /**
     * Gets the value of {@link Configuration#getHistoryParallelism()} -- or
     * if zero, then as a default gets the number of available processors.
     * @return a natural number &gt;= 1
     */
    public int getHistoryParallelism() {
        int parallelism = (int)getConfigurationValue("historyParallelism");
        return parallelism < 1 ? Runtime.getRuntime().availableProcessors() :
            parallelism;
    }

    /**
     * Gets the value of {@link Configuration#getHistoryRenamedParallelism()} -- or
     * if zero, then as a default gets the number of available processors.
     * @return a natural number &gt;= 1
     */
    public int getHistoryRenamedParallelism() {
        int parallelism = (int)getConfigurationValue("historyRenamedParallelism");
        return parallelism < 1 ? Runtime.getRuntime().availableProcessors() :
            parallelism;
    }

    public boolean isTagsEnabled() {
        return (boolean)getConfigurationValue("tagsEnabled");
    }

    public void setTagsEnabled(boolean tagsEnabled) {
        setConfigurationValue("tagsEnabled", tagsEnabled);
    }

    public boolean isScopesEnabled() {
        return (boolean)getConfigurationValue("scopesEnabled");
    }

    public void setScopesEnabled(boolean scopesEnabled) {
        setConfigurationValue("scopesEnabled", scopesEnabled);
    }

    public boolean isProjectsEnabled() {
        return (boolean)getConfigurationValue("projectsEnabled");
    }

    public void setProjectsEnabled(boolean projectsEnabled) {
        setConfigurationValue("projectsEnabled", projectsEnabled);
    }

    public boolean isFoldingEnabled() {
        return (boolean)getConfigurationValue("foldingEnabled");
    }

    public void setFoldingEnabled(boolean foldingEnabled) {
        setConfigurationValue("foldingEnabled", foldingEnabled);
    }

    public Date getDateForLastIndexRun() {
        return indexTime.getDateForLastIndexRun();
    }

    public String getCTagsExtraOptionsFile() {
        return (String)getConfigurationValue("CTagsExtraOptionsFile");
    }

    public void setCTagsExtraOptionsFile(String filename) {
        setConfigurationValue("CTagsExtraOptionsFile", filename);
    }

    public Set<String> getAllowedSymlinks() {
        return (Set<String>)getConfigurationValue("allowedSymlinks");
    }

    public void setAllowedSymlinks(Set<String> allowedSymlinks) {
        setConfigurationValue("allowedSymlinks", allowedSymlinks);
    }

    /**
     * Return whether e-mail addresses should be obfuscated in the xref.
     * @return if we obfuscate emails
     */
    public boolean isObfuscatingEMailAddresses() {
        return (boolean)getConfigurationValue("obfuscatingEMailAddresses");
    }

    /**
     * Set whether e-mail addresses should be obfuscated in the xref.
     * @param obfuscate should we obfuscate emails?
     */
    public void setObfuscatingEMailAddresses(boolean obfuscate) {
        setConfigurationValue("obfuscatingEMailAddresses", obfuscate);
    }

    /**
     * Should status.jsp print internal settings, like paths and database URLs?
     *
     * @return {@code true} if status.jsp should show the configuration,
     * {@code false} otherwise
     */
    public boolean isChattyStatusPage() {
        return (boolean)getConfigurationValue("chattyStatusPage");
    }

    /**
     * Set whether status.jsp should print internal settings.
     *
     * @param chatty {@code true} if internal settings should be printed,
     * {@code false} otherwise
     */
    public void setChattyStatusPage(boolean chatty) {
        setConfigurationValue("chattyStatusPage", chatty);
    }

    public void setFetchHistoryWhenNotInCache(boolean nofetch) {
        setConfigurationValue("fetchHistoryWhenNotInCache", nofetch);
    }

    public boolean isFetchHistoryWhenNotInCache() {
        return (boolean)getConfigurationValue("fetchHistoryWhenNotInCache");
    }

    public boolean isHistoryCache() {
        return (boolean)getConfigurationValue("historyCache");
    }

    public void setHandleHistoryOfRenamedFiles(boolean enable) {
        setConfigurationValue("handleHistoryOfRenamedFiles", enable);
    }

    public boolean isHandleHistoryOfRenamedFiles() {
        return (boolean)getConfigurationValue("handleHistoryOfRenamedFiles");
    }

    public void setNavigateWindowEnabled(boolean enable) {
        setConfigurationValue("navigateWindowEnabled", enable);
    }

    public boolean isNavigateWindowEnabled() {
        return (boolean)getConfigurationValue("navigateWindowEnabled");
    }

    public void setRevisionMessageCollapseThreshold(int threshold) {
        setConfigurationValue("revisionMessageCollapseThreshold", threshold);
    }

    public int getRevisionMessageCollapseThreshold() {
        return (int)getConfigurationValue("revisionMessageCollapseThreshold");
    }

    public void setMaxSearchThreadCount(int count) {
        setConfigurationValue("maxSearchThreadCount", count);
    }

    public int getMaxSearchThreadCount() {
        return (int)getConfigurationValue("maxSearchThreadCount");
    }

    public int getCurrentIndexedCollapseThreshold() {
        return (int)getConfigurationValue("currentIndexedCollapseThreshold");
    }

    public void setCurrentIndexedCollapseThreshold(int currentIndexedCollapseThreshold) {
        setConfigurationValue("currentIndexedCollapseThreshold", currentIndexedCollapseThreshold);
    }

    public int getGroupsCollapseThreshold() {
        return (int)getConfigurationValue("groupsCollapseThreshold");
    }

    // The URI is not necessary to be present in the configuration
    // (so that when -U option of the indexer is omitted, the config will not
    // be sent to the webapp) so store it only in the RuntimeEnvironment.
    public void setConfigURI(String host) {
        configURI = host;
    }

    public String getConfigURI() {
        return configURI;
    }

    public boolean isHistoryEnabled() {
        return (boolean)getConfigurationValue("historyEnabled");
    }

    public void setHistoryEnabled(boolean flag) {
        setConfigurationValue("historyEnabled", flag);
    }

    public boolean getDisplayRepositories() {
        return (boolean)getConfigurationValue("displayRepositories");
    }

    public void setDisplayRepositories(boolean flag) {
        setConfigurationValue("displayRepositories", flag);
    }

    public boolean getListDirsFirst() {
        return (boolean)getConfigurationValue("listDirsFirst");
    }

    public void setListDirsFirst(boolean flag) {
        setConfigurationValue("listDirsFirst", flag);
    }

    public void setTabSize(int size) {
        setConfigurationValue("tabSize", size);
    }

    public int getTabSize() {
        return (int)getConfigurationValue("tabSize");
    }

    /**
     * Gets the total number of context lines per file to show
     * @return a value greater than zero
     */
    public short getContextLimit() {
        return (short)getConfigurationValue("contextLimit");
    }

    /**
     * Gets the number of context lines to show before or after any match:
     * @return a value greater than or equal to zero
     */
    public short getContextSurround() {
        return (short)getConfigurationValue("contextSurround");
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
     * Read configuration from a file and put it into effect.
     * @param file the file to read
     * @param interactive true if run in interactive mode
     * @throws IOException
     */
    public void readConfiguration(File file, boolean interactive) throws IOException {
        setConfiguration(Configuration.read(file), null, interactive);
    }

    /**
     * Write the current configuration to a file
     *
     * @param file the file to write the configuration into
     * @throws IOException if an error occurs
     */
    public void writeConfiguration(File file) throws IOException {
        Lock readLock = configLock.readLock();
        try {
            readLock.lock();

            configuration.write(file);
        } finally {
            readLock.unlock();
        }
    }

    public String getConfigurationXML() {
        String configXML;
        try {
            configLock.readLock().lock();
            configXML = configuration.getXMLRepresentationAsString();
        } finally {
            configLock.readLock().unlock();
        }
        return configXML;
    }

    /**
     * Write the current configuration to a socket
     *
     * @param host the host address to receive the configuration
     * @throws IOException if an error occurs
     */
    public void writeConfiguration(String host) throws IOException {
        String configXML;
        try {
            configLock.readLock().lock();
            configXML = configuration.getXMLRepresentationAsString();
        } finally {
            configLock.readLock().unlock();
        }

        Response r = ClientBuilder.newClient()
                .target(host)
                .path("api")
                .path("v1")
                .path("configuration")
                .queryParam("reindex", true)
                .request()
                .put(Entity.xml(configXML));

        if (r.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new IOException(r.toString());
        }
    }

    /**
     * Send message to webapp to refresh SearcherManagers for given projects.
     * This is used for partial reindex.
     *
     * @param subFiles list of directories to refresh corresponding SearcherManagers
     * @param host the host address to receive the configuration
     */
    public void signalTorefreshSearcherManagers(List<String> subFiles, String host) {
        // subFile entries start with path separator so get basename
        // to convert them to project names.

        subFiles.stream().map(proj -> new File(proj).getName()).forEach(project -> {
            Response r = ClientBuilder.newClient()
                    .target(host)
                    .path("api")
                    .path("v1")
                    .path("system")
                    .path("refresh")
                    .request()
                    .put(Entity.text(project));

            if (r.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOGGER.log(Level.WARNING, "Could not refresh search manager for {0}", project);
            }
        });
    }

    /**
     * Generate a TreeMap of projects with corresponding repository information.
     *
     * Project with some repository information is considered as a repository
     * otherwise it is just a simple project.
     */
    private void generateProjectRepositoriesMap() throws IOException {
        synchronized (repository_map) {
            repository_map.clear();
            for (RepositoryInfo r : getRepositories()) {
                Project proj;
                String repoPath;
                try {
                    repoPath = PathUtils.getPathRelativeToSourceRoot(
                            new File(r.getDirectoryName()), 0);
                } catch (ForbiddenSymlinkException e) {
                    LOGGER.log(Level.FINER, e.getMessage());
                    continue;
                }

                if ((proj = Project.getProject(repoPath)) != null) {
                    List<RepositoryInfo> values = repository_map.computeIfAbsent(proj, k -> new ArrayList<>());
                    // the map is held under the lock because the next call to
                    // values.add(r) which should not be called from multiple threads at the same time
                    values.add(r);
                }
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
        setConfiguration(configuration, null, false);
    }

    /**
     * Sets the configuration and performs necessary actions.
     * @param configuration new configuration
     * @param interactive true if in interactive mode
     */
    public void setConfiguration(Configuration configuration, boolean interactive) {
        setConfiguration(configuration, null, interactive);
    }

    /**
     * Sets the configuration and performs necessary actions.
     * @param configuration new configuration
     * @param subFileList list of repositories
     * @param interactive true if in interactive mode
     */
    public void setConfiguration(Configuration configuration, List<String> subFileList, boolean interactive) {
        try {
            configLock.writeLock().lock();
            this.configuration = configuration;
        } finally {
            configLock.writeLock().unlock();
        }

        // HistoryGuru constructor needs environment properties so no locking is done here.
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
                    configuration.getRepositories(), subFileList, interactive);
        } else {
            histGuru.invalidateRepositories(configuration.getRepositories(),
                    interactive);
        }

        try {
            configLock.writeLock().lock();
            // The invalidation of repositories above might have excluded some
            // repositories in HistoryGuru so the configuration needs to reflect that.
            configuration.setRepositories(new ArrayList<>(histGuru.getRepositories()));
        } finally {
            configLock.writeLock().unlock();
        }

        includeFiles.reloadIncludeFiles();
    }

    public IncludeFiles getIncludeFiles() {
        return includeFiles;
    }

    public String getStatisticsFilePath() {
        return (String)getConfigurationValue("statisticsFilePath");
    }

    public void setStatisticsFilePath(String path) {
        setConfigurationValue("statisticsFilePath", path);
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

    /**
     * Re-apply the configuration.
     * @param reindex is the message result of reindex
     * @param interactive true if in interactive mode
     */
    public void applyConfig(boolean reindex, boolean interactive) {
        applyConfig(configuration, reindex, interactive);
    }

    /**
     * Set configuration from a message. The message could have come from the
     * Indexer (in which case some extra work is needed) or is it just a request
     * to set new configuration in place.
     *
     * @param configuration XML configuration
     * @param reindex is the message result of reindex
     * @param interactive true if in interactive mode
     * @see #applyConfig(org.opengrok.indexer.configuration.Configuration,
     * boolean, boolean) applyConfig(config, reindex, interactive)
     */
    public void applyConfig(String configuration, boolean reindex, boolean interactive) {
        Configuration config;
        try {
            config = makeXMLStringAsConfiguration(configuration);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Configuration decoding failed", ex);
            return;
        }

        applyConfig(config, reindex, interactive);
    }

    /**
     * Set configuration from the incoming parameter. The configuration could
     * have come from the Indexer (in which case some extra work is needed) or
     * is it just a request to set new configuration in place.
     *
     * @param config the incoming configuration
     * @param reindex is the message result of reindex
     * @param interactive true if in interactive mode
     */
    public void applyConfig(Configuration config, boolean reindex, boolean interactive) {
        setConfiguration(config, interactive);
        LOGGER.log(Level.INFO, "Configuration updated");

        if (reindex) {
            // We are assuming that each update of configuration means reindex. If dedicated thread is introduced
            // in the future solely for the purpose of getting the event of reindex, the 2 calls below should
            // be moved there.
            refreshSearcherManagerMap();
            maybeRefreshIndexSearchers();
            // Force timestamp to update itself upon new config arrival.
            refreshDateForLastIndexRun();
        }

        // start/stop the watchdog if necessary
        if (isAuthorizationWatchdog() && getPluginDirectory() != null) {
            watchDog.start(new File(getPluginDirectory()));
        } else {
            watchDog.stop();
        }

        // set the new plugin directory and reload the authorization framework
        getAuthorizationFramework().setPluginDirectory(getPluginDirectory());
        getAuthorizationFramework().setStack(getPluginStack());
        getAuthorizationFramework().reload();

        messagesContainer.setMessageLimit(getMessageLimit());
    }

    public void setIndexTimestamp() throws IOException {
        indexTime.stamp();
    }

    public void refreshDateForLastIndexRun() {
        indexTime.refreshDateForLastIndexRun();
    }

    private void maybeRefreshSearcherManager(SearcherManager sm) {
        try {
            sm.maybeRefresh();
        }  catch (AlreadyClosedException ex) {
            // This is a case of removed project. See refreshSearcherManagerMap() for details.
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "maybeRefresh failed", ex);
        }
    }

    public void maybeRefreshIndexSearchers(Iterable<String> projects) {
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
     * Each IndexSearcher is born from a SearcherManager object. There is one SearcherManager for every project.
     * This schema makes it possible to reuse IndexSearcher/IndexReader objects so the heavy lifting
     * (esp. system calls) performed in FSDirectory and DirectoryReader happens only once for a project.
     * The caller has to make sure that the IndexSearcher is returned back
     * to the SearcherManager. This is done with returnIndexSearcher().
     * The return of the IndexSearcher should happen only after the search result data are read fully.
     *
     * @param proj project
     * @return SearcherManager for given project
     * @throws IOException I/O exception
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

        // TODO might need to rewrite to Project instead of String, need changes in projects.jspf too.
        for (String proj : projects) {
            try {
                SuperIndexSearcher searcher = RuntimeEnvironment.getInstance().getIndexSearcher(proj);
                subreaders[ii++] = searcher.getIndexReader();
                searcherList.add(searcher);
            } catch (IOException | NullPointerException ex) {
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

    public void startExpirationTimer() {
        messagesContainer.setMessageLimit((int)getConfigurationValue("messageLimit"));
        messagesContainer.startExpirationTimer();
    }

    public void stopExpirationTimer() {
        messagesContainer.stopExpirationTimer();
    }

    /**
     * Get the default set of messages for the main tag.
     *
     * @return set of messages
     */
    public SortedSet<AcceptedMessage> getMessages() {
        return messagesContainer.getMessages();
    }

    /**
     * Get the set of messages for the arbitrary tag
     *
     * @param tag the message tag
     * @return set of messages
     */
    public SortedSet<AcceptedMessage> getMessages(final String tag) {
        return messagesContainer.getMessages(tag);
    }

    /**
     * Add a message to the application.
     * Also schedules a expiration timer to remove this message after its expiration.
     *
     * @param message the message
     */
    public void addMessage(final Message message) {
        messagesContainer.addMessage(message);
    }

    /**
     * Remove all messages containing at least one of the tags.
     *
     * @param tags set of tags
     */
    public void removeAnyMessage(final Set<String> tags) {
        messagesContainer.removeAnyMessage(tags);
    }

    /**
     * @return all messages regardless their tag
     */
    public Set<AcceptedMessage> getAllMessages() {
        return messagesContainer.getAllMessages();
    }

    public Path getDtagsEftarPath() {
        Path path;
        try {
            configLock.readLock().lock();
            path = configuration.getDtagsEftarPath();
        } finally {
            configLock.readLock().unlock();
        }
        return path;
    }

    /**
     * Get the eftar file, which contains definition tags for path descriptions.
     *
     * @return {@code null} if there is no such file, the file otherwise.
     */
    public File getDtagsEftar() {
        if (dtagsEftar == null) {
            File tmp = getDtagsEftarPath().toFile();
            if (tmp.canRead()) {
                dtagsEftar = tmp;
            }
        }
        return dtagsEftar;
    }

    public SuggesterConfig getSuggesterConfig() {
        return (SuggesterConfig)getConfigurationValue("suggesterConfig");
    }

    public void setSuggesterConfig(SuggesterConfig config) {
        setConfigurationValue("suggesterConfig", config);
    }

    public int getMessageLimit() { return (int)getConfigurationValue("messageLimit"); }
}
