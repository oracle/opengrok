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
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.analysis.executables;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.authorization.AuthorizationFrameworkReloadTest;
import org.opengrok.configuration.RuntimeEnvironment;
import org.opengrok.index.Indexer;
import org.opengrok.util.TestRepository;
import org.opengrok.history.RepositoryFactory;
import org.opengrok.index.DefaultIndexChangedListener;
import org.opengrok.index.IndexChangedListener;
import org.opengrok.search.SearchEngine;

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
    private static boolean skip = false;
    private static boolean originalProjectsEnabled;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        originalProjectsEnabled = env.isProjectsEnabled();
        env.setProjectsEnabled(false);

        repository = new TestRepository();
        repository.createEmpty();
        repository.addAdhocFile(TESTPLUGINS_JAR,
            AuthorizationFrameworkReloadTest.class.getResourceAsStream(
            TESTPLUGINS_JAR), null);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        RepositoryFactory.initializeIgnoredNames(env);

        if (env.validateExuberantCtags()) {
            env.setVerbose(false);
            env.setHistoryEnabled(false);
            IndexChangedListener progress = new DefaultIndexChangedListener();
            Indexer.getInstance().prepareIndexer(env, true, true,
                new TreeSet<>(Arrays.asList(new String[]{"/c"})),
                false, false, null, null, new ArrayList<>(), false);

            Indexer.getInstance().doIndexerExecution(true, null, progress);
        } else {
            System.out.println(
                "Skipping test. Could not find a ctags I could use in path.");
            skip = true;
        }

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        env.readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        env.setProjectsEnabled(originalProjectsEnabled);
        if (repository != null) repository.destroy();
        if (configFile != null) configFile.delete();
        skip = false;
    }

    @Test
    public void testSearchForJar() throws IOException {
        if (skip) return;

        SearchEngine instance;
        int noHits;

        instance = new SearchEngine();
        instance.setFile(TESTPLUGINS_JAR);
        noHits = instance.search();
        assertTrue("noHits for " + TESTPLUGINS_JAR + " should be positive",
            noHits > 0);
        instance.destroy();
    }
}
