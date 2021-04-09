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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.opengrok.indexer.analysis.NullableNumLinesLOC;
import org.opengrok.indexer.index.NumLinesLOCUtil;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Statistics;

/**
 * Represents a searcher to supplement metadata from the file-system with
 * per-file, OpenGrok-analyzed data.
 */
public class DirectoryExtraReader {

    // N.b.: update #search() comment when changing
    private static final int DIR_LIMIT_NUM = 2000;

    private static final Logger LOGGER = LoggerFactory.getLogger(
        DirectoryExtraReader.class);

    /**
     * Search for supplemental file information in the specified {@code path}.
     * @param searcher a defined instance
     * @param path a defined path to qualify the search
     * @return a list of results, limited to 2000 values
     * @throws IOException if an error occurs searching the index
     */
    public List<NullableNumLinesLOC> search(IndexSearcher searcher, String path)
            throws IOException {
        if (searcher == null) {
            throw new IllegalArgumentException("`searcher' is null");
        }
        if (path == null) {
            throw new IllegalArgumentException("`path' is null");
        }

        QueryBuilder qbuild = new QueryBuilder();
        qbuild.setDirPath(path);
        Query query;
        try {
            query = qbuild.build();
        } catch (ParseException e) {
            final String PARSE_ERROR =
                "An error occurred while parsing dirpath query";
            LOGGER.log(Level.WARNING, PARSE_ERROR, e);
            throw new IOException(PARSE_ERROR);
        }

        Statistics stat = new Statistics();
        TopDocs hits = searcher.search(query, DIR_LIMIT_NUM);

        stat.report(LOGGER, Level.FINEST, "search via DirectoryExtraReader done",
                "search.latency", new String[]{"category", "extra",
                        "outcome", hits.scoreDocs.length > 0 ? "success" : "empty"});

        List<NullableNumLinesLOC> results = processHits(searcher, hits);

        return results;
    }

    private List<NullableNumLinesLOC> processHits(IndexSearcher searcher, TopDocs hits)
            throws IOException {

        List<NullableNumLinesLOC> results = new ArrayList<>();

        for (ScoreDoc sd : hits.scoreDocs) {
            Document d = searcher.doc(sd.doc);
            NullableNumLinesLOC extra = NumLinesLOCUtil.read(d);
            results.add(extra);
        }

        return results;
    }
}
