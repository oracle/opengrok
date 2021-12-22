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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.RCS;

/**
 * @author Vladimir Kotal
 */
@EnabledForRepository(RCS)
public class RCSRepositoryTest {

    private static TestRepository repository = new TestRepository();

    /**
     * Revision numbers present in the RCS test repository, in the order
     * they are supposed to be returned from getHistory(), that is latest
     * changeset first.
     */
    private static final String[] REVISIONS = {"1.2", "1.1"};

    @BeforeAll
    public static void setUpClass() throws IOException, URISyntaxException {
        repository.create(RCSRepositoryTest.class.getResource("/repositories"));
    }

    @AfterAll
    public static void tearDownClass() {
        repository.destroy();
        repository = null;
    }

    @Test
    public void testRepositoryDetection() throws Exception {
        File root = new File(repository.getSourceRoot(), "rcs_test");
        Object ret = RepositoryFactory.getRepository(root);
        assertTrue(ret instanceof RCSRepository);
    }

    @Test
    public void testAnnotation() throws Exception {
        File root = new File(repository.getSourceRoot(), "rcs_test");
        RCSRepository repo = (RCSRepository) RepositoryFactory.getRepository(root);
        File header = new File(root, "header.h");
        Annotation annotation = repo.annotate(header, null);
        if (annotation != null) {
            Annotation expAnnotation = new Annotation(header.getName());
            assertEquals(2, annotation.size());
            expAnnotation.addLine("1.1", "kah", true);
            expAnnotation.addLine("1.1", "kah", true);
            assertEquals(expAnnotation.toString(), annotation.toString());
        }
    }

    @Test
    public void testGetHistory() throws Exception {
        File root = new File(repository.getSourceRoot(), "rcs_test");
        RCSRepository repo = (RCSRepository) RepositoryFactory.getRepository(root);
        History hist = repo.getHistory(new File(root, "Makefile"));
        List<HistoryEntry> entries = hist.getHistoryEntries();
        assertEquals(REVISIONS.length, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            assertEquals(REVISIONS[i], e.getRevision());
            assertNotNull(e.getAuthor());
            assertNotNull(e.getDate());
            assertNotNull(e.getFiles());
            assertNotNull(e.getMessage());
        }
    }
}
