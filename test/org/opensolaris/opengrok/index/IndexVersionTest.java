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
 */
package org.opensolaris.opengrok.index;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.ConditionalRunRule;
import org.opensolaris.opengrok.condition.CtagsInstalled;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.util.FileUtilities;
import org.opensolaris.opengrok.util.IOUtils;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Verify index version check.
 * @author Vladimir Kotal
 */
public class IndexVersionTest {

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    TestRepository repository;
    RuntimeEnvironment env = RuntimeEnvironment.getInstance();
    private File oldIndexDataDir;
    
    @BeforeClass
    public static void setUpClass() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        RepositoryFactory.initializeIgnoredNames(env);
    }

    @Before
    public void setUp() throws IOException {
        repository = new TestRepository();
        repository.create(IndexerTest.class.
                getResourceAsStream("/org/opensolaris/opengrok/history/repositories.zip"));
        oldIndexDataDir = null;
    }

    @After
    public void tearDown() throws IOException {
        repository.destroy();
        
        if (oldIndexDataDir != null) {
            IOUtils.removeRecursive(Paths.get(oldIndexDataDir.getPath()));
        }
    }
    
    /**
     * Generate index(es) and check version.
     * @throws Exception 
     */
    private void testIndexVersion(boolean projectsEnabled) throws Exception {
        env.setVerbose(true);
        env.setHistoryEnabled(false);
        env.setProjectsEnabled(projectsEnabled);
        Indexer.getInstance().prepareIndexer(env, true, true, null,
                false, false, null, null, new ArrayList<>(), false);
        Indexer.getInstance().doIndexerExecution(true, null, null);

        IndexVersion.check(env.getConfiguration());
    }
    
    @Test
    public void testIndexVersionNoIndex() throws Exception {
        IndexVersion.check(env.getConfiguration());
    }
    
    @Test
    @ConditionalRun(CtagsInstalled.class)
    public void testIndexVersionNoProjects() throws Exception {
        testIndexVersion(true);
    }
    
    @Test
    @ConditionalRun(CtagsInstalled.class)
    public void testIndexVersionProjects() throws Exception {
        testIndexVersion(false);
    }
    
    @Test(expected = IndexVersion.IndexVersionException.class)
    public void testIndexVersionOldIndex() throws Exception {
        Configuration cfg = new Configuration();
        oldIndexDataDir = FileUtilities.createTemporaryDirectory("data");
        Path indexPath = Paths.get(oldIndexDataDir.getPath(), "index");
        Files.createDirectory(indexPath);
        File indexDir = new File(indexPath.toString());
        assertTrue("index directory check", indexDir.isDirectory());
        URL oldindex = getClass().getResource("oldindex.zip");
        assertNotNull("resource needs to be non null", oldindex);
        File archive = new File(oldindex.getPath());
        assertTrue("archive exists", archive.isFile());
        FileUtilities.extractArchive(archive, indexDir);
        cfg.setDataRoot(oldIndexDataDir.getPath());
        cfg.setProjectsEnabled(false);
        IndexVersion.check(cfg);
    }
}
