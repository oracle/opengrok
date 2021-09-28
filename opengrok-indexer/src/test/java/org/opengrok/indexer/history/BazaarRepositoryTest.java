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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.condition.EnabledForRepository;

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
public class BazaarRepositoryTest {

    BazaarRepository instance;

    @BeforeEach
    public void setUp() {
        instance = new BazaarRepository();
    }

    @AfterEach
    public void tearDown() {
        instance = null;
    }

    /**
     * Test of parseAnnotation method, of class GitRepository.
     * @throws java.lang.Exception exception
     */
    @Test
    public void parseAnnotation() throws Exception {
        String revId1 = "1234.876.5";
        String revId2 = "1.234";
        String revId3 = "2";
        String author1 = "username@example.com";
        String author2 = "username2@example.com";
        String author3 = "username3@example.com";
        String output = revId1 + "  " + author1 + " 20050912 | some source code here\n" +
                revId2 + "  " + author2 + " 20050912 | and here.\n" +
                revId3 + "           " + author3 + "          20030731 | \n";

        String fileName = "something.ext";

        BazaarAnnotationParser parser = new BazaarAnnotationParser(fileName);
        parser.processStream(new ByteArrayInputStream(output.getBytes()));
        Annotation result = parser.getAnnotation();

        assertNotNull(result);
        assertEquals(3, result.size());
        for (int i = 1; i <= 3; i++) {
            assertTrue(result.isEnabled(i));
        }
        assertEquals(revId1, result.getRevision(1));
        assertEquals(revId2, result.getRevision(2));
        assertEquals(revId3, result.getRevision(3));
        assertEquals(author1, result.getAuthor(1));
        assertEquals(author2, result.getAuthor(2));
        assertEquals(author3, result.getAuthor(3));
        assertEquals(author2.length(), result.getWidestAuthor());
        assertEquals(revId1.length(), result.getWidestRevision());
        assertEquals(fileName, result.getFilename());
    }

    /**
     * Test of fileHasAnnotation method.
     */
    @Test
    public void fileHasAnnotation() {
        boolean result = instance.fileHasAnnotation(null);
        assertTrue(result);
    }

    /**
     * Test of fileHasHistory method.
     */
    @Test
    public void fileHasHistory() {
        boolean result = instance.fileHasHistory(null);
        assertTrue(result);
    }

}
