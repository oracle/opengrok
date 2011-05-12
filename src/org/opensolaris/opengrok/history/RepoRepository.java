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
 * Copyright (c) 2010, Trond Norbye <trond.norbye@gmail.com>. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.opensolaris.opengrok.util.Executor;

/**
 * Access to a Git repository.
 *
 * @author Trond Norbye <trond.norbye@gmail.com>
 * @todo cache all of the GitRepositories within the class
 */
public class RepoRepository extends Repository {

    private static final long serialVersionUID = 1L;
    /** The property name used to obtain the client command for this repository.*/
    public static final String CMD_PROPERTY_KEY = 
        "org.opensolaris.opengrok.history.repo";
    /** The command to use to access the repository if none was given explicitly */
    public static final String CMD_FALLBACK = "repo";

    public RepoRepository() {
        type = "repo";
        setWorking(Boolean.TRUE);
    }

    @Override
    public void setDirectoryName(String directoryName) {
        super.setDirectoryName(directoryName);
    }

    @Override
    public boolean isWorking() {
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        return true;
    }

    @Override
    public void update() throws IOException {
        File directory = new File(getDirectoryName());
        List<String> cmd = new ArrayList<String>();
        ensureCommand(CMD_PROPERTY_KEY, CMD_FALLBACK);
        cmd.add(this.cmd);
        cmd.add("sync");

        Executor executor = new Executor(cmd, directory);
        if (executor.exec() != 0) {
            throw new IOException(executor.getErrorString());
        }
    }

    @Override
    boolean isRepositoryFor(File file) {
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
    InputStream getHistoryGet(String parent, String basename, String rev) {
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

}
