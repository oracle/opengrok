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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.ClassUtil;

/**
 * Class to contain the common info for a repository. This object will live on
 * the server and the client side, so don't add logic that will only work on one
 * side in this object.
 *
 * @author Trond Norbye
 */
public class RepositoryInfo implements Serializable {

    static {
        ClassUtil.remarkTransientFields(RepositoryInfo.class);
    }

    private static final long serialVersionUID = 3L;

    // dummy to avoid storing absolute path in XML encoded configuration
    // Do not use this member.
    private transient String directoryName;
    
    private String directoryNameRelative;
    protected Boolean working;
    protected String type;
    protected boolean remote;
    protected String[] datePatterns = new String[0];
    protected String parent;
    protected String branch;
    protected String currentVersion;
    private String realSourceRoot;

    /**
     * format used for printing the date in {@code currentVersion}
     */
    protected static final SimpleDateFormat outputDateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm Z");

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
        this.realSourceRoot = orig.realSourceRoot;
    }

    /**
     * @return relative path to source root
     */
    public String getDirectoryNameRelative() {
        return directoryNameRelative;
    }

    /**
     * Set relative path to source root
     * @param dir directory
     */
    public void setDirectoryNameRelative(String dir) {
        this.directoryNameRelative = dir;
    }

    /**
     * Get the name of the root directory for this repository.
     *
     * @return the name of the root directory
     */
    public String getDirectoryName() {
        Path dirname = null;
        if (realSourceRoot == null) {
            dirname = 
               Paths.get(RuntimeEnvironment.getInstance().getSourceRootPath(), 
                       directoryNameRelative);
        } else {
            dirname = Paths.get(realSourceRoot, directoryNameRelative);
        }

        return dirname.toString();
    }

    /**
     * Specify the name of the root directory for this repository.
     *
     * @param dir the new name of the root directory. 
     * Can be absolute path (/full/path/to/directory) 
     * or relative to source root (/directory).
     */
    public void setDirectoryName(String dir) {
        
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String rp = env.getSourceRootPath();
        if (dir.startsWith(env.getSourceRootPath())) {
            this.directoryNameRelative = dir.substring(env.getSourceRootPath().length());
        } else {
            // For a simple relative path (/directory) there should
            // only be one file separator character. When there is
            // more than one file separator character, then this is
            // assumed to be the full path to the root of the
            // repository. It happened to not start with the source
            // path from the command line likely due to symbolic linking.
            
            String sd = dir.substring(1); // skip over leading '/'
            if ( sd.indexOf('/') == -1 && sd.indexOf('\\') == -1) {
                directoryNameRelative = dir;  // simple directory name (eg. /myDir)
            } else {
                File folderPath = new File(dir);
                realSourceRoot = folderPath.getParent();
                directoryNameRelative = "/" + folderPath.getName();
            }
        }
    }
    
    /**
     * Obtain source root of the repository
     * 
     * @return source root of repository
     */
    public String getSourceRoot() {
        return (realSourceRoot != null) // only has value when symbolic link found
                ? realSourceRoot 
                : RuntimeEnvironment.getInstance().getSourceRootPath();
    }
    
    /**
     * Set source root of repository.
     * This is mostly for rebuilding this object via Configuration instantiation
     * from configuration.xml
     * @param path to repository root
     */
    public void setSourceRoot(String path) {
        realSourceRoot = path;
    }
    /**
     * Returns true if this repository is usable in this context (for SCM
     * systems that use external binaries, the binary must be available etc)
     *
     * @return true if the HistoryGuru may use the repository
     */
    public boolean isWorking() {
        return working != null && working;
    }

    /**
     * Set the property working
     *
     * @param working
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
     * Set the property remote
     *
     * @param remote
     */
    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    /**
     * get property type
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Set property type
     *
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * get property parent
     *
     * @return parent
     */
    public String getParent() {
        return parent;
    }

    /**
     * Set property parent
     *
     * @param parent
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
}
