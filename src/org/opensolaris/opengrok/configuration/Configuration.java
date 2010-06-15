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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.configuration;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opensolaris.opengrok.history.RepositoryInfo;
import org.opensolaris.opengrok.index.Filter;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * Placeholder class for all configuration variables. Due to the multithreaded
 * nature of the web application, each thread will use the same instance of the
 * configuration object for each page request. Class and methods should have
 * package scope, but that didn't work with the XMLDecoder/XMLEncoder.
 */
public final class Configuration {
    private String ctags;

    /** Should the history log be cached? */
    private boolean historyCache;
    /**
     * The maximum time in milliseconds {@code HistoryCache.get()} can take
     * before its result is cached.
     */
    private int historyCacheTime;

    /** Should the history cache be stored in a database? */
    private boolean historyCacheInDB;

    private List<Project> projects;
    private String sourceRoot;
    private String dataRoot;
    private List<RepositoryInfo> repositories;
    private String urlPrefix;
    private boolean generateHtml;
    /** Default project will be used, when no project is selected and no project is in cookie, so basically only the first time you open the first page, or when you clear your web cookies */
    private Project defaultProject;
    private int indexWordLimit;
    private boolean verbose;
    private boolean allowLeadingWildcard;
    private IgnoredNames ignoredNames;
    private Filter includedNames;
    private String userPage;
    private String bugPage;
    private String bugPattern;
    private String reviewPage;
    private String reviewPattern;
    private String webappLAF;
    private boolean remoteScmSupported;
    private boolean optimizeDatabase;
    private boolean useLuceneLocking;
    private boolean compressXref;
    private boolean indexVersionedFilesOnly;
    private int hitsPerPage;
    private int cachePages;
    private String databaseDriver;
    private String databaseUrl;
    private Integer scanningDepth;
    private Set<String> allowedSymlinks;

    public Integer getScanningDepth() {
        return scanningDepth;
    }

    public void setScanningDepth(Integer scanningDepth) {
        this.scanningDepth = scanningDepth;
    }

    /** Creates a new instance of Configuration */
    public Configuration() {
        //defaults for an opengrok instance configuration
        setHistoryCache(true);
        setHistoryCacheTime(30);
        setHistoryCacheInDB(false);
        setProjects(new ArrayList<Project>());
        setRepositories(new ArrayList<RepositoryInfo>());
        setUrlPrefix("/source/s?");
        //setUrlPrefix("../s?"); // TODO generate relative search paths, get rid of -w <webapp> option to indexer !
        setCtags("ctags");
        //below can cause an outofmemory error, since it is defaulting to NO LIMIT
        setIndexWordLimit(Integer.MAX_VALUE);
        setVerbose(false);
        setGenerateHtml(true);
        setQuickContextScan(true);
        setIgnoredNames(new IgnoredNames());
        setIncludedNames(new Filter());
        setUserPage("http://www.opensolaris.org/viewProfile.jspa?username=");
        setBugPage("http://bugs.opensolaris.org/bugdatabase/view_bug.do?bug_id=");
        setBugPattern("\\b([12456789][0-9]{6})\\b");
        setReviewPage("http://arc.opensolaris.org/caselog/PSARC/");
        setReviewPattern("\\b(\\d{4}/\\d{3})\\b"); // in form e.g. PSARC 2008/305
        setWebappLAF("default");
        setRemoteScmSupported(false);
        setOptimizeDatabase(true);
        setUsingLuceneLocking(false);
        setCompressXref(true);
        setIndexVersionedFilesOnly(false);
        setHitsPerPage(25);
        setCachePages(5);
        setScanningDepth(3); // default depth of scanning for repositories
        setAllowedSymlinks(new HashSet<String>());
    }

    public String getCtags() {
        return ctags;
    }

    public void setCtags(String ctags) {
        this.ctags = ctags;
    }

    public int getCachePages() {
        return cachePages;
    }

    public void setCachePages(int cachePages) {
        this.cachePages = cachePages;
    }

    public int getHitsPerPage() {
        return hitsPerPage;
    }

    public void setHitsPerPage(int hitsPerPage) {
        this.hitsPerPage = hitsPerPage;
    }

    /**
     * Should the history log be cached?
     * @return {@code true} if a {@code HistoryCache} implementation should
     * be used, {@code false} otherwise
     */
    public boolean isHistoryCache() {
        return historyCache;
    }

    /**
     * Set whether history should be cached.
     * @param historyCache if {@code true} enable history cache
     */
    public void setHistoryCache(boolean historyCache) {
        this.historyCache = historyCache;
    }

    /**
     * How long can a history request take before it's cached? If the time
     * is exceeded, the result is cached. This setting only affects
     * {@code FileHistoryCache}.
     *
     * @return the maximum time in milliseconds a history request can take
     * before it's cached
     */
    public int getHistoryCacheTime() {
        return historyCacheTime;
    }

    /**
     * Set the maximum time a history request can take before it's cached.
     * This setting is only respected if {@code FileHistoryCache} is used.
     *
     * @param historyCacheTime maximum time in milliseconds
     */
    public void setHistoryCacheTime(int historyCacheTime) {
        this.historyCacheTime = historyCacheTime;
    }

    /**
     * Should the history cache be stored in a database? If yes,
     * {@code JDBCHistoryCache} will be used to cache the history; otherwise,
     * {@code FileHistoryCache} is used.
     *
     * @return whether the history cache should be stored in a database
     */
    public boolean isHistoryCacheInDB() {
        return historyCacheInDB;
    }

