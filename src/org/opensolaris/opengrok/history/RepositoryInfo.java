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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.history;

import java.io.Serializable;

/**
 * Class to contain the common info for a repository. This object
 * will live on the server and the client side, so don't add logic
 * that will only work on one side in this object.
 *
 * @author Trond Norbye
 */
public class RepositoryInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    protected String directoryName;
    protected boolean working;
    protected String type;
    protected boolean remote;
    protected String datePattern;

    /**
     * Empty constructor to support serialization.
     */
    public RepositoryInfo() {
        super();
    }

    public RepositoryInfo(RepositoryInfo orig) {
        this.directoryName = orig.directoryName;
        this.type = orig.type;
        this.working = orig.isWorking();
        this.remote = orig.isRemote();
        this.datePattern = orig.datePattern;
    }

    /**
     * Get the name of the root directory for this repository.
     * @return the name of the root directory
     */
    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * Specify the name of the root directory for this repository.
     * @param directoryName the new name of the root directory
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    /**
     * Returns true if this repository is usable in this context (for SCM
     * systems that use external binaries, the binary must be availabe etc)
     * 
     * @return true if the HistoryGuru may use the repository
     */
    public boolean isWorking() {
        return working;
    }

    /**
     * Set the property working
     *
     * @param working
     */
    public void setWorking(boolean working) {
        this.working = working;
    }

    /**
     * Is the history and version information for this repository stored on
     * a remote server?
     * 
     * @return true if the history is stored on a remote server.
     */
    public boolean isRemote() {
        return remote;
    }

    /**
     * Set the property remote
     * @param remote
     */
    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    /**
     * get property type
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * Set property type
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }

    public void setDatePattern(String datePattern) {
        this.datePattern = datePattern;
    }

    public String getDatePattern() {
        return datePattern;
    }
}

