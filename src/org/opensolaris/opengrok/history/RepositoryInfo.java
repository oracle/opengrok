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

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;

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
    protected Boolean working;
    protected String type;
    protected boolean remote;
    protected String datePattern;
    protected String cmd;

    /**
     * Empty constructor to support serialization.
     */
    public RepositoryInfo() {
        super();
    }

    public RepositoryInfo(RepositoryInfo orig) {
        this.directoryName = orig.directoryName;
        this.type = orig.type;
        this.working = Boolean.valueOf(orig.isWorking());
        this.remote = orig.isRemote();
        this.datePattern = orig.datePattern;
        this.cmd = orig.cmd;
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
        return working != null && working.booleanValue();
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

    /**
     * Set the name of the external client command that should be used to
     * access the repository wrt. the given parameters. Does nothing, if this
     * repository's <var>cmd</var> has been already set (i.e. has a
     * none-{@code null} value).
     *
     * @param propertyKey property key to lookup the corresponding system property.
     * @param fallbackCommand the command to use, if lookup fails.
     * @return the command to use.
     * @see #cmd
     */
    protected String ensureCommand(String propertyKey, String fallbackCommand) {
        if (cmd != null) {
            return cmd;
        }
        cmd = RuntimeEnvironment.getInstance()
            .getRepoCmd(this.getClass().getCanonicalName());
        if (cmd == null) {
            cmd = System.getProperty(propertyKey, fallbackCommand);
            RuntimeEnvironment.getInstance()
                .setRepoCmd(this.getClass().getCanonicalName(), cmd);
        }
        return cmd;
    }
}

