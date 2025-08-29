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
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.QueryParameters;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiffTest {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static TestRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        repository = new TestRepository();
        URL resource = DiffTest.class.getResource("/repositories");
        assertNotNull(resource);
        repository.create(resource);

        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer.getInstance().prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially
    }

    @AfterAll
    static void tearDown() {
        repository.destroy();
    }

    @Test
    void testGetDiffDataNoParams() {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("foo/bar");
        when(req.getContextPath()).thenReturn("/source");

        PageConfig pageConfig = PageConfig.get(req);
        DiffData diffData = pageConfig.getDiffData();
        assertNotNull(diffData);
        assertNotNull(diffData.getErrorMsg());
        assertTrue(diffData.getErrorMsg().startsWith("Please pick"));
    }

    @Test
    void testGetDiffDataInvalidRevision() {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("git/main.c");
        when(req.getContextPath()).thenReturn("/source");
        when(req.getParameter(QueryParameters.REVISION_PARAM + "1")).thenReturn("main.c@abc");
        when(req.getParameter(QueryParameters.REVISION_PARAM + "2")).thenReturn("main.c@xyz");

        PageConfig pageConfig = PageConfig.get(req);
        DiffData diffData = pageConfig.getDiffData();
        assertNotNull(diffData);
        assertNotNull(diffData.getErrorMsg());
        assertTrue(diffData.getErrorMsg().startsWith("Unable to get revision"));
        assertAll(
                () -> assertNull(diffData.getRevision()),
                () -> assertNull(diffData.getParam(0)),
                () -> assertNull(diffData.getParam(1)),
                () -> assertNull(diffData.getType())
        );
    }

    @Test
    void testGetDiffData() {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/git/main.c");
        when(req.getContextPath()).thenReturn("/source");
        final String rev1 = "bb74b7e";
        final String rev2 = "aa35c25";
        when(req.getParameter(QueryParameters.REVISION_PARAM + "1")).
                thenReturn("/git/main.c@" + rev1);
        when(req.getParameter(QueryParameters.REVISION_PARAM + "2")).
                thenReturn("/git/main.c@" + rev2);

        PageConfig pageConfig = PageConfig.get(req);
        DiffData diffData = pageConfig.getDiffData();
        assertNotNull(diffData);
        assertNull(diffData.getErrorMsg());
        assertAll(() -> assertEquals(rev1, diffData.getRev(0)),
                () -> assertEquals(rev2, diffData.getRev(1)),
                () -> assertTrue(diffData.getFile(0).length > 0),
                () -> assertTrue(diffData.getFile(1).length > 0),
                () -> assertNotNull(diffData.getRevision()),
                () -> assertEquals("/git/main.c@bb74b7e", diffData.getParam(0)),
                () -> assertEquals("/git/main.c@aa35c25", diffData.getParam(1)),
                () -> assertEquals(DiffType.SIDEBYSIDE, diffData.getType()),
                () -> assertFalse(diffData.isFull())
        );
    }
}
