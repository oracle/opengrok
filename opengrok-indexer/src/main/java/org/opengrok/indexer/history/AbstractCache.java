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
 * Copyright (c) 20XX, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.TandemPath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * TODO
 */
public abstract class AbstractCache implements Cache {

    public boolean hasCacheForFile(File file) throws HistoryException {
        try {
            return getCachedFile(file).exists();
        } catch (ForbiddenSymlinkException ex) {
            LOGGER.log(Level.FINER, ex.getMessage());
            return false;
        }
    }

    /**
     * Get a <code>File</code> object describing the cache file.
     *
     * @param file the file to find the cache for
     * @return file that might contain cached object for <code>file</code>
     */
    File getCachedFile(File file) throws HistoryException, ForbiddenSymlinkException {

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        StringBuilder sb = new StringBuilder();
        sb.append(env.getDataRootPath());
        sb.append(File.separatorChar);
        sb.append(getCacheDirName());

        try {
            String add = env.getPathRelativeToSourceRoot(file);
            if (add.length() == 0) {
                add = File.separator;
            }
            sb.append(add);
        } catch (IOException e) {
            throw new HistoryException("Failed to get path relative to source root for " + file, e);
        }

        return new File(TandemPath.join(sb.toString(), ".gz"));
    }

    public List<String> clearCache(Collection<String> repositories) {
        List<String> clearedRepos = new ArrayList<>();

        for (Repository r : HistoryGuru.getInstance().getReposFromString(repositories)) {
            try {
                this.clear(r);
                clearedRepos.add(r.getDirectoryName());
                // TODO: report what kind of cache it is
                LOGGER.log(Level.INFO,"{1} cache for {0} cleared.",
                        new Object[]{r.getDirectoryName(), this.getInfo()});
            } catch (HistoryException e) {
                LOGGER.log(Level.WARNING,
                        "Clearing cache for repository {0} failed: {1}",
                        new Object[]{r.getDirectoryName(), e.getLocalizedMessage()});
            }
        }

        return clearedRepos;
    }

    /**
     * Attempt to delete file with its parent.
     * @param file file to delete
     */
    static void clearWithParent(File file) {
        File parent = file.getParentFile();

        if (!file.delete() && file.exists()) {
            LOGGER.log(Level.WARNING, "Failed to remove obsolete cache-file: {0}", file.getAbsolutePath());
        }

        if (parent.delete()) {
            LOGGER.log(Level.FINE, "Removed empty cache dir:{0}", parent.getAbsolutePath());
        }
    }

    public void clearFile(String path) {
        File historyFile;
        try {
            historyFile = getCachedFile(new File(RuntimeEnvironment.getInstance().getSourceRootPath() + path));
        } catch (ForbiddenSymlinkException ex) {
            LOGGER.log(Level.FINER, ex.getMessage());
            return;
        } catch (HistoryException ex) {
            LOGGER.log(Level.WARNING, String.format("cannot get history file for file %s", path), ex);
            return;
        }

        clearWithParent(historyFile);
    }
}
