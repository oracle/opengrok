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
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.OpenGrokThreadFactory;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.Statistics;
import org.opengrok.indexer.web.Util;

/**
 * Index checker. Offers multiple methods of checking the index. The main method is {@link #check(IndexCheckMode)}.
 *
 * @author Vladimír Kotal
 */
public class IndexCheck implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCheck.class);

    /**
     * Index check modes. Ordered from least to most extensive.
     */
    public enum IndexCheckMode {
        NO_CHECK,
        VERSION,
        DEFINITIONS,
        DOCUMENTS
    }

    /**
     * Common exception for all check modes.
     */
    public static class IndexCheckException extends Exception {
        private static final long serialVersionUID = 5693446916108385595L;

        private final Set<Path> failedPaths = new HashSet<>();

        public IndexCheckException(String s, Path path) {
            super(s);
            failedPaths.add(path);
        }

        public IndexCheckException(String s, Set<Path> paths) {
            super(s);
            failedPaths.addAll(paths);
        }

        public Set<Path> getFailedPaths() {
            return Collections.unmodifiableSet(failedPaths);
        }
    }

    /**
     * Exception thrown when index version does not match Lucene version.
     */
    public static class IndexVersionException extends IndexCheckException {

        private static final long serialVersionUID = 5693446916108385595L;

        private final int luceneIndexVersion;
        private final int indexVersion;

        public IndexVersionException(String s, Path path, int luceneIndexVersion, int indexVersion) {
            super(s, path);
            this.indexVersion = indexVersion;
            this.luceneIndexVersion = luceneIndexVersion;
        }

        @Override
        public String toString() {
            return getMessage() + ": " + String.format("Lucene version = %d", luceneIndexVersion) + ", " +
                    String.format("index version = %d", indexVersion);
        }
    }

    /**
     * Exception thrown when index contains duplicate live documents.
     */
    public static class IndexDocumentException extends IndexCheckException {
        private static final long serialVersionUID = 5693446916108385595L;

        private final Map<String, Integer> fileMap;

        public IndexDocumentException(String s, Path path) {
            super(s, path);
            this.fileMap = null;
        }

        public IndexDocumentException(String s, Path path, Map<String, Integer> fileMap) {
            super(s, path);
            this.fileMap = fileMap;
        }

        @Override
        public String toString() {
            return getMessage() + ": " + (fileMap == null ? "" : fileMap);
        }
    }

    private final Configuration configuration;
    private final Set<String> projectNames = new HashSet<>();

    // Common executor for parallel processing.
    private final ExecutorService executor;

    /**
     * @param configuration configuration based on which to perform the check
     */
    public IndexCheck(@NotNull Configuration configuration) {
        this(configuration, null);
    }

    /**
     * @param configuration configuration based on which to perform the check
     * @param projectNames collection of project names. If non-empty, only projects matching these paths will be checked.
     *                     Otherwise, either the sole index or all project indexes will be checked, depending
     *                     on whether projects are enabled in the configuration.
     */
    public IndexCheck(@NotNull Configuration configuration, Collection<String> projectNames) {
        this.configuration = configuration;
        if (projectNames != null) {
            this.projectNames.addAll(projectNames);
        }

        executor = Executors.newFixedThreadPool(RuntimeEnvironment.getInstance().getRepositoryInvalidationParallelism(),
                new OpenGrokThreadFactory("index-check"));
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(configuration.getIndexCheckTimeout(), TimeUnit.SECONDS)) {
                LOGGER.log(Level.WARNING, "index check took more than {0} seconds",
                        configuration.getIndexCheckTimeout());
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "failed to await termination of index check");
            executor.shutdownNow();
        }
    }

    /**
     * Perform index check of given projects in parallel.
     * @param mode index check mode
     * @param projectNames set of project names
     * @throws IOException on I/O error
     */
    private void checkProjectsParallel(IndexCheckMode mode, Set<String> projectNames)
            throws IOException, IndexCheckException {

        Path indexRoot = Path.of(configuration.getDataRoot(), IndexDatabase.INDEX_DIR);

        Set<Future<Exception>> futures = new HashSet<>();
        for (String projectName : projectNames) {
            futures.add(executor.submit(() -> {
                try {
                    checkDirWithLogging(Path.of(configuration.getSourceRoot(), projectName),
                            Path.of(indexRoot.toString(), projectName),
                            mode);
                } catch (Exception e) {
                    return e;
                }
                return null;
            }));
        }

        IOException ioException = null;
        Set<Path> paths = new HashSet<>();
        /*
         * In case od IndexCheckExceptions, assemble all the paths so they can be returned in a single exception.
         * For IOExceptions, log them all and throw a common one at the end.
         */
        for (Future<Exception> future : futures) {
            Exception exception = null;
            try {
                exception = future.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, "failed to get future", e);
            }
            if (exception != null) {
                if (exception instanceof IndexCheckException) {
                    // The exception is logged because even though the path will be added to the common exception
                    // at the end, the nature of the problem (i.e. specific kind of the index check and associated details)
                    // would be lost.
                    LOGGER.log(Level.WARNING, "index check failed", exception);
                    paths.addAll(((IndexCheckException) exception).getFailedPaths());
                } else if (exception instanceof IOException) {
                    // There can be multiple IOExceptions so log them here and remember the last one.
                    // It will be thrown once all the checks complete.
                    LOGGER.log(Level.WARNING, "could not perform index check", exception);
                    ioException = (IOException) exception;
                }
            }
        }

        // IOException trumps the IndexCheckException, so throw the last one here first.
        if (ioException != null) {
            throw ioException;
        }

        if (!paths.isEmpty()) {
            throw new IndexCheckException("index check failed", paths);
        }
    }

    /**
     * Check index(es). If the check is successful, just return. On failure, an exception will be thrown.
     * @param mode index check mode
     * @throws IOException on I/O error
     * @throws IndexCheckException if some of the indexes failed the check. The exception contains list of the paths.
     */
    public void check(IndexCheckMode mode) throws IOException, IndexCheckException {

        if (mode.equals(IndexCheckMode.NO_CHECK)) {
            LOGGER.log(Level.WARNING, "no index check mode selected");
            return;
        }

        Statistics statistics = new Statistics();

        if (!projectNames.isEmpty()) {
            // Assumes projects are enabled.
            checkProjectsParallel(mode, projectNames);
        } else {
            if (configuration.isProjectsEnabled()) {
                checkProjectsParallel(mode, configuration.getProjects().keySet());
            } else {
                checkDirWithLogging(Path.of(configuration.getSourceRoot()),
                        Path.of(configuration.getDataRoot(), IndexDatabase.INDEX_DIR), mode);
            }
        }

        statistics.report(LOGGER, Level.FINE, "Index check done");
    }

    /**
     * Perform specified check on given index directory. All exceptions except {@code IOException} are swallowed
     * and result in return value of 1.
     * @param indexPath directory with index
     * @throws IOException on I/O error
     * @throws IndexCheckException if the index failed given check
     */
    private void checkDirWithLogging(Path sourcePath, Path indexPath, IndexCheckMode mode)
            throws IOException, IndexCheckException {
        try {
            LOGGER.log(Level.INFO, "Checking index in ''{0}'' (mode {1})", new Object[]{indexPath, mode});
            checkDir(sourcePath, indexPath, mode);
        } catch (IndexCheckException e) {
            LOGGER.log(Level.WARNING, String.format("Index check for directory '%s' failed", indexPath), e);
            throw e;
        }

        LOGGER.log(Level.INFO, "Index check for directory ''{0}'' passed", indexPath);
    }

    /**
     * Check index in given directory. If the directory is empty, just return.
     * <p>
     * It assumes that that all commits (if any)
     * in the Lucene segment file were done with the same version.
     * </p>
     * @param sourcePath path to source directory
     * @param indexPath directory with index to check
     * @param mode      index check mode
     * @throws IOException           if the directory cannot be opened
     * @throws IndexVersionException if the version of the index does not match Lucene index version
     * @throws IndexDocumentException if there are duplicate documents in the index or not matching definitions
     */
    void checkDir(Path sourcePath, Path indexPath, IndexCheckMode mode)
            throws IndexVersionException, IndexDocumentException, IOException {

        switch (mode) {
            case VERSION:
                checkVersion(sourcePath, indexPath);
                break;
            case DOCUMENTS:
                checkDuplicateDocuments(sourcePath, indexPath);
                break;
            case DEFINITIONS:
                checkDefinitions(sourcePath, indexPath);
        }
    }

    private static List<String> getLines(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
        }

        return lines;
    }

    /**
     * Crosscheck definitions found in the index for given file w.r.t. actual file contents.
     * There is a number of cases this check can fail even for legitimate cases. This is why
     * certain patterns and file types are skipped.
     * @param path path to the file being checked
     * @return okay indication
     */
    private boolean checkDefinitionsForFile(Path path) throws ParseException, IOException, ClassNotFoundException {

        // Avoid paths with certain suffixes. These exhibit some behavior that cannot be handled
        // For example, '1;' in Perl code is interpreted by Universal Ctags as 'STDOUT'.
        Set<String> suffixesToAvoid = Set.of(".sh", ".SH", ".pod", ".pl", ".pm", ".js", ".json", ".css");
        if (suffixesToAvoid.stream().anyMatch(s -> path.toString().endsWith(s))) {
            return true;
        }

        boolean okay = true;
        Definitions defs = IndexDatabase.getDefinitions(path.toFile());
        if (defs != null) {
            LOGGER.log(Level.FINE, "checking definitions for ''{0}''", path);
            List<String> lines = getLines(path);

            for (Definitions.Tag tag : defs.getTags()) {
                // These symbols are sometimes produced by Universal Ctags even though they are not
                // actually present in the file.
                if (tag.symbol.startsWith("__anon")) {
                    continue;
                }

                // Needed for some TeX definitions.
                String symbol = tag.symbol;
                if (symbol.contains("\\")) {
                    symbol = symbol.replace("\\", "");
                }

                // C++ operator overload symbol contains extra space, ignore them for now.
                if (symbol.startsWith("operator ")) {
                    continue;
                }

                // These could be e.g. C structure members, having their line number equal to
                // where the structure definition starts, ignore.
                if (tag.type.equals("argument")) {
                    continue;
                }

                if (!lines.get(tag.line - 1).contains(symbol)) {
                    // Line wrap, skip it.
                    if (lines.get(tag.line - 1).endsWith("\\")) {
                        continue;
                    }

                    // Line wraps cause the symbol to be reported on different line than it resides on.
                    // Perform more thorough/expensive check.
                    final String str = symbol;
                    if (lines.stream().noneMatch(l -> l.contains(str))) {
                        LOGGER.log(Level.WARNING, String.format("'%s' does not contain '%s' (should be on line %d)",
                                path, symbol, tag.line));
                        okay = false;
                    }
                }
            }
        }

        return okay;
    }

    private static class GetFiles extends SimpleFileVisitor<Path> {
        Set<Path> files = new HashSet<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (RuntimeEnvironment.getInstance().getIgnoredNames().ignore(dir.toFile())) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file.toFile().isFile()) {
                files.add(file);
            }

            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Check definitions stored in documents against definitions found by ctags in the respective input files.
     * This is done only for a subset of files, see {@link #checkDefinitionsForFile(Path)}.
     * This check parallelized.
     * @param sourcePath path to source root subtree
     * @param indexPath path to index to check
     * @throws IOException on I/O error
     * @throws IndexDocumentException if there are any documents with definitions not matching definitions found by ctags
     */
    private void checkDefinitions(Path sourcePath, Path indexPath) throws IOException, IndexDocumentException {

        Statistics statistics = new Statistics();
        GetFiles getFiles = new GetFiles();
        Files.walkFileTree(sourcePath, getFiles);
        Set<Path> paths = getFiles.files;
        LOGGER.log(Level.FINE, "Checking definitions in ''{0}'' ({1} paths)",
                new Object[]{indexPath, paths.size()});

        long errors = 0;
        ExecutorService executorService = RuntimeEnvironment.getInstance().getIndexerParallelizer().getFixedExecutor();
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Path path : paths) {
            futures.add(executorService.submit(() -> checkDefinitionsForFile(path)));
        }

        IOException ioException = null;
        for (Future<Boolean> future : futures) {
            try {
                if (!future.get()) {
                    errors++;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "failure when checking definitions", e);
                final Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    ioException = (IOException) cause;
                }
            }
        }
        statistics.report(LOGGER, Level.FINE, String.format("checked %d files", paths.size()));

        // If there were multiple cases of IOException, they were logged above.
        // Propagate the last one so that upper layers can properly decide on how to treat the index check.
        if (ioException != null) {
            throw ioException;
        }

        if (errors > 0) {
            throw new IndexDocumentException(String.format("document check failed for (%d documents out of %d)",
                    errors, paths.size()), indexPath);
        }
    }

    /**
     * @param sourcePath path to the source
     * @param indexPath path to the index directory
     * @throws IOException on I/O error
     * @throws IndexVersionException if the version stored in the document does not match the version
     * used by the running program
     */
    private void checkVersion(Path sourcePath, Path indexPath) throws IOException, IndexVersionException {
        LockFactory lockFactory = NativeFSLockFactory.INSTANCE;
        int segVersion;

        try (Directory indexDirectory = FSDirectory.open(indexPath, lockFactory)) {
            try {
                SegmentInfos segInfos = SegmentInfos.readLatestCommit(indexDirectory);
                segVersion = segInfos.getIndexCreatedVersionMajor();
            } catch (IndexNotFoundException e) {
                LOGGER.log(Level.WARNING, "no index found in ''{0}''", indexDirectory);
                return;
            }
        }

        LOGGER.log(Level.FINE, "Checking index version in ''{0}'': index={1} program={2}",
                new Object[]{indexPath, segVersion, Version.LATEST.major});
        if (segVersion != Version.LATEST.major) {
            throw new IndexVersionException(
                String.format("Index for '%s' has index version discrepancy", sourcePath), sourcePath,
                    Version.LATEST.major, segVersion);
        }
    }

    @VisibleForTesting
    static IndexReader getIndexReader(Path indexPath) throws IOException {
        try (FSDirectory indexDirectory = FSDirectory.open(indexPath, NoLockFactory.INSTANCE)) {
            return DirectoryReader.open(indexDirectory);
        }
    }

    /**
     * @param indexPath path to the index
     * @return set of deleted uids in the index related to the project name
     * @throws IOException if the index cannot be read
     */
    @VisibleForTesting
    static Set<String> getDeletedUids(Path indexPath) throws IOException {
        Set<String> deletedUids = new HashSet<>();

        try (IndexReader indexReader = getIndexReader(indexPath)) {
            Bits liveDocs = MultiBits.getLiveDocs(indexReader);
            if (liveDocs == null) { // the index has no deletions
                return deletedUids;
            }

            for (int i = 0; i < indexReader.maxDoc(); i++) {
                Document doc = indexReader.storedFields().document(i);
                // This should avoid the special LOC documents.
                IndexableField field = doc.getField(QueryBuilder.U);
                if (field != null) {
                    String uid = field.stringValue();

                    if (!liveDocs.get(i)) {
                        deletedUids.add(uid);
                    }
                }
            }
        }

        return deletedUids;
    }

    /**
     * @param indexPath path to index
     * @return list of live document paths (some of them can be duplicate if the index is corrupted)
     * or {@code null} if live documents cannot be retrieved.
     * @throws IOException on I/O error
     */
    @Nullable
    @VisibleForTesting
    static List<String> getLiveDocumentPaths(Path indexPath) throws IOException {
        try (IndexReader indexReader = getIndexReader(indexPath)) {
            List<String> livePaths = new ArrayList<>();

            Bits liveDocs = MultiBits.getLiveDocs(indexReader);
            if (liveDocs == null) { // the index has no deletions
                return null;
            }

            for (int i = 0; i < indexReader.maxDoc(); i++) {
                Document doc = indexReader.storedFields().document(i);

                if (!liveDocs.get(i)) {
                    continue;
                }

                // This should avoid the special LOC documents.
                IndexableField field = doc.getField(QueryBuilder.U);
                if (field != null) {
                    String uid = field.stringValue();
                    livePaths.add(Util.uid2url(uid));
                }
            }

            return livePaths;
        }
    }

    private static void checkDuplicateDocuments(Path sourcePath, Path indexPath) throws IOException, IndexDocumentException {

        LOGGER.log(Level.FINE, "Checking duplicate documents in ''{0}''", indexPath);
        Statistics stat = new Statistics();
        List<String> livePaths = getLiveDocumentPaths(indexPath);
        if (livePaths == null) {
            throw new IndexDocumentException(String.format("cannot determine live paths for '%s'", indexPath),
                    indexPath);
        }
        HashSet<String> pathSet = new HashSet<>(livePaths);
        Map<String, Integer> fileMap = new ConcurrentHashMap<>();
        if (pathSet.size() != livePaths.size()) {
            LOGGER.log(Level.FINE,
                    "index in ''{0}'' has document path set ({1}) vs document list ({2}) discrepancy",
                    new Object[]{indexPath, pathSet.size(), livePaths.size()});
            for (String path : livePaths) {
                if (pathSet.contains(path)) {
                    fileMap.putIfAbsent(path, 0);
                    fileMap.put(path, fileMap.get(path) + 1);
                }
            }
        }

        // Traverse the file map and leave only duplicate entries.
        for (String path: fileMap.keySet()) {
            if (fileMap.get(path) > 1) {
                LOGGER.log(Level.FINER, "duplicate path: ''{0}''", path);
            } else {
                fileMap.remove(path);
            }
        }

        stat.report(LOGGER, Level.FINE, String.format("duplicate check in '%s' done", indexPath));
        if (!fileMap.isEmpty()) {
            throw new IndexDocumentException(String.format("index for '%s' contains duplicate live documents",
                    sourcePath), sourcePath, fileMap);
        }
    }
}
