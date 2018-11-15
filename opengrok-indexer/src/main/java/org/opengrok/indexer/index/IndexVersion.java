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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.util.Version;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Index version checker
 *
 * @author Vladimir Kotal
 */
public class IndexVersion {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(IndexVersion.class);

    /**
     * exception thrown when index version does not match Lucene version
     */
    public static class IndexVersionException extends Exception {

        public IndexVersionException(String s) {
            super(s);
        }
    }
    
    /**
     * Check if version of index(es) matches major Lucene version.
     * @param subFilesList list of paths. If non-empty, only projects matching these paths will be checked.
     * @throws Exception otherwise
     */
    public static void check(List<String> subFilesList) throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        File indexRoot = new File(env.getDataRootPath(), IndexDatabase.INDEX_DIR);
        LOGGER.log(Level.FINE, "Checking for Lucene index version mismatch in {0}",
                indexRoot);

        if (!subFilesList.isEmpty()) {
            // Assumes projects are enabled.
            for (String projectName : subFilesList) {
                LOGGER.log(Level.FINER,
                        "Checking Lucene index version in project {0}",
                        projectName);
                checkDir(getDirectory(new File(indexRoot, projectName)));
            }
        } else {
            if (env.isProjectsEnabled()) {
                for (String projectName : env.getProjects().keySet()) {
                    LOGGER.log(Level.FINER,
                            "Checking Lucene index version in project {0}",
                            projectName);
                    checkDir(getDirectory(new File(indexRoot, projectName)));
                }
            } else {
                LOGGER.log(Level.FINER, "Checking Lucene index version in {0}",
                        indexRoot);
                checkDir(getDirectory(indexRoot));
            }
        }
    }
    
    private static Directory getDirectory(File indexDir) throws IOException {
        LockFactory lockfact = NativeFSLockFactory.INSTANCE;
        FSDirectory indexDirectory = FSDirectory.open(indexDir.toPath(), lockfact);
        return indexDirectory;
    }

    /**
     * Check index version in given directory. It assumes that that all commits
     * in the Lucene segment file were done with the same version.
     *
     * @param dir directory with index
     * @thows IOException if the directory cannot be opened
     */
    private static void checkDir(Directory dir) throws IOException, Exception {
        int segVersion;
        try {
            segVersion = SegmentInfos.readLatestCommit(dir).getIndexCreatedVersionMajor();
        } catch (IndexNotFoundException e) {
            return;
        }
        if (segVersion != Version.LATEST.major) {
            throw new IndexVersionException(
                String.format("Directory %s has index of version %d and Lucene has %d",
                dir.toString(), segVersion, Version.LATEST.major));
        }
    }
}
