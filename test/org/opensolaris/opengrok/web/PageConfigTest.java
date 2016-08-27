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
 * Copyright (c) 2011, 2016 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web;

import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.opensolaris.opengrok.condition.ConditionalRun;
import org.opensolaris.opengrok.condition.ConditionalRunRule;
import org.opensolaris.opengrok.condition.RepositoryInstalled;
import org.opensolaris.opengrok.history.Annotation;
import org.opensolaris.opengrok.history.HistoryGuru;
import org.opensolaris.opengrok.util.TestRepository;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@code PageConfig} class.
 */
public class PageConfigTest {
    private static TestRepository repository = new TestRepository();

    @Rule
    public ConditionalRunRule rule = new ConditionalRunRule();

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
    public void testRequestAttributes() {
        HttpServletRequest req = new DummyHttpServletRequest();
        PageConfig cfg = PageConfig.get(req);

        String[] attrs = {"a", "b", "c", "d"};

        Object[] values = {
            "some object",
            new DummyHttpServletRequest(),
            1,
            this
        };

        assertEquals(attrs.length, values.length);

        for (int i = 0; i < attrs.length; i++) {
            cfg.setRequestAttribute(attrs[i], values[i]);

            Object attribute = req.getAttribute(attrs[i]);
            assertNotNull(attribute);
            assertEquals(values[i], attribute);

            attribute = cfg.getRequestAttribute(attrs[i]);
            assertNotNull(attribute);
            assertEquals(values[i], attribute);
        }
    }

    @ConditionalRun(condition = RepositoryInstalled.MercurialInstalled.class)
    @Test
    public void canProcessHistory() {
        // Expect no redirection (that is, empty string is returned) for a
        // file that exists.
        assertCanProcess("", "/source", "/history", "/mercurial/main.c");

        // Expect directories without trailing slash to get a trailing slash
        // appended.
        assertCanProcess("/source/history/mercurial/",
                         "/source", "/history", "/mercurial");

        // Expect no redirection (that is, empty string is returned) if the
        // directories already have a trailing slash.
        assertCanProcess("", "/source", "/history", "/mercurial/");

        // Expect null if the file or directory doesn't exist.
        assertCanProcess(null, "/source", "/history", "/mercurial/xyz");
        assertCanProcess(null, "/source", "/history", "/mercurial/xyz/");
    }

    @Test
    public void canProcessXref() {
        // Expect no redirection (that is, empty string is returned) for a
        // file that exists.
        assertCanProcess("", "/source", "/xref", "/mercurial/main.c");

        // Expect directories without trailing slash to get a trailing slash
        // appended.
        assertCanProcess("/source/xref/mercurial/",
                         "/source", "/xref", "/mercurial");

        // Expect no redirection (that is, empty string is returned) if the
        // directories already have a trailing slash.
        assertCanProcess("", "/source", "/xref", "/mercurial/");

        // Expect null if the file or directory doesn't exist.
        assertCanProcess(null, "/source", "/xref", "/mercurial/xyz");
        assertCanProcess(null, "/source", "/xref", "/mercurial/xyz/");
    }

    @Test
    public void testGetIntParam() {
        String[] attrs = {"a", "b", "c", "d", "e", "f", "g", "h"};
        int[] values = {1, 100, -1, 2, 200, 3000, -200, 3000};
        DummyHttpServletRequest req = new DummyHttpServletRequest() {
            @Override
            public String getParameter(String name) {
                switch(name) {
                    case "a": return "1";
                    case "b": return "100";
                    case "c": return null;
                    case "d": return "2";
                    case "e": return "200";
                    case "f": return "3000";
                    case "g": return null;
                    case "h": return "abcdef";
                }
                return null;
            }
        };
        PageConfig cfg = PageConfig.get(req);

        assertEquals(attrs.length, values.length);
        for (int i = 0; i < attrs.length; i++) {
            assertEquals(values[i], cfg.getIntParam(attrs[i], values[i]));
        }
    }

    @Test
    public void testGetRequestedRevision() {
        final String[] params = {"r", "h", "r", "r", "r"};
        final String[] revisions = {
            "6c5588de", "", "6c5588de", "6c5588de", "6c5588de"
        };
        assertEquals(params.length, revisions.length);
        for (int i = 0; i < revisions.length; i++) {
            final int index = i;
            DummyHttpServletRequest req = new DummyHttpServletRequest() {
                @Override
                public String getParameter(String name) {
                    if (name.equals("r")) {
                        return revisions[index];
                    }
                    return null;
                }
            };

            PageConfig cfg = PageConfig.get(req);
            String rev = cfg.getRequestedRevision();

            assertNotNull(rev);
            assertEquals(revisions[i], rev);
            assertFalse(rev.contains("r="));

            PageConfig.cleanup(req);
        }
    }

    @Test
    @ConditionalRun(condition = RepositoryInstalled.GitInstalled.class)
    public void testGetAnnotation() {
        final String[] revisions = {"aa35c258", "bb74b7e8"};

        for (int i = 0; i < revisions.length; i++) {
            final int index = i;
            HttpServletRequest req = new DummyHttpServletRequest() {
                @Override
                public String getContextPath() {
                    return "/source";
                }

                @Override
                public String getServletPath() {
                    return "/history";
                }

                @Override
                public String getPathInfo() {
                    return "/git/main.c";
                }

                @Override
                public String getParameter(String name) {
                    switch(name) {
                        case "r": return revisions[index];
                        case "a": return "true";
                    }
                    return null;
                }
            };
            PageConfig cfg = PageConfig.get(req);

            Annotation annotation = cfg.getAnnotation();
            assertNotNull(annotation);
            assertEquals("main.c", annotation.getFilename());
            assertEquals(revisions.length - i, annotation.getFileVersionsCount());

            for(int j = 1; j <= annotation.size(); j ++ ){
                String tmp = annotation.getRevision(j);
                assertTrue(Arrays.asList(revisions).contains(tmp));
            }

            assertEquals("The version should be reflected through the revision",
                    revisions.length - i,
                    annotation.getFileVersion(revisions[i]));

            PageConfig.cleanup(req);
        }
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
