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

/**
 * Placeholder for the information that builds up a project
 */ 
public class Project {
    private String path;
    private String description;
    
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
     * Set a textual description of this project
     * @param description a textual description of the project
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Set the path (relative from source root) this project is located
     * @param path the relative path from source sroot where this project is
     *             located.
     */
    public void setPath(String path) {
        this.path = path;
    }
    
    /**
     * Creates a new instance of Project
     */
    public Project() {
    }
    
    /**
     * Create a new instance of Project with a given description and path
     * @param description the description of this project
     * @param path the path to where this project is located (relative from source root)
     */
    public Project(String description, String path) {
        this.description = description;
        this.path = path;
    }
}
