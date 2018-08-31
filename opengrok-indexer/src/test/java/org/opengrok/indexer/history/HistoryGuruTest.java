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
 */
package org.opengrok.indexer.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opengrok.indexer.condition.ConditionalRun;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.FileUtilities;
import org.opengrok.indexer.util.TestRepository;

/**
 * Test the functionality provided by the HistoryGuru (with friends)
 *
 * @author Trond Norbye
 * @author Vladimir Kotal
 */
public class HistoryGuruTest {

    private static TestRepository repository = new TestRepository();
    private static final List<File> FILES = new ArrayList<>();

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

    @BeforeClass
    public static void setUpClass() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream(
                "repositories.zip"));
        RepositoryFactory.initializeIgnoredNames(env);
        FileUtilities.getAllFiles(new File(repository.getSourceRoot()),
                FILES, true);
        Assert.assertNotEquals(0, FILES.size());
        
        HistoryGuru histGuru = HistoryGuru.getInstance();
        assertNotNull(histGuru);
        Assert.assertEquals(0, histGuru.getRepositories().size());
        
        // Add initial set of repositories to HistoryGuru and RuntimeEnvironment.
        // This is a test in itself. While this makes the structure of the tests
        // a bit incomprehensible, it does not make sense to run the rest of tests
        // if the basic functionality does not work.
        env.setRepositories(repository.getSourceRoot());
        Assert.assertTrue(histGuru.getRepositories().size() > 0);
        Assert.assertEquals(histGuru.getRepositories().size(),
                env.getRepositories().size());
        
        // Create cache with initial set of repositories.
        histGuru.createCache();
    }

    @AfterClass
    public static void tearDownClass() {
        repository.destroy();
    }

    @Test
    public void testUpdateRepositories() {
        HistoryGuru instance = HistoryGuru.getInstance();
        instance.updateRepositories();
    }

    @Test
    public void testGetRevision() throws HistoryException, IOException {
        HistoryGuru instance = HistoryGuru.getInstance();

        for (File f : FILES) {
            if (f.isFile() && instance.hasHistory(f)) {
                for (HistoryEntry entry
                        : instance.getHistory(f).getHistoryEntries()) {
                    String revision = entry.getRevision();
                    try (InputStream in = instance.getRevision(
                            f.getParent(), f.getName(), revision)) {
                        assertNotNull("Failed to get revision " + revision
                                + " of " + f.getAbsolutePath(), in);
                    }
                }
            }
        }
    }

    @Test
    public void testBug16465() throws HistoryException, IOException {
        HistoryGuru instance = HistoryGuru.getInstance();
        for (File f : FILES) {
            if (f.getName().equals("bugreport16465@")) {
                assertNotNull(instance.getHistory(f));
                assertNotNull(instance.annotate(f, null));
            }
        }
    }

    @Test
    public void annotation() throws Exception {
        HistoryGuru instance = HistoryGuru.getInstance();
        for (File f : FILES) {
            if (instance.hasAnnotation(f)) {
                instance.annotate(f, null);
            }
        }
    }

    @Test
    public void getCacheInfo() throws HistoryException {
        // FileHistoryCache is used by default
        assertEquals("FileHistoryCache",
                HistoryGuru.getInstance().getCacheInfo());
    }
    
    @Test
    @ConditionalRun(RepositoryInstalled.GitInstalled.class)
    public void testAddRemoveRepositories() {
        HistoryGuru instance = HistoryGuru.getInstance();
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        final int numReposOrig = instance.getRepositories().size();
        
        // Try to add non-existent repository.
        Collection<String> repos = new ArrayList<>();
        repos.add("totally-nonexistent-repository");
        Collection<RepositoryInfo> added = instance.addRepositories(repos,
                env.getIgnoredNames());
        Assert.assertEquals(0, added.size());
        Assert.assertEquals(numReposOrig, instance.getRepositories().size());
        
        // Remove one repository.
        repos = new ArrayList<>();
        repos.add(env.getSourceRootPath() + File.separator + "git");
        instance.removeRepositories(repos);
        Assert.assertEquals(numReposOrig - 1, instance.getRepositories().size());
        
        // Add the repository back.
        added = instance.addRepositories(repos, env.getIgnoredNames());
        Assert.assertEquals(1, added.size());
        Assert.assertEquals(numReposOrig, instance.getRepositories().size());
    }
}
