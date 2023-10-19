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
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 *
 * @author Vladimir Kotal
 */
class RepositoryInfoTest {
    @BeforeEach
    void setUp() {
        RuntimeEnvironment.getInstance().setSourceRoot("/src");
    }

    @Test
    void testEquals() {
        String repoDirectory = "/src/foo";

        RepositoryInfo ri1 = new RepositoryInfo();
        ri1.setDirectoryName(new File(repoDirectory));
        ri1.setBranch("branch1");

        RepositoryInfo ri2 = new RepositoryInfo();
        assertNotEquals(ri1, ri2);

        ri2.setDirectoryName(new File(repoDirectory));
        assertEquals(ri1, ri2);
    }

    @Test
    void testUsername() {
        RepositoryInfo repositoryInfo = new RepositoryInfo();
        final String username = "foo";
        repositoryInfo.setUsername(username);
        assertEquals(username, repositoryInfo.getUsername());
    }

    @Test
    void testPassword() {
        RepositoryInfo repositoryInfo = new RepositoryInfo();
        final String password = "foo";
        repositoryInfo.setPassword(password);
        assertEquals(password, repositoryInfo.getPassword());
    }

    /**
     * This test assumes that {@link RepositoryFactory#getRepository(File, CommandTimeoutType, boolean)}
     * uses {@link RepositoryInfo#fillFromProject()} to complete {@link RepositoryInfo} field values.
     */
    @Test
    void testFillFromProjectUsernamePassword() {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setProjectsEnabled(true);

        final String projectName = "projectWithUsernameAndPassword";
        Project project = new Project(projectName);
        final String dirName = "/proj";
        project.setPath(dirName);
        final String username = "foo";
        final String password = "bar";
        project.setUsername(username);
        project.setPassword(password);
        env.setProjects(Map.of(projectName, project));

        RepositoryInfo repositoryInfo = new RepositoryInfo();
        repositoryInfo.setDirectoryNameRelative(dirName);
        repositoryInfo.fillFromProject();
        assertEquals(username, repositoryInfo.getUsername());
        assertEquals(password, repositoryInfo.getPassword());
    }

    private static Stream<Arguments> provideArgumentsForTestHistoryAndAnnotationFields() {
        return Stream.of(
                Arguments.of(true, true, true),
                Arguments.of(true, true, false),
                Arguments.of(true, false, true),
                Arguments.of(false, true, true),
                Arguments.of(true, false, false),
                Arguments.of(false, false, true),
                Arguments.of(false, true, false),
                Arguments.of(false, false, false)
        );
    }

    /**
     * Test history/annotation field "inheritance" from project or global configuration.
     * <p>
     * Same comment applies as for {@link #testFillFromProjectUsernamePassword()}.
     * </p>
     */
    @ParameterizedTest
    @MethodSource("provideArgumentsForTestHistoryAndAnnotationFields")
    void testHistoryAndAnnotationFields(boolean isProjectPresent, boolean historyEnabled, boolean useAnnotationCache) {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setProjectsEnabled(true);

        final String dirName = "/proj";
        if (isProjectPresent) {
            final String projectName = "projectWithHistoryAndAnnotationSettings";
            Project project = new Project(projectName);
            project.setPath(dirName);
            project.setAnnotationCacheEnabled(useAnnotationCache);
            project.setHistoryEnabled(historyEnabled);
            env.setProjects(Map.of(projectName, project));
        } else {
            env.setProjects(Collections.emptyMap());
            env.setHistoryEnabled(historyEnabled);
            env.setAnnotationCacheEnabled(useAnnotationCache);
        }

        RepositoryInfo repositoryInfo = new RepositoryInfo();
        repositoryInfo.setDirectoryNameRelative(dirName);
        repositoryInfo.fillFromProject();
        assertEquals(historyEnabled, repositoryInfo.isHistoryEnabled());
        assertEquals(useAnnotationCache, repositoryInfo.isAnnotationCacheEnabled());
    }
}
