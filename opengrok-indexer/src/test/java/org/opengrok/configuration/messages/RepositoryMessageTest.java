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
package org.opengrok.configuration.messages;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.condition.ConditionalRun;
import org.opengrok.condition.RepositoryInstalled;
import org.opengrok.configuration.RuntimeEnvironment;
import org.opengrok.history.HistoryGuru;
import org.opengrok.history.MercurialRepositoryTest;
import org.opengrok.history.RepositoryFactory;
import org.opengrok.history.RepositoryInfo;
import org.opengrok.index.Indexer;
import org.opengrok.util.TestRepository;

/**
 * Test repository message handling.
 * 
 * @author Vladimir Kotal
 */
public class RepositoryMessageTest {
    
    RuntimeEnvironment env;

    private static TestRepository repository = new TestRepository();
    
    @Before
    public void setUp() throws IOException {
        repository = new TestRepository();
        repository.create(HistoryGuru.class.getResourceAsStream(
                "repositories.zip"));

        env = RuntimeEnvironment.getInstance();
        env.removeAllMessages();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);
    }
    
    @After
    public void tearDown() {
        if (env != null) {
            env.removeAllMessages();

            // This should match Configuration constructor.
            env.setProjects(new ConcurrentHashMap<>());
            env.setRepositories(new ArrayList<RepositoryInfo>());
            env.getProjectRepositoriesMap().clear();
        }

        repository.destroy();
    }
    
    @Test
    public void testValidate() {
        Message m = new RepositoryMessage();
        Assert.assertFalse(MessageTest.assertValid(m));
        m.addTag("foo");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText("text");
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText(null);
        Assert.assertFalse(MessageTest.assertValid(m));
        m.setText("get-repo-type");
        Assert.assertTrue(MessageTest.assertValid(m));
    }
    
    @ConditionalRun(condition = RepositoryInstalled.MercurialInstalled.class)
    @ConditionalRun(condition = RepositoryInstalled.GitInstalled.class)
    @Test
    public void testGetRepositoryType() throws Exception {
        Message m = new RepositoryMessage();
        m.setText("get-repo-type");
        m.addTag("/totally-nonexistent-repository");
        String out = new String(m.apply(env));
        Assert.assertEquals("/totally-nonexistent-repository:N/A", out);
        
        // Create subrepository.
        File mercurialRoot = new File(repository.getSourceRoot() + File.separator + "mercurial");
        MercurialRepositoryTest.runHgCommand(mercurialRoot,
            "clone", mercurialRoot.getAbsolutePath(),
            mercurialRoot.getAbsolutePath() + File.separator + "closed");
        
        env.setHistoryEnabled(true);
        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                null, // no default project
                false, // don't list files
                false, // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null, // repositories - needed when refreshing history partially
                new ArrayList<>(), // don't zap cache
                false); // don't list repos
        
        m = new RepositoryMessage();
        m.setText("get-repo-type");
        m.addTag("/mercurial");
        out = new String(m.apply(env));
        Assert.assertEquals("/mercurial:Mercurial", out);
        
        m = new RepositoryMessage();
        m.setText("get-repo-type");
        m.addTag("/mercurial/closed");
        out = new String(m.apply(env));
        Assert.assertEquals("/mercurial/closed:Mercurial", out);
        
        m = new RepositoryMessage();
        m.setText("get-repo-type");
        m.addTag("/git");
        out = new String(m.apply(env));
        Assert.assertEquals("/git:git", out);
    }
}
