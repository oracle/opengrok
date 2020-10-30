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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.suggest.query.customized;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;
import org.opengrok.suggest.query.PhraseScorer;
import org.opengrok.suggest.query.data.BitIntsHolder;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class CustomSloppyPhraseScorerTest {

    public static void test(
            final int slop,
            final int offset,
            final String[] terms,
            final Integer[] expectedPositions
    ) throws IOException {
        Directory dir = new ByteBuffersDirectory();

        try (IndexWriter iw = new IndexWriter(dir, new IndexWriterConfig())) {
            Document doc = new Document();
            doc.add(new TextField("test", "zero one two three four five six seven eight nine ten", Field.Store.NO));

            iw.addDocument(doc);
        }

        CustomPhraseQuery query = new CustomPhraseQuery(slop, "test", terms);
        query.setOffset(offset);

        try (IndexReader ir = DirectoryReader.open(dir)) {
            IndexSearcher is = new IndexSearcher(ir);

            Weight w = query.createWeight(is, ScoreMode.COMPLETE_NO_SCORES, 1);

            LeafReaderContext context = ir.getContext().leaves().get(0);

            Scorer scorer = w.scorer(context);

            TwoPhaseIterator it = scorer.twoPhaseIterator();

            int correctDoc = -1;

            int docId;
            while ((docId = it.approximation().nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                if (it.matches()) {
                    correctDoc = docId;
                }
            }

            BitIntsHolder bs = (BitIntsHolder) ((PhraseScorer) scorer).getPositions(correctDoc);

            assertThat(toSet(bs), contains(expectedPositions));
        }
    }

    private static Set<Integer> toSet(final BitIntsHolder bs) {
        Set<Integer> intSet = new HashSet<>();
        for (int i = 0; i < bs.length(); i++) {
            if (bs.has(i)) {
                intSet.add(i);
            }
        }
        return intSet;
    }

    @Test
    public void simpleTestAfter() throws IOException {
        test(2, 2, new String[] {"five", "six"}, new Integer[] {7, 8, 9});
    }

    @Test
    public void simpleTestBefore() throws IOException {
        test(2, -1, new String[] {"five", "six"}, new Integer[] {2, 3, 4});
    }

}
