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
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opengrok.indexer.analysis.CompatibleAnalyser;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.web.SortOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests sorting of search results by path.
 */
class SearchEnginePathSortTest {

    @TempDir
    Path tempDir;

    private RuntimeEnvironment env;
    private String originalSourceRoot;
    private String originalDataRoot;

    @BeforeEach
    void setUp() throws IOException {
        env = RuntimeEnvironment.getInstance();
        originalSourceRoot = env.getSourceRootPath();
        originalDataRoot = env.getDataRootPath();

        Path sourceRoot = tempDir.resolve("source");
        Path dataRoot = tempDir.resolve("data");
        Files.createDirectories(sourceRoot);
        env.setSourceRoot(sourceRoot.toString());
        env.setDataRoot(dataRoot.toString());
        env.releaseIndexSearchers();

        Path indexDir = dataRoot.resolve(IndexDatabase.INDEX_DIR);
        Files.createDirectories(indexDir);
        createIndex(indexDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        env.releaseIndexSearchers();
        if (originalSourceRoot != null) {
            env.setSourceRoot(originalSourceRoot);
        }
        if (originalDataRoot != null) {
            env.setDataRoot(originalDataRoot);
        }
    }

    /**
     * The index deliberately stores absolute paths whose lexicographic order is the reverse of the
     * source-root relative paths. Sorting by path has to use the paths displayed in the UI, i.e. the
     * relative {@link QueryBuilder#PATH} field, rather than the absolute {@link QueryBuilder#FULLPATH}.
     */
    private static void createIndex(Path indexDir) throws IOException {
        try (Directory directory = FSDirectory.open(indexDir);
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new CompatibleAnalyser()))) {
            addDocument(writer, "/infra.base/src/appl/Makefile", "/zzz/infra.base/src/appl/Makefile");
            addDocument(writer, "/infra.base/src/perl/Makefile", "/yyy/infra.base/src/perl/Makefile");
            addDocument(writer, "/infra.base/src/python/Makefile", "/xxx/infra.base/src/python/Makefile");
        }
    }

    private static void addDocument(IndexWriter writer, String path, String fullPath) throws IOException {
        Document doc = new Document();
        doc.add(new TextField(QueryBuilder.FULL, "needle", Store.NO));
        doc.add(new TextField(QueryBuilder.PATH, path, Store.YES));
        doc.add(new SortedDocValuesField(QueryBuilder.PATH, new BytesRef(path)));
        doc.add(new SortedDocValuesField(QueryBuilder.FULLPATH, new BytesRef(fullPath)));
        writer.addDocument(doc);
    }

    @Test
    void testSortByPathUsesRelativePath() throws IOException {
        SearchEngine engine = new SearchEngine(100);
        try {
            engine.setFreetext("needle");
            engine.setSortOrder(SortOrder.BY_PATH);
            assertEquals(3, engine.search(Collections.emptyList()));

            String[] paths = Arrays.stream(engine.scoreDocs()).
                    map(hit -> {
                        try {
                            return engine.doc(hit.doc).get(QueryBuilder.PATH);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).
                    toArray(String[]::new);

            assertArrayEquals(new String[]{
                    "/infra.base/src/appl/Makefile",
                    "/infra.base/src/perl/Makefile",
                    "/infra.base/src/python/Makefile"
            }, paths);
        } finally {
            engine.destroy();
        }
    }
}
