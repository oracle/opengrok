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
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengrok.indexer.condition.RepositoryInstalled.Type.BAZAAR;

/**
 * Simple Bazaar repository test.
 *
 * @author austvik
 */
@EnabledForRepository(BAZAAR)
class BazaarRepositoryTest {

    BazaarRepository instance;
    private TestRepository repository;

    private File setUpTestRepository() throws IOException, URISyntaxException {
        repository = new TestRepository();
        repository.create(getClass().getResource("/repositories"));
        File repositoryRoot = new File(repository.getSourceRoot(), "bazaar");
        instance.setDirectoryName(repositoryRoot);
        return repositoryRoot;
    }

    @BeforeEach
    void setUp() {
        instance = new BazaarRepository();
    }

    @AfterEach
    void tearDown() {
        instance = null;

        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    /**
     * Test of annotate method, of class BazaarRepository.
     * @throws java.lang.Exception exception
     */
    @Test
    void annotate() throws Exception {
        File repositoryRoot = setUpTestRepository();
        String fileName = "header.h";
        Annotation result = instance.annotate(new File(repositoryRoot, fileName), null);

        assertNotNull(result);
        assertEquals(2, result.size());
        for (int i = 1; i <= 2; i++) {
            assertTrue(result.isEnabled(i));
            assertEquals("1", result.getRevision(i));
        }
        assertEquals(fileName, result.getFilename());
    }

    /**
     * Test of fileHasAnnotation method.
     */
    @Test
    void fileHasAnnotation() {
        boolean result = instance.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Test of fileHasHistory method.
     */
    @Test
    void fileHasHistory() {
        boolean result = instance.fileHasHistory(null);
        assertTrue(result);
    }

}
