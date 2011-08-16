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
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import javax.servlet.http.HttpServletRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.util.TestRepository;

/**
 * Unit tests for the {@code PageConfig} class.
 */
public class PageConfigTest {
    private static TestRepository repository = new TestRepository();

    @BeforeClass
    public static void setUpClass() throws Exception {
        repository = new TestRepository();
        repository.create(
                HistoryGuru.class.getResourceAsStream("repositories.zip"));
        HistoryGuru.getInstance().addRepositories(repository.getSourceRoot());
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        repository.destroy();
        repository = null;
    }

    @Test
    public void canProcess() {
        // Expect no redirection (that is, empty string is returned) for a
        // file that exists.
        assertCanProcess("", "/source", "/xref", "/mercurial/main.c");
        assertCanProcess("", "/source", "/history", "/mercurial/main.c");

        // Expect directories without trailing slash to get a trailing slash
        // appended.
        assertCanProcess("/source/xref/mercurial/",
                         "/source", "/xref", "/mercurial");
        assertCanProcess("/source/history/mercurial/",
                         "/source", "/history", "/mercurial");

        // Expect no redirection (that is, empty string is returned) if the
        // directories already have a trailing slash.
        assertCanProcess("", "/source", "/xref", "/mercurial/");
        assertCanProcess("", "/source", "/history", "/mercurial/");

        // Expect null if the file or directory doesn't exist.
        assertCanProcess(null, "/source", "/xref", "/mercurial/xyz");
        assertCanProcess(null, "/source", "/history", "/mercurial/xyz");
        assertCanProcess(null, "/source", "/xref", "/mercurial/xyz/");
        assertCanProcess(null, "/source", "/history", "/mercurial/xyz/");
    }

    /**
     * Assert that {@code canProcess()} returns the expected value for the
     * specified path.
     *
     * @param expected the expected return value
     * @param context the context path
     * @param servlet the servlet path
     * @param pathInfo the path info
     */
    private void assertCanProcess(
            String expected, String context, String servlet, String pathInfo) {
        PageConfig config =
                PageConfig.get(createRequest(context, servlet, pathInfo));
        assertEquals(expected, config.canProcess());
    }

    /**
     * Create a request with the specified path elements.
     *
     * @param contextPath the context path
     * @param servletPath the path of the servlet
     * @param pathInfo the path info
     * @return a servlet request for the specified path
     */
    private static HttpServletRequest createRequest(
            final String contextPath, final String servletPath,
            final String pathInfo)
    {
        return new DummyHttpServletRequest() {
            @Override
            public String getContextPath() {
                return contextPath;
            }
            @Override
            public String getServletPath() {
                return servletPath;
            }
            @Override
            public String getPathInfo() {
                return pathInfo;
            }
        };
    }
}
