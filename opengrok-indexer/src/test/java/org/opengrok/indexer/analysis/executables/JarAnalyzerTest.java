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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.executables;

import java.io.File;
import java.util.Collections;
import java.util.TreeSet;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.authorization.AuthorizationFrameworkReloadTest;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.DefaultIndexChangedListener;
import org.opengrok.indexer.index.IndexChangedListener;
import org.opengrok.indexer.search.SearchEngine;

/**
 * Represents a container for tests of {@link JarAnalyzer} and
 * {@link SearchEngine}.
 * <p>
 * Derived from Trond Norbye's {@code SearchEngineTest}
 */
public class JarAnalyzerTest {

    private static final String TESTPLUGINS_JAR = "testplugins.jar";

    private static RuntimeEnvironment env;
    private static TestRepository repository;
    private static File configFile;
    private static boolean originalProjectsEnabled;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        originalProjectsEnabled = env.isProjectsEnabled();
        env.setProjectsEnabled(false);

        repository = new TestRepository();
        repository.createEmpty();
        repository.addAdhocFile(TESTPLUGINS_JAR,
            AuthorizationFrameworkReloadTest.class.getResourceAsStream("/authorization/plugins/" +
            TESTPLUGINS_JAR), null);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        RepositoryFactory.initializeIgnoredNames(env);

        env.setHistoryEnabled(false);
        IndexChangedListener progress = new DefaultIndexChangedListener();
        Indexer.getInstance().prepareIndexer(env, true, true,
                false, null, null);
        env.setDefaultProjectsFromNames(new TreeSet<>(Collections.singletonList("/c")));

        Indexer.getInstance().doIndexerExecution(true, null, progress);

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        env.readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterClass
    public static void tearDownClass() {
        env.setProjectsEnabled(originalProjectsEnabled);
        if (repository != null) {
            repository.destroy();
        }
        if (configFile != null) {
            configFile.delete();
        }
    }

    @Test
    public void testSearchForJar() {
        SearchEngine instance = new SearchEngine();
        instance.setFile(TESTPLUGINS_JAR);
        int noHits = instance.search();
        assertTrue("noHits for " + TESTPLUGINS_JAR + " should be positive",
            noHits > 0);
        instance.destroy();
    }
}