    /**
     * Set whether the history cache should be stored in a database, and
     * {@code JDBCHistoryCache} should be used instead of {@code
     * FileHistoryCache}.
     *
     * @param historyCacheInDB whether the history cached should be stored in
     * a database
     */
    public void setHistoryCacheInDB(boolean historyCacheInDB) {
        this.historyCacheInDB = historyCacheInDB;
    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public String getDataRoot() {
        return dataRoot;
    }

    public void setDataRoot(String dataRoot) {
        this.dataRoot = dataRoot;
    }

    public List<RepositoryInfo> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<RepositoryInfo> repositories) {
        this.repositories = repositories;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public void setGenerateHtml(boolean generateHtml) {
        this.generateHtml = generateHtml;
    }

    public boolean isGenerateHtml() {
        return generateHtml;
    }

    public void setDefaultProject(Project defaultProject) {
        this.defaultProject = defaultProject;
    }

    public Project getDefaultProject() {
        return defaultProject;
    }

    public int getIndexWordLimit() {
        return indexWordLimit;
    }

    public void setIndexWordLimit(int indexWordLimit) {
        this.indexWordLimit = indexWordLimit;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setAllowLeadingWildcard(boolean allowLeadingWildcard) {
        this.allowLeadingWildcard = allowLeadingWildcard;
    }

    public boolean isAllowLeadingWildcard() {
        return allowLeadingWildcard;
    }

    private boolean quickContextScan;

    public boolean isQuickContextScan() {
        return quickContextScan;
    }

    public void setQuickContextScan(boolean quickContextScan) {
        this.quickContextScan = quickContextScan;
    }

    public void setIgnoredNames(IgnoredNames ignoredNames) {
        this.ignoredNames = ignoredNames;
    }

    public IgnoredNames getIgnoredNames() {
        return ignoredNames;
    }

    public void setIncludedNames(Filter includedNames) {
        this.includedNames = includedNames;
    }

    public Filter getIncludedNames() {
        return includedNames;
    }

    public void setUserPage(String userPage) {
        this.userPage = userPage;
    }

    public String getUserPage() {
        return userPage;
    }

    public void setBugPage(String bugPage) {
        this.bugPage = bugPage;
    }

    public String getBugPage() {
        return bugPage;
    }

    public void setBugPattern(String bugPattern) {
        this.bugPattern = bugPattern;
    }

    public String getBugPattern() {
        return bugPattern;
    }

    public String getReviewPage() {
        return reviewPage;
    }

    public void setReviewPage(String reviewPage) {
        this.reviewPage = reviewPage;
    }

    public String getReviewPattern() {
        return reviewPattern;
    }

    public void setReviewPattern(String reviewPattern) {
        this.reviewPattern = reviewPattern;
    }

    public String getWebappLAF() {
        return webappLAF;
    }

    public void setWebappLAF(String webappLAF) {
        this.webappLAF = webappLAF;
    }

    public boolean isRemoteScmSupported() {
        return remoteScmSupported;
    }

    public void setRemoteScmSupported(boolean remoteScmSupported) {
        this.remoteScmSupported = remoteScmSupported;
    }

    public boolean isOptimizeDatabase() {
        return optimizeDatabase;
    }

    public void setOptimizeDatabase(boolean optimizeDatabase) {
        this.optimizeDatabase = optimizeDatabase;
    }

    public boolean isUsingLuceneLocking() {
        return useLuceneLocking;
    }

    public void setUsingLuceneLocking(boolean useLuceneLocking) {
        this.useLuceneLocking = useLuceneLocking;
    }

    public void setCompressXref(boolean compressXref) {
        this.compressXref = compressXref;
    }

    public boolean isCompressXref() {
        return compressXref;
    }

    public boolean isIndexVersionedFilesOnly() {
        return indexVersionedFilesOnly;
    }

    public void setIndexVersionedFilesOnly(boolean indexVersionedFilesOnly) {
        this.indexVersionedFilesOnly = indexVersionedFilesOnly;
    }

    public Date getDateForLastIndexRun() {
        File timestamp = new File(getDataRoot(), "timestamp");
        return new Date(timestamp.lastModified());
    }

    public String getDatabaseDriver() {
        return databaseDriver;
    }

    public void setDatabaseDriver(String databaseDriver) {
        this.databaseDriver = databaseDriver;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public void setDatabaseUrl(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public Set<String> getAllowedSymlinks() {
        return allowedSymlinks;
    }

    public void setAllowedSymlinks(Set<String> allowedSymlinks) {
        this.allowedSymlinks = allowedSymlinks;
    }

    /**
     * Write the current configuration to a file
     * @param file the file to write the configuration into
     * @throws IOException if an error occurs
     */
    public void write(File file) throws IOException {
        final FileOutputStream out = new FileOutputStream(file);
        try {
            this.encodeObject(out);
        } finally {
            out.close();
        }
    }

    public String getXMLRepresentationAsString() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        this.encodeObject(bos);
        return bos.toString();
    }

    private void encodeObject(OutputStream out) {
        XMLEncoder e = new XMLEncoder(new BufferedOutputStream(out));
        e.writeObject(this);
        e.close();
    }

    public static Configuration read(File file) throws IOException {
        final FileInputStream in = new FileInputStream(file);
        try {
            return decodeObject(in);
        } finally {
            in.close();
        }
    }



    public static Configuration makeXMLStringAsConfiguration(String xmlconfig) throws IOException {
        final Configuration ret;
        final ByteArrayInputStream in = new ByteArrayInputStream(xmlconfig.getBytes());
        ret = decodeObject(in);
        return ret;
    }

    private static Configuration decodeObject(InputStream in) throws IOException {
        XMLDecoder d = new XMLDecoder(new BufferedInputStream(in));
        final Object ret = d.readObject();
        d.close();

        if (!(ret instanceof Configuration)) {
            throw new IOException("Not a valid config file");
        }
        return (Configuration)ret;
    }

}
