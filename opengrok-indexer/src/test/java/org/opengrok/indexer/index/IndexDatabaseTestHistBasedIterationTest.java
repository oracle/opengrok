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
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.index.ImpactsEnum;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOBooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.FileCollector;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class IndexDatabaseTestHistBasedIterationTest {

    private Path sourceRoot;

    @BeforeEach
    void setup() throws IOException {
        sourceRoot = Files.createTempDirectory("fileSorting");
        RuntimeEnvironment.getInstance().setSourceRoot(sourceRoot.toString());
    }

    @AfterEach
    void cleanup() throws IOException {
        IOUtils.removeRecursive(sourceRoot);
    }

    /**
     * Fake TermsEnum class that supplies certain order of next() and term() values in order to
     * test history based indexing, specifically the uid traversal in
     * {@link IndexDatabase#processFileHistoryBased(IndexDownArgs, File, String)}.
     * Implements just {@link TermsEnum#term()} and {@link TermsEnum#next()}.
     */
    private static class CustomTermsEnum extends TermsEnum {

        final ArrayDeque<BytesRef> dequeue;

        /**
         * The stream elements will be returned from {@link #term()} and {@link #next()}.
         * @param stream stream of uid Strings
         */
        CustomTermsEnum(Stream<Pair<String, String>> stream) {
            this.dequeue = stream.
                    map(pair -> Util.path2uid(Util.fixPathIfWindows(pair.getLeft()), pair.getRight())).
                    map(BytesRef::new).
                    collect(Collectors.toCollection(ArrayDeque::new));
        }

        @Override
        public AttributeSource attributes() {
            return null;
        }

        @Override
        public boolean seekExact(BytesRef bytesRef) throws IOException {
            return false;
        }

        @Override
        public IOBooleanSupplier prepareSeekExact(BytesRef bytesRef) throws IOException {
            return null;
        }

        @Override
        public SeekStatus seekCeil(BytesRef bytesRef) throws IOException {
            return null;
        }

        @Override
        public void seekExact(long l) throws IOException {

        }

        @Override
        public void seekExact(BytesRef bytesRef, TermState termState) throws IOException {

        }

        @Override
        public BytesRef term() throws IOException {
            return dequeue.peekFirst();
        }

        @Override
        public long ord() throws IOException {
            return 0;
        }

        @Override
        public int docFreq() throws IOException {
            return 0;
        }

        @Override
        public long totalTermFreq() throws IOException {
            return 0;
        }

        @Override
        public PostingsEnum postings(PostingsEnum postingsEnum, int i) throws IOException {
            return null;
        }

        @Override
        public ImpactsEnum impacts(int i) throws IOException {
            return null;
        }

        @Override
        public TermState termState() throws IOException {
            return null;
        }

        @Override
        public BytesRef next() throws IOException {
            return dequeue.pollFirst();
        }
    }

    /**
     * This is a higher level test that verifies the path comparator used when traversing the terms when
     * handling history based reindex is in concordance with the path traversal used when performing
     * the initial reindex.
     */
    @Test
    void testUidIterPathComparison() throws Exception {
        final Path pathRelative = Path.of("foo", "bar-module", "niftyhack.c");
        long lastMod = 123L;
        // This naming was chosen because before correct path comparator was used, "/foo/bar-module/niftyhack.c"
        // was sorted before "/foo/bar/niftyhack.c" since the comparison was done of the whole path
        // and the '-' character has lower order than the '/' path separator.
        TermsEnum uidIter = new CustomTermsEnum(Stream.of(
                Pair.of(File.separator + Path.of( "foo", "bar", "niftyhack.c"),
                        DateTools.timeToString(lastMod, DateTools.Resolution.MILLISECOND)),
                Pair.of(File.separator + pathRelative,
                        DateTools.timeToString(567L, DateTools.Resolution.MILLISECOND))
        ));
        IndexWriter indexWriter = Mockito.mock(IndexWriter.class);
        // Technically there should be a non-null project associated with history based reindex,
        // however it is not needed for this test.
        IndexDatabase indexDatabase = new IndexDatabase(null, uidIter, indexWriter);
        IndexDownArgs args = new IndexDownArgs();

        Path path = Path.of(sourceRoot.toString(), pathRelative.toString());
        File file = path.toFile();
        file.getParentFile().mkdirs();
        Files.createFile(path);

        indexDatabase.processFileHistoryBased(args, file, File.separator + pathRelative.toString());
        // checkSettings() will return false for the second file, however accept() will return false,
        // therefore it should not be added to the args.
        assertEquals(1, args.works.size());
        assertEquals(file, args.works.get(0).file);
        assertNull(uidIter.next());
    }

    /**
     * If a file is to be processed for history based reindex it must have the same uid as a pre-existing live document,
     * otherwise this would lead to corrupted index - there would be a deleted document and live document sharing
     * the same uid. This would break down the index traversal.
     */
    @Test
    void testDuplicateUidCheck() throws Exception {
        final Path relativePath = Path.of("foo", "bar.txt");
        final String relativePathWithLeadingSlash = File.separator + relativePath;
        final Path path = Path.of(sourceRoot.toString(), relativePath.toString());
        File file = path.toFile();
        file.getParentFile().mkdirs();
        Files.createFile(path);
        assertTrue(file.exists());

        TermsEnum uidIter = new CustomTermsEnum(Stream.of(
                Pair.of(relativePathWithLeadingSlash,
                        DateTools.timeToString(file.lastModified(), DateTools.Resolution.MILLISECOND))
        ));
        IndexWriter indexWriter = Mockito.mock(IndexWriter.class);
        IndexDatabase indexDatabase = new IndexDatabase(null, uidIter, indexWriter);
        IndexDownArgs args = new IndexDownArgs();
        assertThrows(IndexDatabase.IndexerFault.class,
                () -> indexDatabase.processFileHistoryBased(args, file, relativePathWithLeadingSlash));
    }

    /**
     * Verify that the files acquired from a FileCollector are passed to the
     * {@link IndexDatabase#processFileHistoryBased(IndexDownArgs, File, String)} in the correct order.
     */
    @Test
    void testInputFileSorting() throws Exception {
        final String projectName = "foo";
        Project project = Mockito.mock(Project.class);
        when(project.getName()).thenReturn(projectName);
        when(project.getPath()).thenReturn(projectName); // Project path is not relevant.

        FileCollector fileCollector = Mockito.mock(FileCollector.class);
        Set<String> files = new HashSet<>();
        List<Path> filePaths = List.of(
                Path.of("zzz", "duh.c"),
                Path.of("foo", "bar.txt"),
                Path.of( "foo-module", "bar.txt"));
        for (Path filePathRelative : filePaths) {
            Path filePath = Path.of(sourceRoot.toString(), filePathRelative.toString());
            File parent = filePath.toFile().getParentFile();
            if (!parent.isDirectory()) {
                parent.mkdirs();
            }
            Files.createFile(filePath);
            files.add(filePathRelative.toString());
        }
        when(fileCollector.getFiles()).thenReturn(files);
        RuntimeEnvironment.getInstance().setFileCollector(projectName, fileCollector);

        IndexWriter indexWriter = Mockito.mock(IndexWriter.class);
        // By using null for uidIter all the files returned from the FileCollector for the project
        // should be considered new and therefore should be added to the args.
        IndexDatabase indexDatabaseOrig = new IndexDatabase(project, null, indexWriter);
        IndexDatabase indexDatabase = Mockito.spy(indexDatabaseOrig);
        IndexDownArgs args = new IndexDownArgs();

        indexDatabase.indexDownUsingHistory(sourceRoot.toFile(), args);
        List<Path> expectedPaths = fileCollector.getFiles().stream().
                map(Path::of).
                sorted(IndexDatabase.FILEPATH_COMPARATOR).
                collect(Collectors.toList());
        // Verify the iteration through the set is different.
        // This is needed to verify that the list was indeed sorted.
        List<String> filesFromSet = new ArrayList<>(files);
        assertNotEquals(filesFromSet, expectedPaths.stream().map(Path::toString).collect(Collectors.toList()));

        // Verify that the files were processed in certain order.
        InOrder inOrder = Mockito.inOrder(indexDatabase);
        for (Path path : expectedPaths) {
            inOrder.verify(indexDatabase).processFileHistoryBased(eq(args), any(File.class), eq(path.toString()));
        }
        assertEquals(expectedPaths.size(), args.works.size());
    }
}
