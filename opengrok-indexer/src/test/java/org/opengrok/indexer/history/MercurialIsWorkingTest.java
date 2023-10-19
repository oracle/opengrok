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
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.util.ForbiddenSymlinkException;
import org.opengrok.indexer.util.TestRepository;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import static org.opengrok.indexer.history.RepositoryFactoryTest.testNotWorkingRepository;

/**
 * This test needs to be in separate class since it tests static initializer
 * of {@code MercurialRepository#HG_IS_WORKING}. Assuming the test will be run
 * in separate JVM by JUnit.
 */
class MercurialIsWorkingTest {
    private static TestRepository repository = new TestRepository();

    @BeforeAll
    static void setUpClass() throws Exception {
        repository.create(RepositoryFactoryTest.class.getResource("/repositories"));
    }

    @AfterAll
    static void tearDownClass() {
        if (repository != null) {
            repository.destroy();
            repository = null;
        }
    }

    @Test
    void testNotWorkingMercurialRepository()
            throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException,
            IOException, ForbiddenSymlinkException {
        testNotWorkingRepository(repository, "mercurial", MercurialRepository.CMD_PROPERTY_KEY);
    }
}
