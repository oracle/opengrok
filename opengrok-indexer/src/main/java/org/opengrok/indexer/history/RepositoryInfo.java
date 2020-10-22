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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ClassUtil;
import org.opengrok.indexer.util.DTOElement;
import org.opengrok.indexer.util.PathUtils;

/**
 * Class to contain the common info for a repository. This object will live on
 * the server and the client side, so don't add logic that will only work on one
 * side in this object.
 *
 * @author Trond Norbye
 */
public class RepositoryInfo implements Serializable {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(RepositoryInfo.class);

    static {
        ClassUtil.remarkTransientFields(RepositoryInfo.class);
    }

    private static final long serialVersionUID = 3L;

    @DTOElement
    private String directoryNameRelative;
    private transient String directoryNameCanonical;

    @DTOElement
    protected Boolean working;
    @DTOElement
    protected String type;  // type of the repository, should be unique
    @DTOElement
    protected boolean remote;
    protected String[] datePatterns = new String[0];
    @DTOElement
    protected String parent;
    @DTOElement
    protected String branch;
    @DTOElement
    protected String currentVersion;
    @DTOElement
    private boolean handleRenamedFiles;
    @DTOElement
    private boolean historyEnabled;

    /**
     * Empty constructor to support serialization.
     */
    public RepositoryInfo() {
        super();
    }

    public RepositoryInfo(RepositoryInfo orig) {
        this.directoryNameRelative = orig.directoryNameRelative;
        this.type = orig.type;
        this.working = orig.isWorking();
        this.remote = orig.isRemote();
        this.datePatterns = orig.datePatterns;
        this.parent = orig.parent;
        this.branch = orig.branch;
        this.currentVersion = orig.currentVersion;
    }

    /**
     * @return true if the repository handles renamed files, false otherwise.
     */
    public boolean isHandleRenamedFiles() {
        return this.handleRenamedFiles;
    }

    /**
     * @param flag true if the repository should handle renamed files, false otherwise.
     */
    public void setHandleRenamedFiles(boolean flag) {
        this.handleRenamedFiles = flag;
    }

    /**
     * @return true if the repository should have history cache.
     */
    public boolean isHistoryEnabled() {
        return this.historyEnabled;
    }

    public void setHistoryEnabled(boolean flag) {
        this.historyEnabled = flag;
    }

    /**
     * @return relative path to source root
     */
    public String getDirectoryNameRelative() {
        return directoryNameRelative;
    }

    /**
     * Set relative path to source root.
     * @param dir directory
     */
    public void setDirectoryNameRelative(String dir) {
        this.directoryNameRelative = dir;
        this.directoryNameCanonical = null;
    }

    /**
     * Get the name of the root directory for this repository.
     *
     * @return the name of the root directory
     */
    public String getDirectoryName() {
        return Paths.get(RuntimeEnvironment.getInstance().getSourceRootPath(),
                directoryNameRelative).toString();
    }

    /**
     * Get the canonical {@link #getDirectoryName()} of the root directory for
     * this repository.
     */
    String getCanonicalDirectoryName() throws IOException {
        if (directoryNameCanonical == null) {
            directoryNameCanonical = new File(getDirectoryName()).getCanonicalPath();
        }
        return directoryNameCanonical;
    }

    /**
     * Specify the name of the root directory for this repository.
     *
     * @param dir the new root directory
     */
    public void setDirectoryName(File dir) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String rootPath = env.getSourceRootPath();
        String path;
        String originalPath = dir.getPath();
        try {
            path = PathUtils.getRelativeToCanonical(originalPath, rootPath);
            // OpenGrok has a weird convention that directoryNameRelative must start with a path separator,
            // as it is elsewhere directly appended to env.getSourceRootPath() and also stored as such.
            if (!path.equals(originalPath)) {
                path = File.separator + path;
            }
        } catch (IOException e) {
            path = originalPath;
            LOGGER.log(Level.SEVERE, String.format("Failed to get canonical path for %s", path), e);
        }

        if (path.startsWith(rootPath)) {
            setDirectoryNameRelative(path.substring(rootPath.length()));
        } else {
            setDirectoryNameRelative(path);
        }
    }

    /**
     * Returns true if this repository is usable in this context (for SCM
     * systems that use external binaries, the binary must be available etc).
     *
     * @return true if the HistoryGuru may use the repository
     */
    public boolean isWorking() {
        return working != null && working;
    }

    /**
     * Set the property working.
     *
     * @param working is repository working
     */
    public void setWorking(Boolean working) {
        this.working = working;
    }

    /**
     * Is the history and version information for this repository stored on a
     * remote server?
     *
     * @return true if the history is stored on a remote server.
     */
    public boolean isRemote() {
        return remote;
    }

    /**
     * Set the property remote.
     *
     * @param remote is remote repository
     */
    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    /**
     * Get property type.
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Set property type.
     *
     * @param type repository type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get property parent.
     *
     * @return parent
     */
    public String getParent() {
        return parent;
    }

    /**
     * Set property parent.
     *
     * @param parent parent of the repository
     */
    public void setParent(String parent) {
        this.parent = parent;
    }

    public void setDatePatterns(String[] datePatterns) {
        this.datePatterns = datePatterns;
    }

    public String[] getDatePatterns() {
        return datePatterns;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    /**
     * Fill configurable properties from associated project (if any) or Configuration.
     */
    public void fillFromProject() {
        Project proj = Project.getProject(getDirectoryNameRelative());
        if (proj != null) {
            setHistoryEnabled(proj.isHistoryEnabled());
            setHandleRenamedFiles(proj.isHandleRenamedFiles());
        } else {
            RuntimeEnvironment env = RuntimeEnvironment.getInstance();

            setHistoryEnabled(env.isHistoryEnabled());
            setHandleRenamedFiles(env.isHandleHistoryOfRenamedFiles());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RepositoryInfo)) {
            return false;
        }
        
        RepositoryInfo ri = (RepositoryInfo) obj;
        
        // Directory paths should be unique.
        if (ri.getDirectoryNameRelative() != null && this.getDirectoryNameRelative() != null) {
            return ri.getDirectoryNameRelative().equals(this.getDirectoryNameRelative());
        } else {
            return (ri.getDirectoryNameRelative() == null && this.getDirectoryNameRelative() == null);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.directoryNameRelative);
        return hash;
    }
}
