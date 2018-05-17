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
 * Copyright (c) 2008, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis.plain;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.ConditionalRunRule;
import org.opensolaris.opengrok.condition.CtagsInstalled;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.Indexer;
import org.opensolaris.opengrok.util.TestRepository;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.index.DefaultIndexChangedListener;
import org.opensolaris.opengrok.index.IndexChangedListener;
import org.opensolaris.opengrok.search.SearchEngine;

/**
 * Represents a container for tests of {@link PlainAnalyzer} and
 * {@link SearchEngine}.
 * <p>
 * Derived from Trond Norbye's {@code SearchEngineTest}
 */
@ConditionalRun(CtagsInstalled.class)
public class PlainAnalyzerTest {

    private static final String TURKISH_TEST_STRINGS = "TR.strings";

    private static RuntimeEnvironment env;
    private static TestRepository repository;
    private static File configFile;
    private static boolean originalProjectsEnabled;
    private static boolean originalAllNonWhitespace;

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();

        originalProjectsEnabled = env.isProjectsEnabled();
        env.setProjectsEnabled(false);
        originalAllNonWhitespace = env.isAllNonWhitespace();
        env.setAllNonWhitespace(true);

        repository = new TestRepository();
        repository.createEmpty();
        repository.addAdhocFile(TURKISH_TEST_STRINGS,
            PlainAnalyzerTest.class.getResourceAsStream(TURKISH_TEST_STRINGS),
            null);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        RepositoryFactory.initializeIgnoredNames(env);

        env.setVerbose(false);
        env.setHistoryEnabled(false);
        IndexChangedListener progress = new DefaultIndexChangedListener();
        Indexer.getInstance().prepareIndexer(env, true, true,
            new TreeSet<>(Arrays.asList(new String[]{"/c"})),
            false, false, null, null, new ArrayList<>(), false);
        Indexer.getInstance().doIndexerExecution(true, null, progress);

        configFile = File.createTempFile("configuration", ".xml");
        env.writeConfiguration(configFile);
        env.readConfiguration(new File(configFile.getAbsolutePath()));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        env.setProjectsEnabled(originalProjectsEnabled);
        env.setAllNonWhitespace(originalAllNonWhitespace);
        if (repository != null) {
            repository.destroy();
        }
        if (configFile != null) {
            configFile.delete();
        }
    }

    @Test
    public void testSearchForPlainFiles() throws IOException {
        SearchEngine instance;
        int noHits;

        instance = new SearchEngine();
        instance.setFile(TURKISH_TEST_STRINGS);
        instance.setFreetext("\\%\\@\\\"");
        noHits = instance.search();
        assertTrue("noHits for " + TURKISH_TEST_STRINGS + " should be positive",
            noHits > 0);
        instance.destroy();
    }
}
