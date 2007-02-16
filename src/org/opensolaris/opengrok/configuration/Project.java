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
 *
 * @author Trond Norbye
 */
public class Project {
    private String path;
    private String description;
    
    public String getDescription() {
        return description;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    /** Creates a new instance of Project */
    public Project() {
    }
    
    public Project(String description, String path) {
        this.description = description;
        this.path = path;
    }
}
