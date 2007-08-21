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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensolaris.opengrok.history.ExternalRepository;
import org.opensolaris.opengrok.index.IgnoredNames;

/**
 * Placeholder class for all configuration variables. Due to the multithreaded
 * nature of the web application, each thread will use the same instance of the
 * configuration object for each page request. Class and methods should have
 * package scope, but that didn't work with the XMLDecoder/XMLEncoder.
 */
public class Configuration {
    private String ctags;
    private boolean historyCache;
    private int historyCacheTime;
    private List<Project> projects;
    private String sourceRoot;
    private String dataRoot;
    private Map<String, ExternalRepository> repositories;
    private String urlPrefix;
    private boolean generateHtml;
    private Project defaultProject;
    private int indexWordLimit;
    private boolean verbose;
    private boolean allowLeadingWildcard;
    private IgnoredNames ignoredNames;
    
    /** Creates a new instance of Configuration */
    public Configuration() {
        setHistoryCache(true);
        setHistoryCacheTime(30);
        setProjects(new ArrayList<Project>());
        setRepositories(new HashMap<String, ExternalRepository>());
        setUrlPrefix("/source/s?");
        setCtags("ctags");
        setIndexWordLimit(60000);
        setVerbose(false);
        setGenerateHtml(true);
        setQuickContextScan(true);
        setIgnoredNames(new IgnoredNames());
    }
    
    public String getCtags() {
        return ctags;
    }
    
    public void setCtags(String ctags) {
        this.ctags = ctags;
    }
    
    public boolean isHistoryCache() {
        return historyCache;
    }
    
    public void setHistoryCache(boolean historyCache) {
        this.historyCache = historyCache;
    }
    
    public int getHistoryCacheTime() {
        return historyCacheTime;
    }
    
    public void setHistoryCacheTime(int historyCacheTime) {
        this.historyCacheTime = historyCacheTime;
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
    
    public Map<String, ExternalRepository> getRepositories() {
        return repositories;
    }
    
    public void setRepositories(Map<String, ExternalRepository> repositories) {
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
}
