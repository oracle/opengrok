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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2010, Trond Norbye <trond.norbye@gmail.com>. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.opengrok.indexer.configuration.CommandTimeoutType;

/**
 * Access to a Git repository.
 *
 * @author Trond Norbye &lt;trond.norbye@gmail.com&gt;
 */
public class RepoRepository extends Repository {
    // TODO: cache all of the GitRepositories within the class

    private static final long serialVersionUID = 1L;
    /**
     * The property name used to obtain the client command for this repository.
     */
    public static final String CMD_PROPERTY_KEY = "org.opengrok.indexer.history.repo";
    /**
     * The command to use to access the repository if none was given explicitly.
     */
    public static final String CMD_FALLBACK = "repo";

    @SuppressWarnings("this-escape")
    public RepoRepository() {
        type = "repo";
        setWorking(Boolean.TRUE);

        ignoredDirs.add(".repo");
    }

    @Override
    public boolean isWorking() {
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        return true;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File f = new File(file, ".repo");
            return f.exists() && f.isDirectory();
        }
        return false;
    }

    @Override
    boolean supportsSubRepositories() {
        return true;
    }

    @Override
    boolean fileHasHistory(File file) {
        return false;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return false;
    }

    @Override
    History getHistory(File file) {
        throw new UnsupportedOperationException("Should never be called!");
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {
        throw new UnsupportedOperationException("Should never be called!");
    }

    @Override
    boolean fileHasAnnotation(File file) {
        return false;
    }

    @Override
    Annotation annotate(File file, String revision) {
        throw new UnsupportedOperationException("Should never be called!");
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        return null;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) {
        return null;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        return null;
    }
}
