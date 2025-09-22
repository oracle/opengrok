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
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.authorization.AuthControlFlag;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.authorization.AuthorizationPlugin;
import org.opengrok.indexer.authorization.TestPlugin;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.configuration.IndexTimestamp;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.Annotation;
import org.opengrok.indexer.history.LatestRevisionUtil;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.MERCURIAL;
import static org.opengrok.indexer.history.LatestRevisionUtil.getLatestRevision;

/**
 * Unit tests for the {@code PageConfig} class.
 */
class PageConfigTest {
    private static final String HASH_BB74B7E8 = "bb74b7e849170c31dc1b1b5801c83bf0094a3b10";
    private static final String HASH_AA35C258 = "aa35c25882b9a60a97758e0ceb276a3f8cb4ae3a";
    private static TestRepository repository = new TestRepository();

    @BeforeAll
    static void setUpClass() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setHistoryEnabled(true);

        repository = new TestRepository();
        URL repositoryURL = PageConfigTest.class.getResource("/repositories");
        assertNotNull(repositoryURL);
        repository.create(repositoryURL);
        env.setRepositories(repository.getSourceRoot());
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
        repository = null;
    }

    @Test
    void testRequestAttributes() {
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

    @Test
    @EnabledForRepository(MERCURIAL)
    void canProcessHistory() {
        // Expect no redirection (that is, empty string is returned) for a
        // file that exists.
        assertCanProcess("", "/source", "/history", "/mercurial/main.c");

        // Expect directories without trailing slash to get a trailing slash
        // appended.
        assertCanProcess("/source/history/mercurial/", "/source", "/history", "/mercurial");

        // Expect no redirection (that is, empty string is returned) if the
        // directories already have a trailing slash.
        assertCanProcess("", "/source", "/history", "/mercurial/");

        // Expect null if the file or directory doesn't exist.
        assertCanProcess(null, "/source", "/history", "/mercurial/xyz");
        assertCanProcess(null, "/source", "/history", "/mercurial/xyz/");
    }

    @Test
    void canProcessXref() {
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

    /**
     * Testing the root of /xref for authorization filtering.
     */
    @Test
    void testGetResourceFileList() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // backup original values
        String oldSourceRootPath = env.getSourceRootPath();
        AuthorizationFramework oldAuthorizationFramework = env.getAuthorizationFramework();
        Map<String, Project> oldProjects = env.getProjects();

        // Set up the source root directory containing some projects.
        env.setSourceRoot(repository.getSourceRoot());
        env.setProjectsEnabled(true);

        // Enable projects.
        for (String file : Objects.requireNonNull(new File(repository.getSourceRoot()).list())) {
            Project proj = new Project(file);
            proj.setIndexed(true);
            env.getProjects().put(file, proj);
        }

        HttpServletRequest req = createRequest("/source", "/xref", "");
        PageConfig cfg = PageConfig.get(req);
        List<String> allFiles = new ArrayList<>(cfg.getResourceFileList());

        /*
         * Check if there are some files (the "5" here is just a sufficient
         * value for now which won't break any future repository tests) without
         * any authorization.
         */
        assertTrue(allFiles.size() > 5);
        assertTrue(allFiles.contains("git"));
        assertTrue(allFiles.contains("mercurial"));

        /*
         * Now set up the same projects with authorization plugin enabling only
         * some of them.
         * <pre>
         *  - disabling "git"
         *  - disabling "mercurial"
         * </pre>
         */
        env.setAuthorizationFramework(new AuthorizationFramework());
        env.getAuthorizationFramework().reload();
        env.getAuthorizationFramework().getStack()
                .add(new AuthorizationPlugin(AuthControlFlag.REQUIRED, new TestPlugin() {
                    @Override
                    public boolean isAllowed(HttpServletRequest request, Project project) {
                        return !project.getName().startsWith("git")
                                && !project.getName().startsWith("mercurial");
                    }
                }));

        req = createRequest("/source", "/xref", "");
        cfg = PageConfig.get(req);
        List<String> filteredFiles = new ArrayList<>(cfg.getResourceFileList());
        // list subtraction - retains only disabled files
        allFiles.removeAll(filteredFiles);

        assertEquals(2, allFiles.size());
        assertTrue(allFiles.contains("git"));
        assertTrue(allFiles.contains("mercurial"));

        // restore original values
        env.setAuthorizationFramework(oldAuthorizationFramework);
        env.setSourceRoot(oldSourceRootPath);
        env.setProjects(oldProjects);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @Test
    void testGetSortedFilesDirsFirst() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setListDirsFirst(true);

        // Make sure the source root has just directories.
        File sourceRootFile = new File(repository.getSourceRoot());
        assertTrue(Arrays.stream(Objects.requireNonNull(sourceRootFile.listFiles())).
                filter(File::isFile).
                collect(Collectors.toSet()).isEmpty());

        // Create regular file under source root.
        File file = new File(sourceRootFile, "foo.txt");
        assertTrue(file.createNewFile());
        assertTrue(file.isFile());

        // Make sure the regular file is last.
        List<String> entries = PageConfig.getSortedFiles(sourceRootFile.listFiles());
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        int numEntries = entries.size();
        assertEquals("foo.txt", entries.get(entries.size() - 1));

        // Create symbolic link to non-existent target.
        Path link = Path.of(sourceRootFile.getCanonicalPath(), "link");
        Path target = Paths.get("/nonexistent");
        Files.createSymbolicLink(link, target);

        // Check the symlink was sorted as file.
        entries = PageConfig.getSortedFiles(sourceRootFile.listFiles());
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        assertEquals(numEntries + 1, entries.size());
        assertEquals("link", entries.get(entries.size() - 1));

        // Cleanup.
        file.delete();
        link.toFile().delete();
    }

    @Test
    void testGetIntParam() {
        String[] attrs = {"a", "b", "c", "d", "e", "f", "g", "h"};
        int[] values = {1, 100, -1, 2, 200, 3000, -200, 3000};
        DummyHttpServletRequest req = new DummyHttpServletRequest() {
            @Override
            public String getParameter(String name) {
                switch (name) {
                    case "a":
                        return "1";
                    case "b":
                        return "100";
                    case "c":
                        return null;
                    case "d":
                        return "2";
                    case "e":
                        return "200";
                    case "f":
                        return "3000";
                    case "g":
                        return null;
                    case "h":
                        return "abcdef";
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
    void testGetLatestRevisionValid() {
        DummyHttpServletRequest req1 = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return "/git/main.c";
            }
        };

        PageConfig cfg = PageConfig.get(req1);
        String rev = getLatestRevision(cfg.getResourceFile());

        assertEquals(HASH_AA35C258, rev);
    }

    @Test
    void testGetLatestRevisionViaIndex() throws Exception {
        // Run the indexer.
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setSourceRoot(repository.getSourceRoot());
        env.setDataRoot(repository.getDataRoot());
        env.setProjectsEnabled(true);
        env.setHistoryEnabled(true);
        RepositoryFactory.initializeIgnoredNames(env);

        Indexer indexer = Indexer.getInstance();
        indexer.prepareIndexer(
                env,
                true, // search for repositories
                true, // scan and add projects
                // don't create dictionary
                null, // subFiles - needed when refreshing history partially
                null); // repositories - needed when refreshing history partially
        indexer.doIndexerExecution(null, null);

        final String filePath = "/git/main.c";
        String rev = LatestRevisionUtil.getLastRevFromIndex(new File(repository.getSourceRoot(), filePath));
        assertNotNull(rev);
        assertEquals(HASH_AA35C258, rev);
    }

    @Test
    void testGetRevisionLocation() {
        DummyHttpServletRequest req1 = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return "/git/main.c";
            }

            @Override
            public String getContextPath() {
                return "source";
            }

            @Override
            public String getQueryString() {
                return "a=true";
            }
        };

        PageConfig cfg = PageConfig.get(req1);

        String location = cfg.getRevisionLocation(getLatestRevision(cfg.getResourceFile()));
        assertNotNull(location);
        assertEquals("source/xref/git/main.c?r=" + HASH_AA35C258 + "&a=true", location);
    }

    @Test
    void testGetRevisionLocationNullQuery() {
        DummyHttpServletRequest req1 = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return "/git/main.c";
            }

            @Override
            public String getContextPath() {
                return "source";
            }

            @Override
            public String getQueryString() {
                return null;
            }
        };

        PageConfig cfg = PageConfig.get(req1);

        String location = cfg.getRevisionLocation(getLatestRevision(cfg.getResourceFile()));
        assertNotNull(location);
        assertEquals("source/xref/git/main.c?r=" + HASH_AA35C258, location);
    }

    @Test
    void testGetLatestRevisionNotValid() {
        DummyHttpServletRequest req2 = new DummyHttpServletRequest() {
            @Override
            public String getPathInfo() {
                return "/git/nonexistent_file";
            }
        };

        PageConfig cfg = PageConfig.get(req2);
        String rev = getLatestRevision(cfg.getResourceFile());
        assertNull(rev);
    }

    private static Stream<Pair<String, String>> getParamsForTestGetRequestedRevision() {
        return Stream.of(Pair.of("6c5588de", "6c5588de"),
                Pair.of("10013:cb02e4e3d492", "10013:cb02e4e3d492"),
                Pair.of("", ""),
                Pair.of(null, ""),
                Pair.of("(foo)\n", "foo"));
    }

    @MethodSource("getParamsForTestGetRequestedRevision")
    @ParameterizedTest
    void testGetRequestedRevision(Pair<String, String> revisionParam) {
        final String actualRevision = revisionParam.getLeft();
        final String expectedRevision = revisionParam.getRight();
        DummyHttpServletRequest req = new DummyHttpServletRequest() {
            @Override
            public String getParameter(String name) {
                if (name.equals("r")) {
                    return actualRevision;
                }
                return null;
            }
        };

        PageConfig cfg = PageConfig.get(req);
        String rev = cfg.getRequestedRevision();

        assertNotNull(rev);
        assertEquals(expectedRevision, rev);
        assertFalse(rev.contains("r="));

        PageConfig.cleanup(req);
    }

    @Test
    void testGetAnnotation() {
        final String[] revisions = {HASH_AA35C258, HASH_BB74B7E8};

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
                    switch (name) {
                        case "r":
                            return revisions[index];
                        case "a":
                            return "true";
                    }
                    return null;
                }
            };
            PageConfig cfg = PageConfig.get(req);

            Annotation annotation = cfg.getAnnotation();
            assertNotNull(annotation);
            assertEquals("main.c", annotation.getFilename());
            assertEquals(revisions.length - i, annotation.getFileVersionsCount());

            for (int j = 1; j <= annotation.size(); j++) {
                String tmp = annotation.getRevision(j);
                assertTrue(Arrays.asList(revisions).contains(tmp));
            }

            assertEquals(revisions.length - i, annotation.getFileVersion(revisions[i]),
                    "The version should be reflected through the revision");

            PageConfig.cleanup(req);
        }
    }

    /**
     * Test the case when the source root is null.
     */
    @Test
    void testCheckSourceRootExistence1() {
        assertThrows(FileNotFoundException.class, () -> {
            HttpServletRequest req = new DummyHttpServletRequest();
            PageConfig cfg = PageConfig.get(req);
            String path = RuntimeEnvironment.getInstance().getSourceRootPath();
            System.out.println(path);
            RuntimeEnvironment.getInstance().setSourceRoot(null);
            try {
                cfg.checkSourceRootExistence();
            } finally {
                RuntimeEnvironment.getInstance().setSourceRoot(path);
                PageConfig.cleanup(req);
            }
        });
    }

    /**
     * Test the case when source root is empty.
     */
    @Test
    void testCheckSourceRootExistence2() {
        assertThrows(FileNotFoundException.class, () -> {
            HttpServletRequest req = new DummyHttpServletRequest();
            PageConfig cfg = PageConfig.get(req);
            String path = RuntimeEnvironment.getInstance().getSourceRootPath();
            RuntimeEnvironment.getInstance().setSourceRoot("");
            try {
                cfg.checkSourceRootExistence();
            } finally {
                RuntimeEnvironment.getInstance().setSourceRoot(path);
                PageConfig.cleanup(req);
            }
        });
    }

    /**
     * Test the case when source root does not exist.
     * @throws IOException I/O exception
     */
    @Test
    void testCheckSourceRootExistence3() throws IOException {
        HttpServletRequest req = new DummyHttpServletRequest();
        PageConfig cfg = PageConfig.get(req);
        String path = RuntimeEnvironment.getInstance().getSourceRootPath();
        File temp = File.createTempFile("opengrok", "-test-file.tmp");
        Files.delete(temp.toPath());
        RuntimeEnvironment.getInstance().setSourceRoot(temp.getAbsolutePath());
        assertThrows(IOException.class, cfg::checkSourceRootExistence,
                "This should throw an exception when the file does not exist");
        RuntimeEnvironment.getInstance().setSourceRoot(path);
        PageConfig.cleanup(req);
    }

    /**
     * Test the case when source root can not be read.
     * @throws IOException I/O exception
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    void testCheckSourceRootExistence4() throws IOException {
        HttpServletRequest req = new DummyHttpServletRequest();
        PageConfig cfg = PageConfig.get(req);
        String path = RuntimeEnvironment.getInstance().getSourceRootPath();
        File temp = File.createTempFile("opengrok", "-test-file.tmp");
        Files.delete(temp.toPath());
        Files.createDirectories(temp.toPath());
        // skip the test if the implementation does not permit setting permissions
        assumeTrue(temp.setReadable(false));
        RuntimeEnvironment.getInstance().setSourceRoot(temp.getAbsolutePath());
        assertThrows(IOException.class, cfg::checkSourceRootExistence,
                "This should throw an exception when the file is not readable");
        RuntimeEnvironment.getInstance().setSourceRoot(path);

        PageConfig.cleanup(req);
        temp.deleteOnExit();
    }

    /**
     * Test a successful check.
     * @throws IOException I/O exception
     */
    @Test
    void testCheckSourceRootExistence5() throws IOException {
        HttpServletRequest req = new DummyHttpServletRequest();
        PageConfig cfg = PageConfig.get(req);
        String path = RuntimeEnvironment.getInstance().getSourceRootPath();
        File temp = File.createTempFile("opengrok", "-test-file.tmp");
        assertTrue(temp.delete());
        assertTrue(temp.mkdirs());
        RuntimeEnvironment.getInstance().setSourceRoot(temp.getAbsolutePath());
        cfg.checkSourceRootExistence();
        RuntimeEnvironment.getInstance().setSourceRoot(path);
        temp.deleteOnExit();
        PageConfig.cleanup(req);
    }

    /**
     * Assert that {@code canProcess()} returns the expected value for the
     * specified path.
     * @param expected the expected return value
     * @param context the context path
     * @param servlet the servlet path
     * @param pathInfo the path info
     */
    private void assertCanProcess(String expected, String context, String servlet, String pathInfo) {
        PageConfig config = PageConfig.get(createRequest(context, servlet, pathInfo));
        assertEquals(expected, config.canProcess());
    }

    /**
     * Create a request with the specified path elements.
     * @param contextPath the context path
     * @param servletPath the path of the servlet
     * @param pathInfo the path info
     * @return a servlet request for the specified path
     */
    private static HttpServletRequest createRequest(
            final String contextPath, final String servletPath,
            final String pathInfo) {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIsNotModifiedEtag(boolean createTimestamp) throws IOException {
        HttpServletRequest req = new DummyHttpServletRequest() {
            @Override
            public String getHeader(String name) {
                if (name.equals(HttpHeaders.IF_NONE_MATCH)) {
                    return "foo"; // will not match the hash computed in
                }
                return null;
            }

            @Override
            public String getPathInfo() {
                return "path";
            }
        };

        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.refreshDateForLastIndexRun();
        Path timestampPath = Path.of(env.getDataRootPath(), IndexTimestamp.TIMESTAMP_FILE_NAME);
        if (createTimestamp) {
            Files.createFile(timestampPath);
            assertTrue(timestampPath.toFile().exists());
        } else {
            Files.deleteIfExists(timestampPath);
        }

        PageConfig cfg = PageConfig.get(req);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        assertFalse(cfg.isNotModified(req, resp));
        verify(resp).setHeader(eq(HttpHeaders.ETAG), startsWith("W/"));
    }

    @Test
    void testIsNotModifiedNotModified() {
        DummyHttpServletRequest req = mock(DummyHttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/");
        PageConfig cfg = PageConfig.get(req);
        final String etag = cfg.getEtag();
        when(req.getHeader(HttpHeaders.IF_NONE_MATCH)).thenReturn(etag);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        assertTrue(cfg.isNotModified(req, resp));
        verify(resp).setStatus(eq(HttpServletResponse.SC_NOT_MODIFIED));
    }
}
