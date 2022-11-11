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
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.IndexDatabase;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;

import java.io.File;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for getting the latest revision of a file.
 */
public class LatestRevisionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LatestRevisionUtil.class);

    private LatestRevisionUtil() {
        // private to enforce static
    }

    /**
     * @param file file object corresponding to a file under source root
     * @return last revision string for {@code file} or null
     */
    @Nullable
    public static String getLatestRevision(File file) {
        if (!RuntimeEnvironment.getInstance().isHistoryEnabled()) {
            return null;
        }

        String lastRev = getLastRevFromIndex(file);
        if (lastRev != null) {
            return lastRev;
        }

        // fallback
        try {
            return getLastRevFromHistory(file);
        } catch (HistoryException e) {
            LOGGER.log(Level.WARNING, "cannot get latest revision for {0}", file);
            return null;
        }
    }

    /**
     * Retrieve last revision from the document matching the file (if any).
     * @param file object corresponding to a file under source root
     * @return last revision or {@code null} if the document cannot be found, is out of sync
     * w.r.t. last modified time of the file or the last commit ID is not stored in the document.
     */
    @Nullable
    @VisibleForTesting
    public static String getLastRevFromIndex(File file) {
        Document doc = null;
        try {
            doc = IndexDatabase.getDocument(file);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format("cannot get document for %s", file), e);
        }

        String lastRev = null;
        if (doc != null) {
            // There is no point of checking the date if the LASTREV field is not present.
            lastRev = doc.get(QueryBuilder.LASTREV);
            if (lastRev != null) {
                Date docDate;
                try {
                    docDate = DateTools.stringToDate(doc.get(QueryBuilder.DATE));
                } catch (ParseException e) {
                    LOGGER.log(Level.WARNING, String.format("cannot get date from the document %s", doc), e);
                    return null;
                }
                Date fileDate = new Date(file.lastModified());
                if (docDate.compareTo(fileDate) < 0) {
                    LOGGER.log(Level.FINER, "document for ''{0}'' is out of sync", file);
                    return null;
                }
            }
        }

        return lastRev;
    }

    @Nullable
    private static String getLastRevFromHistory(File file) throws HistoryException {
        HistoryEntry he = HistoryGuru.getInstance().getLastHistoryEntry(file, true);
        if (he != null) {
            return he.getRevision();
        }

        return null;
    }
}
