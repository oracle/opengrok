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
 * Copyright (c) 2020, Anatoly Akkerman <akkerman@gmail.com>, <anatoly.akkerman@twosigma.com>.
 */
package org.opengrok.indexer.history;

import org.opengrok.indexer.logger.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RepositoryLookup uncached implementation (original logic taken from HistoryGuru.getRepository).
 */
public class RepositoryLookupUncached implements RepositoryLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryLookupUncached.class);

    @Override
    public Repository getRepository(final Path filePath, Set<String> repoParentDirs,
        Map<String, Repository> repositories, PathCanonicalizer canonicalizer) {
        Path path = filePath;

        while (path != null) {
            String nextPath = path.toString();
            for (String rootKey : repoParentDirs) {
                String rel;
                try {
                    rel = canonicalizer.resolve(path, path.getFileSystem().getPath(rootKey));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e, () ->
                        "Failed to get relative to canonical for " + nextPath);
                    return null;
                }
                Repository repo;
                if (rel.equals(nextPath)) {
                    repo = repositories.get(nextPath);
                } else {
                    String inRootPath = Paths.get(rootKey, rel).toString();
                    repo = repositories.get(inRootPath);
                }
                if (repo != null) {
                    return repo;
                }
            }

            path = path.getParent();
        }

        return null;
    }

    @Override
    public void clear() {
        // Do nothing
    }

    @Override
    public void repositoriesRemoved(Collection<Repository> removedRepos) {
        // Do nothings
    }
}
