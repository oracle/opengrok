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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.util.Version;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Index checker.
 *
 * @author Vladimír Kotal
 */
public class IndexCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexCheck.class);

    /**
     * Exception thrown when index version does not match Lucene version.
     */
    public static class IndexVersionException extends Exception {

        private static final long serialVersionUID = 5693446916108385595L;

        private int luceneIndexVersion = -1;
        private int indexVersion = -1;

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

    private IndexCheck() {
        // utility class
    }
    
    /**
     * Check if version of index(es) matches major Lucene version.
     * @param subFilesList list of paths. If non-empty, only projects matching these paths will be checked.
     * @return true on success, false on failure
     */
    public static boolean check(List<String> subFilesList) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        File indexRoot = new File(env.getDataRootPath(), IndexDatabase.INDEX_DIR);
        LOGGER.log(Level.FINE, "Checking for Lucene index version mismatch in {0}",
                indexRoot);
        int ret = 0;

        if (!subFilesList.isEmpty()) {
            // Assumes projects are enabled.
            for (String projectName : subFilesList) {
                LOGGER.log(Level.FINER,
                        "Checking Lucene index version in project {0}",
                        projectName);
                ret |= checkDirNoExceptions(new File(indexRoot, projectName));
            }
        } else {
            if (env.isProjectsEnabled()) {
                for (String projectName : env.getProjects().keySet()) {
                    LOGGER.log(Level.FINER,
                            "Checking Lucene index version in project {0}",
                            projectName);
                    ret |= checkDirNoExceptions(new File(indexRoot, projectName));
                }
            } else {
                LOGGER.log(Level.FINER, "Checking Lucene index version in {0}",
                        indexRoot);
                ret |= checkDirNoExceptions(indexRoot);
            }
        }

        return ret == 0;
    }

    private static int checkDirNoExceptions(File dir) {
        try {
            checkDir(dir);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Index check for directory " + dir.toString() + " failed", e);
            return 1;
        }

        return 0;
    }

    /**
     * Check index in given directory. It assumes that that all commits (if any)
     * in the Lucene segment file were done with the same version.
     *
     * @param dir directory with index
     * @throws IOException if the directory cannot be opened
     * @throws IndexVersionException if the version of the index does not match Lucene index version
     */
    public static void checkDir(File dir) throws IndexVersionException, IOException {
        LockFactory lockFactory = NativeFSLockFactory.INSTANCE;
        int segVersion;

        try (Directory indexDirectory = FSDirectory.open(dir.toPath(), lockFactory)) {
           SegmentInfos segInfos;

            segInfos = SegmentInfos.readLatestCommit(indexDirectory);
            segVersion = segInfos.getIndexCreatedVersionMajor();
        }

        if (segVersion != Version.LATEST.major) {
            throw new IndexVersionException(
                String.format("Directory %s has index version discrepancy", dir.toString()),
                    Version.LATEST.major, segVersion);
        }
    }
}
