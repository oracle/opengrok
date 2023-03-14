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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.Statistics;
import org.opengrok.indexer.web.Util;

/**
 * Index checker.
 *
 * @author Vladimír Kotal
 */
public class IndexCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCheck.class);

    /**
     * Index check modes. Ordered from least to most extensive.
     */
    public enum IndexCheckMode {
        NO_CHECK,
        VERSION,
        DOCUMENTS
    }

    /**
     * Exception thrown when index version does not match Lucene version.
     */
    public static class IndexVersionException extends Exception {

        private static final long serialVersionUID = 5693446916108385595L;

        private final int luceneIndexVersion;
        private final int indexVersion;

        public IndexVersionException(String s, int luceneIndexVersion, int indexVersion) {
            super(s);

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
    public static class IndexDocumentException extends Exception {
        private static final long serialVersionUID = 5693446916108385595L;

        private final Map<String, Integer> fileMap;

        public IndexDocumentException(String s) {
            super(s);

            this.fileMap = null;
        }

        public IndexDocumentException(String s, Map<String, Integer> fileMap) {
            super(s);

            this.fileMap = fileMap;
        }

        @Override
        public String toString() {
            return getMessage() + ": " + (fileMap == null ? "" : fileMap);
        }
    }

    private IndexCheck() {
        // utility class
    }

    /**
     * Check index(es).
     * @param configuration configuration based on which to perform the check
     * @param mode index check mode
     * @param projectNames collection of project names. If non-empty, only projects matching these paths will be checked.
     *                     Otherwise, either the sole index or all project indexes will be checked, depending
     *                     on whether projects are enabled in the configuration.
     * @return true on success, false on failure
     */
    public static boolean check(@NotNull Configuration configuration, IndexCheckMode mode,
                                Collection<String> projectNames) {

        if (mode.equals(IndexCheckMode.NO_CHECK)) {
            LOGGER.log(Level.WARNING, "no index check mode selected");
            return true;
        }

        Path indexRoot = Path.of(configuration.getDataRoot(), IndexDatabase.INDEX_DIR);
        int ret = 0;

        if (!projectNames.isEmpty()) {
            // Assumes projects are enabled.
            for (String projectName : projectNames) {
                ret |= checkDirNoExceptions(Path.of(indexRoot.toString(), projectName), mode, projectName);
            }
        } else {
            if (configuration.isProjectsEnabled()) {
                for (String projectName : configuration.getProjects().keySet()) {
                    ret |= checkDirNoExceptions(Path.of(indexRoot.toString(), projectName), mode, projectName);
                }
            } else {
                ret |= checkDirNoExceptions(indexRoot, mode, "");
            }
        }

        return ret == 0;
    }

    /**
     * @param indexPath directory with index
     * @return 0 on success, 1 on failure
     */
    private static int checkDirNoExceptions(Path indexPath, IndexCheckMode mode, String projectName) {
        try {
            LOGGER.log(Level.INFO, "Checking index in ''{0}''", indexPath);
            checkDir(indexPath, mode, projectName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format("Index check for directory '%s' failed", indexPath), e);
            return 1;
        }

        LOGGER.log(Level.INFO, "Index check for directory ''{0}'' passed", indexPath);
        return 0;
    }

    /**
     * Check index in given directory. It assumes that that all commits (if any)
     * in the Lucene segment file were done with the same version.
     *
     * @param indexPath directory with index to check
     * @param mode index check mode
     * @param projectName name of the project, can be empty
     * @throws IOException if the directory cannot be opened
     * @throws IndexVersionException if the version of the index does not match Lucene index version
     */
    public static void checkDir(Path indexPath, IndexCheckMode mode, String projectName)
            throws IndexVersionException, IndexDocumentException, IOException {

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

        LOGGER.log(Level.FINE, "Checking index version in ''{0}''", indexPath);
        if (segVersion != Version.LATEST.major) {
            throw new IndexVersionException(
                String.format("Directory '%s' has index version discrepancy", indexPath),
                    Version.LATEST.major, segVersion);
        }

        if (mode.ordinal() >= IndexCheckMode.DOCUMENTS.ordinal()) {
            checkDuplicateDocuments(indexPath, projectName);
        }
    }

    public static IndexReader getIndexReader(Path indexPath) throws IOException {
        try (FSDirectory indexDirectory = FSDirectory.open(indexPath, NoLockFactory.INSTANCE)) {
            return DirectoryReader.open(indexDirectory);
        }
    }

    /**
     * @param indexPath path to the index
     * @return set of deleted uids in the index related to the project name
     * @throws IOException if the index cannot be read
     */
    public static Set<String> getDeletedUids(Path indexPath) throws IOException {
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
     * @param projectName project name, can be empty
     * @return list of live document paths (some of them can be duplicate if the index is corrupted)
     * or {@code null} if live documents cannot be retrieved.
     * @throws IOException on I/O error
     */
    @Nullable
    public static List<String> getLiveDocumentPaths(Path indexPath, String projectName) throws IOException {
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

    private static void checkDuplicateDocuments(Path indexPath, String projectName)
            throws IOException, IndexDocumentException {

        LOGGER.log(Level.FINE, "Checking duplicate documents in ''{0}''", indexPath);
        Statistics stat = new Statistics();
        List<String> livePaths = getLiveDocumentPaths(indexPath, projectName);
        if (livePaths == null) {
            throw new IndexDocumentException(String.format("cannot determine live paths for '%s'", indexPath));
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
            throw new IndexDocumentException(String.format("index in '%s' contains duplicate live documents",
                    indexPath), fileMap);
        }
    }
}
