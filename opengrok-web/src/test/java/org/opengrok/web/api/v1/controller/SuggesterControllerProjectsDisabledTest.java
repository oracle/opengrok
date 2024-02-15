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
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.configuration.SuggesterConfig;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.RestApp;
import org.opengrok.web.api.v1.suggester.provider.service.impl.SuggesterServiceImpl;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class SuggesterControllerProjectsDisabledTest extends OGKJerseyTest {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static TestRepository repository;

    @Override
    protected DeploymentContext configureDeployment() {
        return ServletDeploymentContext.forServlet(new ServletContainer(new RestApp())).build();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new GrizzlyWebTestContainerFactory();
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(SuggesterControllerTest.class.getClassLoader().getResource("sources"));

        env.setHistoryEnabled(false);
        env.setProjectsEnabled(false);
        env.setSourceRoot(repository.getSourceRoot() + File.separator + "java");
        Indexer.getInstance().prepareIndexer(env, true, true,
                null, null);
        env.setDefaultProjectsFromNames(Collections.singleton("__all__"));
        Indexer.getInstance().doIndexerExecution(null, null);

        env.getSuggesterConfig().setRebuildCronConfig(null);
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
    }

    @BeforeEach
    void before() throws Exception {
        SuggesterServiceImpl.getInstance().waitForInit(15, TimeUnit.SECONDS);

        env.setSuggesterConfig(new SuggesterConfig());
    }

    @Test
    void suggestionsSimpleTest() {
        SuggesterControllerTest.Result res = target(SuggesterController.PATH)
                .queryParam("field", QueryBuilder.FULL)
                .queryParam(QueryBuilder.FULL, "inner")
                .request()
                .get(SuggesterControllerTest.Result.class);

        assertThat(res.suggestions.stream().map(r -> r.phrase).collect(Collectors.toList()),
                containsInAnyOrder("innermethod", "innerclass"));
    }

}
