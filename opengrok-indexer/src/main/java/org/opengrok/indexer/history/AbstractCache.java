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
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Class to hold code shared between various cache implementations,
 * notably {@link FileHistoryCache} and {@link FileAnnotationCache}.
 */
public abstract class AbstractCache implements Cache {



    public boolean hasCacheForFile(File file) throws CacheException {
        try {
            return getCachedFile(file).exists();
        } catch (CacheException ex) {
            throw new CacheException(ex);
        }
    }

    /**
     * Get a <code>File</code> object describing the cache file.
     *
     * @param file the file to find the cache for
     * @return file that might contain cached object for <code>file</code>
     * @throws CacheException on error
     */
    File getCachedFile(File file) throws CacheException {

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
        } catch (ForbiddenSymlinkException | IOException e) {
            throw new CacheException(String.format("Failed to get path relative to source root for '%s'", file), e);
        }

        String suffix = getCacheFileSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            return new File(TandemPath.join(sb.toString(), suffix));
        }

        return new File(sb.toString());
    }

    public List<String> clearCache(Collection<RepositoryInfo> repositories) {
        List<String> clearedRepos = new ArrayList<>();

        for (RepositoryInfo repo : repositories) {
            try {
                this.clear(repo);
                clearedRepos.add(repo.getDirectoryNameRelative());
                LOGGER.log(Level.INFO, "{1} cache for ''{0}'' cleared.",
                        new Object[]{repo.getDirectoryName(), this.getInfo()});
            } catch (CacheException e) {
                LOGGER.log(Level.WARNING,
                        "Clearing cache for repository ''{0}'' failed: {1}",
                        new Object[]{repo.getDirectoryName(), e.getLocalizedMessage()});
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
            LOGGER.log(Level.WARNING, "Failed to remove obsolete cache-file: ''{0}''", file.getAbsolutePath());
        }

        if (parent.delete()) {
            LOGGER.log(Level.FINE, "Removed empty cache dir: ''{0}''", parent.getAbsolutePath());
        }
    }

    public void clearFile(String path) {
        File historyFile;
        try {
            historyFile = getCachedFile(new File(RuntimeEnvironment.getInstance().getSourceRootPath() + path));
        } catch (CacheException ex) {
            LOGGER.log(Level.WARNING, String.format("cannot get cached file for file '%s'"
                    + " - the cache entry will not be cleared", path), ex);
            return;
        }

        clearWithParent(historyFile);
    }

    public String getCacheFileSuffix() {
        return "";
    }
}
