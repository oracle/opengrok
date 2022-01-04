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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Interface for finding enclosing Repository for a given Path, used by HistoryGuru.
 * <p>
 * Two implementations exists
 * - uncached, (legacy behavior) extracted from HistoryGuru into a stand-alone impl
 * - cached, new implementation, which reduces number of expensive canonicalization calls
 * <p>
 * We preserve both cached and uncached implementations in order to verify correctness of the cached impl.
 */
public interface RepositoryLookup {

    static RepositoryLookupCached cached() {
        return new RepositoryLookupCached();
    }

    static RepositoryLookupUncached uncached() {
        return new RepositoryLookupUncached();
    }

    /**
     * This interface allows intercepting PathUtils.getRelativeToCanonical in order to measure the impact of caching.
     *
     * In practice, PathUtils::getRelativeToCanonical is the implementation of this interface
     */
    @FunctionalInterface
    interface PathCanonicalizer {
        String resolve(Path path, Path relativeTo) throws IOException;
    }

    /**
     * Find enclosing repository for a given path.
     *
     * @param path path to find enclosing repository for
     * @param repoParentDirs Set of repository parent dirs (parents of repository roots)
     * @param repositories Map of repository root to Repository
     * @param canonicalizer PathCanonicalizer reference
     * @return enclosing Repository or null if not found
     */
    Repository getRepository(Path path, Set<String> repoParentDirs, Map<String, Repository> repositories,
        PathCanonicalizer canonicalizer);

    /**
     * Lifecycle method to clear internal cache (if any) when repositories are invalidated.
     */
    void clear();

    /**
     * Lifecycle method to invalidate any cache entries that point to given repositories that are being removed.
     *
     * @param removedRepos collection of repositories
     */
    void repositoriesRemoved(Collection<Repository> removedRepos);
}
