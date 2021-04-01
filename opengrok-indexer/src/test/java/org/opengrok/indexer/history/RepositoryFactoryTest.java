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
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.condition.RepositoryInstalled;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test RepositoryFactory.
 *
 * @author Vladimir Kotal
 */
public class RepositoryFactoryTest {
    private static RuntimeEnvironment env;
    private static TestRepository repository = new TestRepository();
    private static Set<String> savedDisabledRepositories;
    private static boolean savedIsProjectsEnabled;

    @BeforeAll
    public static void setUpClass() throws Exception {
        env = RuntimeEnvironment.getInstance();
        repository.create(RepositoryFactoryTest.class.getResourceAsStream("repositories.zip"));
        savedDisabledRepositories = env.getDisabledRepositories();
        savedIsProjectsEnabled = env.isProjectsEnabled();
    }
    
    @AfterAll
    public static void tearDownClass() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @AfterEach
    public void tearDown() {
        env.setRepoCmds(Collections.emptyMap());
        env.setDisabledRepositories(savedDisabledRepositories);
        env.setProjectsEnabled(savedIsProjectsEnabled);
    }

    @EnabledForRepository(RepositoryInstalled.Type.MERCURIAL)
    @Test
    public void testRepositoryMatchingSourceRoot() throws IllegalAccessException, InvocationTargetException,
            ForbiddenSymlinkException, InstantiationException, NoSuchMethodException, IOException {

        File root = new File(repository.getSourceRoot(), "mercurial");
        env.setSourceRoot(root.getAbsolutePath());
        env.setProjectsEnabled(true);
        Repository repo = RepositoryFactory.getRepository(root);
        assertNull(repo, "should not get repo for root if projects enabled");
    }

    @EnabledForRepository(RepositoryInstalled.Type.MERCURIAL)
    @Test
    public void testNormallyEnabledMercurialRepository() throws IllegalAccessException,
            InvocationTargetException, ForbiddenSymlinkException, InstantiationException,
            NoSuchMethodException, IOException {

        File root = new File(repository.getSourceRoot(), "mercurial");
        env.setSourceRoot(root.getAbsolutePath());
        assertNotNull(RepositoryFactory.getRepository(root), "should get repository for mercurial/");

        List<Class<? extends Repository>> clazzes = RepositoryFactory.getRepositoryClasses();
        assertTrue(clazzes.contains(MercurialRepository.class), "should contain MercurialRepository");
    }

    @EnabledForRepository(RepositoryInstalled.Type.MERCURIAL)
    @Test
    public void testMercurialRepositoryWhenDisabled() throws IllegalAccessException,
            InvocationTargetException, ForbiddenSymlinkException, InstantiationException,
            NoSuchMethodException, IOException {

        env.setDisabledRepositories(new HashSet<>(
                Collections.singletonList("MercurialRepository")));

        File root = new File(repository.getSourceRoot(), "mercurial");
        env.setSourceRoot(root.getAbsolutePath());
        assertNull(RepositoryFactory.getRepository(root), "should not get repository for mercurial/ if disabled");

        List<Class<? extends Repository>> clazzes = RepositoryFactory.getRepositoryClasses();
        assertFalse(clazzes.contains(MercurialRepository.class), "should not contain MercurialRepository");
    }

    /*
     * There is no conditional run based on whether given repository is installed because
     * this test is not supposed to have working Mercurial anyway.
     */
    static void testNotWorkingRepository(TestRepository repository, String repoPath, String propName)
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            IOException, ForbiddenSymlinkException {

        String origPropValue = System.setProperty(propName, "/foo/bar/nonexistent");
        try {
            File root = new File(repository.getSourceRoot(), repoPath);
            RuntimeEnvironment.getInstance().setSourceRoot(repository.getSourceRoot());
            Repository repo = RepositoryFactory.getRepository(root);
            assertNotNull(repo, "should have defined repo");
            assertFalse(repo.isWorking(), "repo should not be working");
        } finally {
            if (origPropValue != null) {
                System.setProperty(propName, origPropValue);
            } else {
                System.clearProperty(propName);
            }
        }
    }

    @Test
    public void testNotWorkingBitkeeperRepository()
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            IOException, ForbiddenSymlinkException {
        testNotWorkingRepository(repository, "bitkeeper", BitKeeperRepository.CMD_PROPERTY_KEY);
    }

    @Test
    public void testRepositoryFactoryEveryImplIsNamedAsRepository() {
        List<Class<? extends Repository>> repositoryClasses = RepositoryFactory.getRepositoryClasses();
        for (Class<? extends Repository> clazz : repositoryClasses) {
            assertTrue(clazz.getSimpleName().endsWith("Repository"), "should end with \"Repository\"");
        }
    }
}
