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
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ClassUtil;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.web.Util;

import static org.opengrok.indexer.configuration.PatternUtil.compilePattern;

/**
 * Placeholder for the information that builds up a project.
 */
public class Project implements Comparable<Project>, Nameable, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(Project.class);

    static {
        ClassUtil.remarkTransientFields(Project.class);
    }

    /**
     * Path relative to source root. Uses the '/' separator on all platforms.
     */
    private String path;

    /**
     * This variable is very important, since it's used as the project
     * identifier all over xrefs and webapp.
     */
    private String name;

    /**
     * Size of tabs in this project. Used for displaying the xrefs correctly in
     * projects with non-standard tab size.
     */
    private int tabSize;

    /**
     * A flag if the navigate window should be opened by default when browsing
     * the source code of this project.
     */
    private Boolean navigateWindowEnabled = null;

    /**
     * This flag sets per-project handling of renamed files.
     */
    private Boolean handleRenamedFiles = null;

    /**
     * This flag enables/disables per-project history cache.
     */
    private Boolean historyEnabled = null;

    /**
     * This flag enables/disables per project merge commits.
     */
    private Boolean mergeCommitsEnabled = null;

    /**
     * This marks the project as (not)ready before initial index is done. this
     * is to avoid all/multi-project searches referencing this project from
     * failing.
     */
    private boolean indexed = false;

    /**
     * This flag sets per-project reindex based on traversing SCM history.
     */
    private Boolean historyBasedReindex = null;

    /**
     * Set of groups which match this project.
     */
    private transient Set<Group> groups = new TreeSet<>();

    /**
     * These properties override global settings, if set.
     */
    private String bugPage;
    private String bugPattern;
    private String reviewPage;
    private String reviewPattern;

    // This empty constructor is needed for serialization.
    public Project() {
    }

    /**
     * Create a project with given name.
     *
     * @param name the name of the project
     */
    public Project(String name) {
        this.name = name;
    }

    /**
     * Create a project with given name and path and default configuration
     * values.
     *
     * @param name the name of the project
     * @param path the path of the project relative to the source root
     */
    public Project(String name, String path) {
        this.name = name;
        this.path = Util.fixPathIfWindows(path);
        completeWithDefaults();
    }

    /**
     * Get a textual name of this project.
     *
     * @return a textual name of the project
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the path (relative from source root) where this project is located.
     *
     * @return the relative path
     */
    public String getPath() {
        return path;
    }

    public boolean isIndexed() {
        return indexed;
    }

    /**
     * Get the project id.
     *
     * @return the id of the project
     */
    public String getId() {
        return path;
    }

    /**
     * Get the tab size for this project, if tab size has been set.
     *
     * @return tab size if set, 0 otherwise
     * @see #hasTabSizeSetting()
     */
    public int getTabSize() {
        return tabSize;
    }

    /**
     * Set a textual name of this project, preferably don't use " , " in the
     * name, since it's used as delimiter for more projects
     *
     * XXX we should not allow setting project name after it has been
     * constructed because it is probably part of HashMap.
     *
     * @param name a textual name of the project
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set the path (relative from source root) this project is located.
     *
     * @param path the relative path from source root where this project is
     * located, starting with path separator.
     */
    public void setPath(String path) {
        this.path = Util.fixPathIfWindows(path);
    }

    public void setIndexed(boolean flag) {
        this.indexed = flag;
    }

    /**
     * Set tab size for this project. Used for expanding tabs to spaces in
     * xrefs.
     *
     * @param tabSize the size of tabs in this project
     */
    public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
    }

    /**
     * Has this project an explicit tab size setting?
     *
     * @return {@code true} if the tab size has been set for this project, or
     * {@code false} if it hasn't and the default should be used
     */
    public boolean hasTabSizeSetting() {
        return tabSize > 0;
    }

    /**
     * Indicate whether the navigate window should be opened by default when
     * browsing a source code from this project.
     *
     * @return true if yes; false otherwise
     */
    public boolean isNavigateWindowEnabled() {
        return navigateWindowEnabled != null && navigateWindowEnabled;
    }

    /**
     * Set the value of navigateWindowEnabled.
     *
     * @param navigateWindowEnabled new value of navigateWindowEnabled
     */
    public void setNavigateWindowEnabled(boolean navigateWindowEnabled) {
        this.navigateWindowEnabled = navigateWindowEnabled;
    }

    /**
     * @return true if this project handles renamed files.
     */
    public boolean isHandleRenamedFiles() {
        return handleRenamedFiles != null && handleRenamedFiles;
    }

    /**
     * @return true if merge commits are enabled.
     */
    public boolean isMergeCommitsEnabled() {
        return mergeCommitsEnabled != null && mergeCommitsEnabled;
    }

    /**
     * @param flag true if project should handle renamed files, false otherwise.
     */
    public void setHandleRenamedFiles(boolean flag) {
        this.handleRenamedFiles = flag;
    }

    /**
     * @return true if this project should have history cache.
     */
    public boolean isHistoryEnabled() {
        return historyEnabled != null && historyEnabled;
    }

    /**
     * @param flag true if project should have history cache, false otherwise.
     */
    public void setHistoryEnabled(boolean flag) {
        this.historyEnabled = flag;
    }

    /**
     * @param flag true if project's repositories should deal with merge commits.
     */
    public void setMergeCommitsEnabled(boolean flag) {
        this.mergeCommitsEnabled = flag;
    }

    /**
     * @return true if this project handles renamed files.
     */
    public boolean isHistoryBasedReindex() {
        return historyBasedReindex != null && historyBasedReindex;
    }

    /**
     * @param flag true if project should handle renamed files, false otherwise.
     */
    public void setHistoryBasedReindex(boolean flag) {
        this.historyBasedReindex = flag;
    }

    @VisibleForTesting
    public void clearProperties() {
        historyBasedReindex = null;
        mergeCommitsEnabled = null;
        historyEnabled = null;
        handleRenamedFiles = null;
    }

    /**
     * Return groups where this project belongs.
     *
     * @return set of groups|empty if none
     */
    public Set<Group> getGroups() {
        return groups;
    }

    public void setGroups(Set<Group> groups) {
        this.groups = groups;
    }

    /**
     * Adds a group where this project belongs.
     *
     * @param group group to add
     */
    public void addGroup(Group group) {
        while (group != null) {
            this.groups.add(group);
            group = group.getParent();
        }
    }

    public void setBugPage(String bugPage) {
        this.bugPage = bugPage;
    }

    public String getBugPage() {
        if (bugPage != null) {
            return bugPage;
        } else {
            return RuntimeEnvironment.getInstance().getBugPage();
        }
    }

    /**
     * Set the bug pattern to a new value.
     *
     * @param bugPattern the new pattern
     * @throws PatternSyntaxException when the pattern is not a valid regexp or
     * does not contain at least one capture group and the group does not
     * contain a single character
     */
    public void setBugPattern(String bugPattern) throws PatternSyntaxException {
        this.bugPattern = compilePattern(bugPattern);
    }

    public String getBugPattern() {
        if (bugPattern != null) {
            return bugPattern;
        } else {
            return RuntimeEnvironment.getInstance().getBugPattern();
        }
    }

    public String getReviewPage() {
        if (reviewPage != null) {
            return reviewPage;
        } else {
            return RuntimeEnvironment.getInstance().getReviewPage();
        }
    }

    public void setReviewPage(String reviewPage) {
        this.reviewPage = reviewPage;
    }

    public String getReviewPattern() {
        if (reviewPattern != null) {
            return reviewPattern;
        } else {
            return RuntimeEnvironment.getInstance().getReviewPattern();
        }
    }

    /**
     * Set the review pattern to a new value.
     *
     * @param reviewPattern the new pattern
     * @throws PatternSyntaxException when the pattern is not a valid regexp or
     * does not contain at least one capture group and the group does not
     * contain a single character
     */
    public void setReviewPattern(String reviewPattern) throws PatternSyntaxException {
        this.reviewPattern = compilePattern(reviewPattern);
    }

    /**
     * Fill the project with the current configuration where the applicable
     * project property has a default value.
     */
    public final void completeWithDefaults() {
        Configuration defaultCfg = new Configuration();
        final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        /*
         * Choosing strategy for properties (tabSize used as example here):
         * <pre>
         * this       cfg        defaultCfg   chosen value
         * ===============================================
         *  |5|        4             0             5
         *   0        |4|            0             4
         * </pre>
         *
         * The strategy is:
         * 1) if the project has some non-default value; use that
         * 2) if the project has a default value; use the provided configuration
         */
        if (getTabSize() == defaultCfg.getTabSize()) {
            setTabSize(env.getTabSize());
        }

        // Allow project to override global setting of renamed file handling.
        if (handleRenamedFiles == null) {
            setHandleRenamedFiles(env.isHandleHistoryOfRenamedFiles());
        }

        // Allow project to override global setting of history cache generation.
        if (historyEnabled == null) {
            setHistoryEnabled(env.isHistoryEnabled());
        }

        // Allow project to override global setting of navigate window.
        if (navigateWindowEnabled == null) {
            setNavigateWindowEnabled(env.isNavigateWindowEnabled());
        }

        // Allow project to override global setting of merge commits.
        if (mergeCommitsEnabled == null) {
            setMergeCommitsEnabled(env.isMergeCommitsEnabled());
        }

        if (bugPage == null) {
            setBugPage(env.getBugPage());
        }
        if (bugPattern == null) {
            setBugPattern(env.getBugPattern());
        }

        if (reviewPage == null) {
            setReviewPage(env.getReviewPage());
        }
        if (reviewPattern == null) {
            setReviewPattern(env.getReviewPattern());
        }

        if (historyBasedReindex == null) {
            setHistoryBasedReindex(env.isHistoryBasedReindex());
        }
    }

    /**
     * Get the project for a specific file.
     *
     * @param path the file to lookup (relative to source root)
     * @return the project that this file belongs to (or null if the file
     * doesn't belong to a project)
     */
    public static Project getProject(String path) {
        // Try to match each project path as prefix of the given path.
        final RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            final String lpath = Util.fixPathIfWindows(path);
            for (Project p : env.getProjectList()) {
                String projectPath = p.getPath();
                if (projectPath == null) {
                    LOGGER.log(Level.WARNING, "Path of project {0} is not set", p.getName());
                    return null;
                }

                // Check if the project's path is a prefix of the given
                // path. It has to be an exact match, or the project's path
                // must be immediately followed by a separator. "/foo" is
                // a prefix for "/foo" and "/foo/bar", but not for "/foof".
                if (lpath.startsWith(projectPath)
                        && (projectPath.length() == lpath.length()
                        || lpath.charAt(projectPath.length()) == '/')) {
                    return p;
                }
            }
        }

        return null;
    }

    /**
     * Get the project for a specific file.
     *
     * @param file the file to lookup
     * @return the project that this file belongs to (or {@code null} if the file doesn't belong to a project)
     */
    public static Project getProject(File file) {
        Project ret = null;
        try {
            ret = getProject(RuntimeEnvironment.getInstance().getPathRelativeToSourceRoot(file));
        } catch (FileNotFoundException e) { // NOPMD
            // ignore if not under source root
        } catch (ForbiddenSymlinkException e) {
            LOGGER.log(Level.FINER, e.getMessage());
            // ignore
        } catch (IOException e) { // NOPMD
            // problem has already been logged, just return null
        }
        return ret;
    }

    /**
     * Returns project object by its name, used in webapp to figure out which
     * project is to be searched.
     *
     * @param name name of the project
     * @return project that fits the name
     */
    public static Project getByName(String name) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            Project proj;
            if ((proj = env.getProjects().get(name)) != null) {
                return (proj);
            }
        }
        return null;
    }

    @Override
    public int compareTo(Project p2) {
        return getName().toUpperCase(Locale.ROOT).compareTo(
                p2.getName().toUpperCase(Locale.ROOT));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + (this.name == null ? 0 :
                this.name.toUpperCase(Locale.ROOT).hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Project other = (Project) obj;

        int numNull = (name == null ? 1 : 0) + (other.name == null ? 1 : 0);
        switch (numNull) {
            case 0:
                return name.toUpperCase(Locale.ROOT).equals(
                        other.name.toUpperCase(Locale.ROOT));
            case 1:
                return false;
            default:
                return true;
        }
    }
}
