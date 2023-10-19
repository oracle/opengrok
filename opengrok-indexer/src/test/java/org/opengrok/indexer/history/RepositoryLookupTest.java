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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.number.OrderingComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.util.PathUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class RepositoryLookupTest {
    // Files to include into Repositories having short paths
    private static final String[] SHORT_PATH_CONTENTS = {"foo/bar.txt", "foo/bla/barf.txt", "special/woops@",
        "special/veryveryverylongfilename_just_for_fun"};
    // Files to include into Repositories having longer paths
    private static final String[] LONG_PATH_CONTENTS = {"foo/bla/1/2/3/4/bar.txt", "foo/bla/1/2/3/4/5/barf.txt"};
    Map<String, Repository> repositories;
    Set<String> repositoryRoots;
    // TODO: Parametrize this to run against windows(), unix() and macos()
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    // Pick first root to resolve paths relative to it.
    // Windows Jimfs can only resolve absolute paths relative to a rootDir, not via fileSystem.get("/a/b/c")
    Path rootDir = fileSystem.getRootDirectories().iterator().next();
    private RepositoryLookup.PathCanonicalizer canonicalizerForUncached;
    private RepositoryLookup.PathCanonicalizer canonicalizerForCached;
    private RepositoryLookupUncached uncached;
    private RepositoryLookupCached cached;

    @BeforeEach
    void before() {
        repositories = new HashMap<>();
        repositoryRoots = new HashSet<>();
        // Spy on two canonicalizers, because we are need to measure number of lookups and compare cached vs uncached
        canonicalizerForUncached =
            spyLambda(RepositoryLookup.PathCanonicalizer.class, PathUtils::getRelativeToCanonical);
        canonicalizerForCached =
            spyLambda(RepositoryLookup.PathCanonicalizer.class, PathUtils::getRelativeToCanonical);
        uncached = RepositoryLookup.uncached();
        cached = RepositoryLookup.cached();
    }

    public static <T> T spyLambda(final Class<T> labmdaType, final T lambda) {
        return mock(labmdaType, delegatesTo(lambda));
    }

    @Test
    void testLookups() throws IOException {
        List<TestRepository> testRepositories = Arrays.asList(
            createRepo("src/5/4/3/2/1", Optional.empty(), LONG_PATH_CONTENTS),
            createRepo("src/1/2/3/4/5", Optional.empty(), SHORT_PATH_CONTENTS),
            // Nested inside previous
            createRepo("src/1/2/3/4/5/z", Optional.empty(), SHORT_PATH_CONTENTS),
            createRepo("var/w", Optional.of("src/1/2/3/4/5/linked"), SHORT_PATH_CONTENTS),
            createRepo("src/a/b/c/d/e1/f/g", Optional.empty(), SHORT_PATH_CONTENTS),
            createRepo("src/a/b/c/d/e2/f/g", Optional.empty(), SHORT_PATH_CONTENTS),
            createRepo("src/a/x/y/w", Optional.empty(), SHORT_PATH_CONTENTS),
            createRepo("src/a/x/y/z", Optional.empty(), SHORT_PATH_CONTENTS));

        assertEquals(
            repositories.keySet().stream().map(fileSystem::getPath).map(Path::getParent).distinct().count(),
            repositoryRoots.size());

        for (TestRepository repo : testRepositories) {
            for (Path p : repo.contents) {
                // Number of cached path canonicalization lookups should be strictly less than uncached ones
                compareLookups(Optional.of(repo), p, OrderingComparison::lessThan);
            }
        }
        for (Path p : Arrays.asList(
            rootDir.resolve("some/unknown/path/1/2/3/4"),
            rootDir.resolve("some/unknown/path/otherdir/1/2/3/4"))) {
            // File must exist otherwise lookup, which actually looks at the filesystem will fail
            createFile(p);
            // Number of cached path canonicalization lookups should be strictly less than uncached ones
            compareLookups(Optional.empty(), p, OrderingComparison::lessThan);
        }
    }

    @Test
    void testLookupsOnEmpty() {
        for (Path p : Arrays.asList(
            rootDir.resolve("some/unknown/path/1/2/3/4"),
            rootDir.resolve("some/unknown/path/otherdir/1/2/3/4"))) {
            // File must exist otherwise lookup, which actually looks at the filesystem will fail
            createFile(p);
            // Number of cached path canonicalization lookups <= uncached ones (both should be zero here)
            compareLookups(Optional.empty(), p, OrderingComparison::lessThanOrEqualTo);
        }
    }

    @Test
    void testSizeInvariantsAndBackfill() throws IOException {
        TestRepository testRepo = createRepo("src/5/4/3/2/1", Optional.empty(), LONG_PATH_CONTENTS);
        String knownFileSubpath = LONG_PATH_CONTENTS[0];
        List<String> subdirs = Arrays.asList(knownFileSubpath.split("/"));
        assertThat(subdirs.size(), Matchers.greaterThan(0));
        // Lookup one known file, this should populate the cache
        Path knownFile = testRepo.path.resolve(knownFileSubpath);
        assertEquals(testRepo.repository,
            cached.getRepository(knownFile, repositoryRoots, repositories, canonicalizerForCached));
        final int initialCacheSize = cached.size();
        assertThat(initialCacheSize, Matchers.greaterThan(0));
        int cachedInvocations = mockingDetails(canonicalizerForCached).getInvocations().size();
        System.out.printf("Inital lookup took %d relativize calls%n", cachedInvocations);
        assertThat(cachedInvocations, Matchers.greaterThan(2));

        // Check that backfill works correctly by looking up files in all parents dirs of knownFile in the repo
        Path knownPathInRepo = testRepo.path;
        for (String subpath: subdirs) {
            knownPathInRepo = knownPathInRepo.resolve(subpath);
            Mockito.reset(canonicalizerForCached);
            assertEquals(testRepo.repository,
                cached.getRepository(knownPathInRepo, repositoryRoots, repositories, canonicalizerForCached));
            assertEquals(initialCacheSize, cached.size(),
                "Cache should not grow when file in a known dir is looked up");
            cachedInvocations = mockingDetails(canonicalizerForCached).getInvocations().size();
            System.out.printf("Cached lookups took %d relativize calls%n", cachedInvocations);
            if (Files.isDirectory(knownPathInRepo)) {
                // Dirs are cached during backfill, so no relativize calls are expected
                assertEquals(0, cachedInvocations, "Dirs should be cached");
            } else {
                // Files require a single relativize call before we try its parent dir which is cached
                // because files are not stored in the cache
                assertEquals(1, cachedInvocations, "Files should not be cached");
            }
        }
    }

        @Test
    void testCachedRemovedAndClear() throws IOException {
        List<TestRepository> testRepositories = Arrays.asList(
            createRepo("src/5/4/3/2/1", Optional.empty(), LONG_PATH_CONTENTS),
            createRepo("src/1/2/3/4/5", Optional.empty(), SHORT_PATH_CONTENTS));

        for (TestRepository repo : testRepositories) {
            for (Path p : repo.contents) {
                assertEquals(repo.repository, cached.getRepository(p, repositoryRoots, repositories,
                    canonicalizerForUncached));
            }
        }
        // Remove one of the repos
        TestRepository removed = testRepositories.get(1);
        repositories.remove(removed.path.toString());
        repositoryRoots.remove(removed.path.getParent().toString());
        assertEquals(testRepositories.size() - 1, repositories.size());
        assertEquals(testRepositories.size() - 1, repositoryRoots.size());
        cached.repositoriesRemoved(Collections.singletonList(removed.repository));
        // Verify that once repository is removed, it can no longer be found
        for (TestRepository repo : testRepositories) {
            for (Path p : repo.contents) {
                Repository lookedUp = cached.getRepository(p, repositoryRoots, repositories, canonicalizerForUncached);
                if (repo == removed) {
                    assertNull(lookedUp);
                } else {
                    assertEquals(repo.repository, lookedUp);
                }
            }
        }
        // clear and make sure we can't find anything
        cached.clear();
        repositories.clear();
        repositoryRoots.clear();
        for (TestRepository repo : testRepositories) {
            for (Path p : repo.contents) {
                assertNull(cached.getRepository(p, repositoryRoots, repositories, canonicalizerForUncached));
            }
        }
    }

    private void compareLookups(
        Optional<TestRepository> expectedRepo, Path lookupPath,
        Function<Integer, Matcher<Integer>> invocationComparatorFactory) {
        System.out.printf("Processing %s, %s%n", expectedRepo, lookupPath);
        // Reset counts before we do lookups
        Mockito.reset(canonicalizerForCached, canonicalizerForUncached);
        Repository expected = expectedRepo.map(TestRepository::getRepository).orElse(null);
        assertEquals(expected,
            uncached.getRepository(lookupPath, repositoryRoots, repositories, canonicalizerForUncached));
        assertEquals(expected, cached.getRepository(lookupPath, repositoryRoots, repositories, canonicalizerForCached));
        int uncachedInvocations = mockingDetails(canonicalizerForUncached).getInvocations().size();
        int cachedInvocations = mockingDetails(canonicalizerForCached).getInvocations().size();
        System.out.printf("Lookup %s (expected %s): uncached calls %d, cached calls %d%n", lookupPath,
            expectedRepo.map(TestRepository::getPath), uncachedInvocations, cachedInvocations);
        Matcher<Integer> cachedInvocationComparator = invocationComparatorFactory.apply(uncachedInvocations);
        assertThat(cachedInvocations, cachedInvocationComparator);
    }

    TestRepository createRepo(String physicalPath, Optional<String> linkPath, String... contents) throws IOException {
        // Create mock repository contents
        Path realRepoPath = rootDir.resolve(physicalPath);
        Files.createDirectories(realRepoPath);
        Arrays.stream(contents).map(realRepoPath::resolve).forEach(this::createFile);

        // Construct symlink to the physical path, if necessary
        Optional<Path> indexingPath = linkPath.map(rootDir::resolve);
        indexingPath.ifPresent(symlink -> {
            try {
                Files.createDirectories(symlink.getParent());
                Files.createSymbolicLink(symlink, realRepoPath);
                System.out.printf("Symlink %s -> %s%n", symlink, realRepoPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        // If given symlink, it should be the repoPath, otherwise, it should be the same as physical path
        Path repoPath = indexingPath.orElse(realRepoPath);

        // Just like HistoryGuru, store repository's parent dir in repositoryRoots
        repositoryRoots.add(repoPath.getParent().toString());
        // Construct a mock Repository (we only need references to them)
        Repository repo = mock(Repository.class);
        // Just like HistoryGuru, store repository in a map keyed by its repoPath
        repositories.put(repoPath.toString(), repo);
        // Now resolve contents relative to the actual repo path
        List<Path> contentPaths = Arrays.stream(contents).map(repoPath::resolve).collect(Collectors.toList());
        contentPaths.forEach(p -> assertTrue(Files.exists(p), p + " must exist"));
        return new TestRepository(repo, repoPath, Collections.unmodifiableList(contentPaths));
    }

    private void createFile(Path p) {
        try {
            Files.createDirectories(p.getParent());
            Files.createFile(p);
            System.out.printf("Created %s%n", p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class TestRepository {
        final Repository repository;
        final Path path;
        final List<Path> contents;

        private TestRepository(Repository repository, Path path, List<Path> contents) {
            this.repository = repository;
            this.path = path;
            this.contents = contents;
        }

        Repository getRepository() {
            return repository;
        }

        Path getPath() {
            return path;
        }

        @Override
        public String toString() {
            return "TestRepository{" +
                "repository=" + repository +
                ", path=" + path +
                ", contents=" + contents +
                '}';
        }
    }
}
