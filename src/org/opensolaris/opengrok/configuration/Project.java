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
 * Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.configuration;

import java.io.File;

/**
 * Placeholder for the information that builds up a project
 */ 
public class Project {
    private String path;
    // this variable is very important, since it's used as the project identifier all over xrefs and webapp
    private String description;

    /**
     * Size of tabs in this project. Used for displaying the xrefs correctly in
     * projects with non-standard tab size.
     */
    private int tabSize;
    
    /**
     * Get a textual description of this project
     * @return a textual description of the project
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the path (relative from source root) where this project is located
     * @return the relative path
     */
    public String getPath() {
        return path;
    }

    /**
     * Get the project id
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
     * Set a textual description of this project, prefferably don't use " , " in the name, since it's used as delimiter for more projects
     * @param description a textual description of the project
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Set the path (relative from source root) this project is located
     * It seems that you should ALWAYS prefix the path with current file.separator , current environment should always have it set up
     * @param path the relative path from source sroot where this project is
     *             located.
     */
    public void setPath(String path) {        
        this.path = path;
    }

    /**
     * Set tab size for this project. Used for expanding tabs to spaces
     * in xrefs.
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
     * Get the project for a specific file
     * @param path the file to lookup (relative from source root)
     * @return the project that this file belongs to (or null if the file 
     *         doesn't belong to a project)
     */
    public static Project getProject(String path) {
        Project ret = null;
        String lpath=path;
        if (System.getProperty("file.separator").compareTo("/")!=0) {lpath=path.replace(File.separatorChar, '/');}
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            for (Project proj : env.getProjects()) {                
                if (lpath.indexOf(proj.getPath()) == 0) {
                    ret = proj;
                }
            }
        }
        return ret;
    }

    /**
     * Get the project for a specific file
     * @param file the file to lookup
     * @return the project that this file belongs to (or null if the file 
     *         doesn't belong to a project)
     */
    public static Project getProject(File file) {
        Project ret = null;
        String root = RuntimeEnvironment.getInstance().getSourceRootFile().getAbsolutePath();
        String me = file.getAbsolutePath();
        if (me.startsWith(root)) {
            ret = getProject(me.substring(root.length()));
        }
        return ret;
    }

    /**
     * Returns project object by its description, used in webapp to figure out which project is to be searched
     * @param desc description of the project
     * @return project that fits the description
     */
    public static Project getByDescription(String desc) {
        Project ret = null;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasProjects()) {
            for (Project proj : env.getProjects()) {                
                if (desc.indexOf(proj.getDescription()) == 0) {
                    ret = proj;
                }
            }
        }
        return ret;
    }    
}
