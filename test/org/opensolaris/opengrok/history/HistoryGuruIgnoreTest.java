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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Test behavior or ignored repositories in HistoryGuru. These tests are
 * separated from HistoryGuru tests since it modifies the list of repositories
 * before the class.
 *
 * @author Vladimir Kotal
 */
public class HistoryGuruIgnoreTest {
    private static TestRepository repository = new TestRepository();
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream(
                "repositories.zip"));
        RepositoryFactory.initializeIgnoredNames(RuntimeEnvironment.getInstance());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        repository.destroy();
    }

    /**
     * Test that a repository with ".opengrok_skip_history" file inside
     * is ignored when creating history cache.
     */
    @Test
    public void testIgnoredHistory() throws Exception {

        File srcRoot = new File(repository.getSourceRoot());
        File mercurialRepo = new File(srcRoot, "mercurial");
        Assert.assertTrue(mercurialRepo.isDirectory());
        File ignoredFile = new File(mercurialRepo, ".opengrok_skip_history");
        Assert.assertTrue(ignoredFile.createNewFile());

        Assert.assertEquals(0,
                RuntimeEnvironment.getInstance().getRepositories().size());

        HistoryGuru histguru = HistoryGuru.getInstance();
        histguru.addRepositories(repository.getSourceRoot());

        // addRepositories() adds the list to the RuntimeEnvironment so the
        // ignored repository should not be there.
        Assert.assertFalse(RuntimeEnvironment.getInstance().getRepositories().
                stream().map(x -> x.getDirectoryName()).
                collect(Collectors.toList()).contains("mercurial"));

        // create cache with initial set of repos
        histguru.createCache();

        // Check that the history was not actually generated for the repository.
        File dataRoot = new File(repository.getDataRoot());
        File historyCache = new File(dataRoot, "historycache");
        Assert.assertFalse(new File(historyCache, "mercurial").exists());
    }
}
