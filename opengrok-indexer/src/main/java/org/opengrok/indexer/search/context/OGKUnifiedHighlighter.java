/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.search.context;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.apache.lucene.search.uhighlight.UHComponents;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.util.BytesRef;
import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.ExpandTabsReader;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.Util;

/**
 * Represents a subclass of {@link UnifiedHighlighter} with customizations for
 * OpenGrok.
 */
public class OGKUnifiedHighlighter extends UnifiedHighlighter {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        OGKUnifiedHighlighter.class);

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private int tabSize;

    private String fileTypeName;

    private ContextArgs contextArgs;

    /**
     * Initializes an instance with
     * {@link UnifiedHighlighter#UnifiedHighlighter(org.apache.lucene.search.IndexSearcher, org.apache.lucene.analysis.Analyzer)}
     * for the specified {@code indexSearcher} and {@code indexAnalyzer}, and
     * stores the {@code env} for later use.
     * @param indexSearcher a required instance
     * @param indexAnalyzer a required instance
     * @throws IllegalArgumentException if any argument is null
     */
    public OGKUnifiedHighlighter(IndexSearcher indexSearcher, Analyzer indexAnalyzer) {
        super(indexSearcher, indexAnalyzer);
    }

    /**
     * Gets a file type name-specific analyzer during the execution of
     * {@link #highlightFieldsUnion(java.lang.String[], org.apache.lucene.search.Query, int)},
     * or just gets the object passed in to the constructor at all other times.
     * @return a defined instance
     */
    @Override
    public Analyzer getIndexAnalyzer() {
        String ftname = fileTypeName;
        if (ftname == null) {
            return indexAnalyzer;
        }
        Analyzer fa = AnalyzerGuru.getAnalyzer(ftname);
        return fa == null ? indexAnalyzer : fa;
    }

    public int getTabSize() {
        return tabSize;
    }

    public void setTabSize(int value) {
        this.tabSize = value;
    }

    /**
     * Sets the instance used (if applicable) for the {@link PassageFormatter}
     * assigned to {@link #setFormatter(PassageFormatter)}.
     * @param contextArgs a defined instance or {@code null}
     */
    public void setContextArgs(ContextArgs contextArgs) {
        this.contextArgs = contextArgs;
    }

    /**
     * Transiently arranges that {@link #getIndexAnalyzer()} returns a file type
     * name-specific analyzer during a subsequent call of
     * {@link #highlightFieldsUnionWork(java.lang.String[], org.apache.lucene.search.Query, int)}.
     * @param fields a defined instance
     * @param query a defined instance
     * @param docId a valid document ID
     * @return a defined instance or else {@code null} if there are no results
     * @throws IOException if accessing the Lucene document fails
     */
    public String highlightFieldsUnion(String[] fields, Query query, int docId)
            throws IOException {
        /*
         * Setting fileTypeName has to happen before getFieldHighlighter() is
         * called by highlightFieldsAsObjects() so that the result of
         * getIndexAnalyzer() (if it is called due to requiring ANALYSIS) can be
         * influenced by fileTypeName.
         */
        Document doc = searcher.doc(docId);
        fileTypeName = doc == null ? null : doc.get(QueryBuilder.TYPE);
        try {
            return highlightFieldsUnionWork(fields, query, docId);
        } finally {
            fileTypeName = null;
        }
    }

    /**
     * Calls
     * {@link #highlightFieldsAsObjects(java.lang.String[], org.apache.lucene.search.Query, int[], int[])},
     * and merges multiple passages if the formatter returns
     * {@link FormattedLines} or else returns the first formatted result.
     * @param fields a defined instance
     * @param query a defined instance
     * @param docId a valid document ID
     * @return a defined instance or else {@code null} if there are no results
     * @throws IOException if accessing the Lucene document fails
     */
    protected String highlightFieldsUnionWork(String[] fields, Query query, int docId)
            throws IOException {

        int lineLimit = contextArgs != null ? contextArgs.getContextLimit() : Short.MAX_VALUE;
        int[] maxPassagesCopy = new int[fields.length];
        /*
         * N.b. linelimit + 1 so that the ContextFormatter has an indication
         * when to display the "more..." link. If we're showing surrounding
         * context, though, then leave this essentially unbounded (initially),
         * or else Lucene's BM25 sampling when limited can result in highlights
         * confusingly missing from surrounding context.
         */
        Arrays.fill(maxPassagesCopy, contextArgs != null &&
                contextArgs.getContextSurround() > 0 ? Short.MAX_VALUE : lineLimit + 1);

        FormattedLines res = null;
        Map<String, Object[]> mappedRes = highlightFieldsAsObjects(fields,
            query, new int[]{docId}, maxPassagesCopy);
        for (Object[] passageObjects : mappedRes.values()) {
            for (Object obj : passageObjects) {
                /*
                 * Empirical testing showed that the passage could be null if
                 * the original source text is not available to the highlighter.
                 */
                if (obj != null) {
                    if (!(obj instanceof FormattedLines)) {
                        return obj.toString();
                    }
                    FormattedLines flines = (FormattedLines) obj;
                    res = res == null ? flines : res.merge(flines);
                }
            }
        }
        if (res == null) {
            return null;
        }
        if (res.getCount() > lineLimit) {
            res.setLimited(true);
            while (res.getCount() > lineLimit) {
                res.pop();
            }
        }
        return res.toString();
    }

    /**
     * Produces original text by reading from OpenGrok source content relative
     * to {@link RuntimeEnvironment#getSourceRootPath()} and returns the content
     * for each document if the timestamp matches -- or else just {@code null}
     * for a missing file or a timestamp mismatch (as "the returned Strings must
     * be identical to what was indexed.")
     * <p>
     * "This method must load fields for at least one document from the given
     * {@link DocIdSetIterator} but need not return all of them; by default the
     * character lengths are summed and this method will return early when
     * {@code cacheCharsThreshold} is exceeded. Specifically if that number is
     * 0, then only one document is fetched no matter what. Values in the array
     * of {@link CharSequence} will be {@code null} if no value was found."
     * @return a defined instance
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected List<CharSequence[]> loadFieldValues(String[] fields,
        DocIdSetIterator docIter, int cacheCharsThreshold) throws IOException {

        List<CharSequence[]> docListOfFields = new ArrayList<>(
            cacheCharsThreshold == 0 ? 1 : (int) Math.min(64, docIter.cost()));

        int sumChars = 0;
        do {
            int docId = docIter.nextDoc();
            if (docId == DocIdSetIterator.NO_MORE_DOCS) {
                break;
            }
            Document doc = searcher.doc(docId);

            String path = doc.get(QueryBuilder.PATH);
            String storedU = doc.get(QueryBuilder.U);
            String content = getRepoFileContent(path, storedU);

            CharSequence[] seqs = new CharSequence[fields.length];
            Arrays.fill(seqs, content);
            docListOfFields.add(seqs);

            if (content != null) {
                sumChars += content.length();
            }
        } while (sumChars <= cacheCharsThreshold && cacheCharsThreshold != 0);

        return docListOfFields;
    }

    /**
     * Returns the value from the {@code super} implementation, with logging for
     * ANALYSIS of any field but {@link QueryBuilder#FULL} or
     * {@link QueryBuilder#REFS}.
     * @return the value from the {@code super} implementation
     */
    @Override
    protected OffsetSource getOptimizedOffsetSource(UHComponents components) {

        OffsetSource res = super.getOptimizedOffsetSource(components);
        String field = components.getField();
        if (res == OffsetSource.ANALYSIS) {
            /*
             *     Testing showed that UnifiedHighlighter falls back to
             * ANALYSIS in the presence of multi-term queries (MTQs) such as
             * prefixes and wildcards even for fields that are analyzed with
             * POSTINGS -- i.e. with DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS.
             * This is despite UnifiedHighlighter seeming to indicate that
             * postings should be sufficient in the comment for
             * shouldHandleMultiTermQuery(String): "MTQ highlighting can be
             * expensive, particularly when using offsets in postings."
             *     DEFS are stored with term vectors to avoid this problem,
             * since re-analysis would not at all accord with ctags Definitions.
             *     For FULL and REFS, highlightFieldsUnion() arranges that
             * getIndexAnalyzer() can return a TYPE-specific analyzer for use by
             * getOffsetStrategy() -- if re-ANALYSIS is required.
             */
            switch (field) {
                case QueryBuilder.FULL:
                case QueryBuilder.REFS:
                    // Acceptable -- as described above.
                    break;
                default:
                    if (LOGGER.isLoggable(Level.FINE)) {
                        OffsetSource defaultRes = getOffsetSource(field);
                        LOGGER.log(Level.FINE, "Field {0} using {1} vs {2}",
                            new Object[]{field, res, defaultRes});
                    }
                    break;
            }
        }
        return res;
    }

    private String getRepoFileContent(String repoRelPath, String storedU)
            throws IOException {

        if (storedU == null) {
            LOGGER.log(Level.FINE, "Missing U[UID] for: {0}",
                repoRelPath);
            return null;
        }

        String repoAbsPath = env.getSourceRootPath() + repoRelPath;
        File repoAbsFile = new File(repoAbsPath);
        if (!repoAbsFile.exists()) {
            LOGGER.log(Level.FINE, "Missing file: {0}", repoAbsPath);
            return null;
        }

        repoRelPath = Util.fixPathIfWindows(repoRelPath);
        // Verify that timestamp (U) is unchanged by comparing UID.
        String uid = Util.path2uid(repoRelPath,
            DateTools.timeToString(repoAbsFile.lastModified(),
            DateTools.Resolution.MILLISECOND));
        BytesRef buid = new BytesRef(uid);
        BytesRef storedBuid = new BytesRef(storedU);
        if (storedBuid.compareTo(buid) != 0) {
            LOGGER.log(Level.FINE, "Last-modified differs for: {0}",
                repoRelPath);
            return null;
        }

        StringBuilder bld = new StringBuilder();
        StreamSource src = StreamSource.fromFile(repoAbsFile);
        try (InputStream in = src.getStream();
            Reader rdr = getReader(in)) {
            int c;
            while ((c = rdr.read()) != -1) {
                bld.append((char) c);
            }
        }

        return bld.toString();
    }

    private Reader getReader(InputStream in) throws IOException {
        Reader bsrdr = IOUtils.createBOMStrippedReader(in,
            StandardCharsets.UTF_8.name());
        BufferedReader bufrdr = new BufferedReader(bsrdr);
        return ExpandTabsReader.wrap(bufrdr, tabSize);
    }
}
