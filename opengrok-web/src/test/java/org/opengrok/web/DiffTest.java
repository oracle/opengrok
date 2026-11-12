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
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryFactory;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.indexer.web.QueryParameters;
import org.opengrok.indexer.web.Util;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

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
        assertTrue(diffData.getErrorMsg().startsWith("Missing the"));
    }

    @Test
    void testGetDiffDataInvalidRevision() {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("git/main.c");
        when(req.getContextPath()).thenReturn("/source");
        when(req.getParameter(QueryParameters.REVISION_1_PARAM)).thenReturn("main.c@abc");
        when(req.getParameter(QueryParameters.REVISION_2_PARAM)).thenReturn("main.c@xyz");

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
        when(req.getParameter(QueryParameters.REVISION_1_PARAM)).
                thenReturn("/git/main.c@" + rev1);
        when(req.getParameter(QueryParameters.REVISION_2_PARAM)).
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
                () -> assertEquals("%2Fgit%2Fmain.c%40bb74b7e", diffData.getParam(0)),
                () -> assertEquals("%2Fgit%2Fmain.c%40aa35c25", diffData.getParam(1)),
                () -> assertEquals(DiffType.SIDEBYSIDE, diffData.getType()),
                () -> assertFalse(diffData.isFull())
        );
    }

    /**
     * Commit two versions of a special-character filename, load their diff, then
     * decode and follow the diff link built from its revision parameters.
     * The second request must resolve the same file and revisions without decoding
     * the servlet parameters again.
     */
    @EnabledOnOs({OS.LINUX, OS.MAC, OS.SOLARIS, OS.AIX, OS.OTHER})
    @Test
    void testDiffLinksPreserveSpecialCharacters() throws Exception {
        File gitDirectory = new File(repository.getSourceRoot(), "git");
        // '+' and percent-escape-looking text expose accidental second decoding of revision paths.
        String filename = "diff \"quoted\" space+&percent%20#@café%.txt";
        File file = new File(gitDirectory, filename);
        String[] revisions = new String[2];
        // Create two committed versions so the diff must retrieve this exact filename at both revisions.
        try (Git git = Git.open(gitDirectory)) {
            for (int i = 0; i < revisions.length; i++) {
                Files.writeString(file.toPath(), "revision " + i + "\n");
                git.add().addFilepattern(filename).call();
                revisions[i] = git.commit().setSign(false)
                        .setAuthor("Diff Test", "diff@example.com")
                        .setCommitter("Diff Test", "diff@example.com")
                        .setMessage("revision " + i).call().getName();
            }
        }

        String path = "/git/" + filename;
        String historyValue = Util.formQuoteEscape(path + "@" + revisions[0]);
        assertTrue(historyValue.contains("&quot;quoted&quot;"));
        assertTrue(historyValue.contains("&amp;percent"));
        // Servlet parameters have already been decoded once when PageConfig receives them.
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn(path);
        when(req.getContextPath()).thenReturn("/source");
        when(req.getParameter(QueryParameters.REVISION_1_PARAM)).thenReturn(path + "@" + revisions[0]);
        when(req.getParameter(QueryParameters.REVISION_2_PARAM)).thenReturn(path + "@" + revisions[1]);
        DiffData data = PageConfig.get(req).getDiffData();
        assertNull(data.getErrorMsg());
        assertEquals("revision 0", data.getFile(0)[0]);
        assertEquals("revision 1", data.getFile(1)[0]);

        // Follow the generated diff link through one query-decoding round trip.
        URL link = new URI("http://localhost/source/diff" + Util.uriEncodePath(path)
                + "?" + QueryParameters.REVISION_1_PARAM_EQ + data.getParam(0)
                + "&" + QueryParameters.REVISION_2_PARAM_EQ + data.getParam(1)).toURL();
        Map<String, List<String>> params = Util.getQueryParams(link);
        assertEquals(2, params.size());
        assertEquals(List.of(path + "@" + revisions[0]), params.get(QueryParameters.REVISION_1_PARAM));
        assertEquals(List.of(path + "@" + revisions[1]), params.get(QueryParameters.REVISION_2_PARAM));

        // The next request must still resolve the same revisions and preserve the link parameters.
        when(req.getParameter(QueryParameters.REVISION_1_PARAM)).thenReturn(
                params.get(QueryParameters.REVISION_1_PARAM).getFirst());
        when(req.getParameter(QueryParameters.REVISION_2_PARAM)).thenReturn(
                params.get(QueryParameters.REVISION_2_PARAM).getFirst());
        DiffData linkedData = PageConfig.get(req).getDiffData();
        assertNull(linkedData.getErrorMsg());
        assertEquals(data.getParam(0), linkedData.getParam(0));
        assertEquals(data.getParam(1), linkedData.getParam(1));
    }

}
