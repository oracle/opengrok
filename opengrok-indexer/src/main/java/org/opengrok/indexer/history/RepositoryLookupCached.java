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
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RepositoryLookupCached implements RepositoryLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryLookupCached.class);

    /**
     * Cache of directories to their enclosing repositories.
     *
     * This cache will contain Optional.empty() for any directory that has so far not been mapped to a repo.
     * See findRepository for more information.
     */
    private final ConcurrentMap<String, Optional<Repository>> dirToRepoCache = new ConcurrentHashMap<>();

    /**
     * Find enclosing Repository for a given file, limiting canonicalization operations
     * to a given set of Repository parent dirs.
     * Search results are cached in an internal cache to speed up subsequent lookups for all directories
     * between path and the root path of the enclosing Repository (if found). Negative results are also
     * cached as empty Optional values in the cache. However, negative results are not treated as definitive.
     * <p>
     * The cache is only invalidated on Repository invalidation or removal
     * which means that if the filesystem changes under HistoryGuru, it may return stale results
     *
     * @param filePath      path to find enclosing repository for
     * @param repoRoots     set of Repository parent dirs
     * @param repositories  map of repository roots to repositories
     * @param canonicalizer path canonicalizer implementation to use
     * @return Optional Repository (empty if not found)
     */
    private Optional<Repository> findRepository(final Path filePath, final Set<String> repoRoots,
        Map<String, Repository> repositories, PathCanonicalizer canonicalizer) {
        Path path = filePath;
        FileSystem fileSystem = filePath.getFileSystem();
        Optional<Repository> maybeRepo = Optional.empty();

        // If we find repo mapping, we backfill the cache with these entries as well
        List<String> backfillPaths = new ArrayList<>();
        // Walk up the file's path until we find a matching enclosing Repository
        while (maybeRepo.isEmpty() && path != null) {
            String nextPath = path.toString();
            boolean isDirectory = Files.isDirectory(path);
            if (isDirectory) {
                /*
                 * Only store (and lookup) directory entries in the cache to avoid cache explosion
                 * This may fail to find a Repository (e.g. nextPath is inside a root), so this will
                 * insert an Optional.empty() into the map. However, as we walk up, we may eventually
                 * find a Repo, in which case we need to backfill these placeholder Optional.empty() entries
                 * with the final answer
                 */
                maybeRepo = dirToRepoCache
                    .computeIfAbsent(nextPath, p -> repoForPath(p, repoRoots, repositories, fileSystem, canonicalizer));
            } else {
                maybeRepo = repoForPath(nextPath, repoRoots, repositories, fileSystem, canonicalizer);
            }
            /*
             * Given that this class has to be thread-safe, Optional.empty() value cannot be assumed to be a definitive
             * answer that a given directory is not inside a repo, because of backfilling.
             * E.g.
             * During lookup of repository /a/b/c/d in repository /a, the following happens:
             * - /a/b/c/d: no entry in the cache, insert empty
             * - /a/b/c: no entry in the cache, insert empty
             * - /a/b: no entry in the cache, insert empty
             * - /a: found Repo(a), insert it into cache
             *
             * Now, the code needs to replace (backfill) entries for /a/b/c/d, /a/b/c and /a/b
             * However, before that happens, a concurrent findRepository() call can come in and find empty mappings
             * for /a/b/c/d, /a/b/c and /a/b, before they were backfilled. So, instead of returning an incorrect
             * cached result, we continue confirming negative result fully. This is a reasonable choice because
             * most lookups will be made for files that do live inside repositories, so non-empty cached values will
             * be the dominant cache hits.
             *
             * An alternative to provide definitive negative cache result would be to use a special sentinel
             * Repository object that represents NO_REPOSITORY as a definitive negative cached value, while Optional.empty
             * would represent a tentative negative value.
             */
            if (maybeRepo.isEmpty()) {
                if (isDirectory) {
                    backfillPaths.add(nextPath);
                }
                path = path.getParent();
            }
        }
        /*
         * If the lookup was for /a/b/c/d and we found the repo at /a, then
         *   - /a -> Repo(/a) is done by the computeIfAbsent call
         * And backfill fills in:
         *   - /a/b -> Repo(/a)
         *   - /a/b/c-> Repo(/a)
         */
        maybeRepo.ifPresent(repo ->
            // Replace empty values with newly found Repository
            backfillPaths.forEach(backfillPath -> dirToRepoCache.replace(backfillPath, Optional.empty(), Optional.of(repo))));

        return maybeRepo;
    }

    /**
     * Try to find path's enclosing repository by attempting to canonicalize it relative to
     * a given set of repository roots. This method can be pretty expensive, with complexity
     * of O(N*M) where N is path's depth and M is number of repository parent dirs.
     *
     * @param path           path to find enclosing Repository for
     * @param repoParentDirs limit canonicalization search only to these Repository parent dirs
     * @param canonicalizer  path canonicalizer implementation to use
     * @return Optional of matching Repository (empty if not found)
     */
    private Optional<Repository> repoForPath(String path, Set<String> repoParentDirs,
        Map<String, Repository> repositories,
        FileSystem fileSystem, PathCanonicalizer canonicalizer) {
        Optional<Repository> repo = Optional.ofNullable(repositories.get(path));

        if (repo.isPresent()) {
            return repo;
        }

        for (String repoRoot : repoParentDirs) {
            try {
                String rel = canonicalizer.resolve(fileSystem.getPath(path), fileSystem.getPath(repoRoot));
                if (!rel.equals(path)) {
                    // Resolve the relative against root key and look it up
                    String canonicalPath = Paths.get(repoRoot, rel).toString();
                    repo = Optional.ofNullable(repositories.get(canonicalPath));
                    if (repo.isPresent()) {
                        return repo;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, e, () ->
                    "Failed to get relative to canonical for " + path + " and " + repoRoot);
            }
        }
        return Optional.empty();
    }

    @Override
    public Repository getRepository(Path path, Set<String> repoParentDirs, Map<String, Repository> repositories,
        PathCanonicalizer canonicalizer) {
        Comparator<String> stringComparator = String::compareTo;
        Comparator<String> reversedComparator = stringComparator.reversed();
        TreeSet<String> allRoots = new TreeSet<>(repoParentDirs);
        String pathStr = path.toString();
        // parentRepoDirs are canonicalized, so we need to filter them against canonical path representation
        Path canonicalPath;
        try {
            canonicalPath = path.toRealPath();
        } catch (IOException e) {
            canonicalPath = path.normalize().toAbsolutePath();
        }

        if (!canonicalPath.equals(path)) {
            pathStr = canonicalPath.toString();
        }

        /*
         * Find all potential Repository parent dirs by matching their roots to file's prefix
         * This is useful to limit the number of iterations in repoForPath call.
         * Store matches in reverse-ordered TreeSet so that longest prefixes appear first
         * (There may be multiple entries if we allow nested repositories)
         */
        TreeSet<String> filteredParentDirs = allRoots.stream().filter(pathStr::startsWith)
            .collect(Collectors.toCollection(() -> new TreeSet<>(reversedComparator)));

        return findRepository(path, filteredParentDirs, repositories, canonicalizer).orElse(null);
    }

    @Override
    public void repositoriesRemoved(Collection<Repository> removedRepos) {
        dirToRepoCache.entrySet().stream().filter(entry -> entry.getValue().filter(removedRepos::contains).isPresent())
            .map(Map.Entry::getKey).forEach(dirToRepoCache::remove);
    }

    @Override
    public void clear() {
        dirToRepoCache.clear();
    }

    public int size() {
        return dirToRepoCache.size();
    }
}
