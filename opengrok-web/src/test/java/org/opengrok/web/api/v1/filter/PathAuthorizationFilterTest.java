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
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.opengrok.indexer.authorization.AuthorizationFramework;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PathAuthorizationFilterTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private String originalSourceRoot;
    private boolean originalProjectsEnabled;
    private Map<String, Project> originalProjects;
    private AuthorizationFramework originalAuthorizationFramework;

    private AuthorizationFramework authorizationFramework;
    private Project allowedProject;
    private Project deniedProject;

    @TempDir
    Path sourceRoot;

    @BeforeEach
    void setUp() throws Exception {
        originalSourceRoot = env.getSourceRootPath();
        originalProjectsEnabled = env.isProjectsEnabled();
        originalProjects = env.getProjects();
        originalAuthorizationFramework = env.getAuthorizationFramework();

        Files.createDirectories(sourceRoot.resolve("allowed"));
        Files.createDirectories(sourceRoot.resolve("denied"));
        Files.writeString(sourceRoot.resolve("allowed/file"), "allowed");
        Files.writeString(sourceRoot.resolve("denied/file"), "denied");
        Files.writeString(sourceRoot.resolve("unassigned"), "unassigned");

        allowedProject = new Project("allowed", "/allowed");
        deniedProject = new Project("denied", "/denied");
        Map<String, Project> projects = new LinkedHashMap<>();
        projects.put(allowedProject.getName(), allowedProject);
        projects.put(deniedProject.getName(), deniedProject);

        env.setSourceRoot(sourceRoot.toString());
        env.setProjectsEnabled(true);
        env.setProjects(projects);
        authorizationFramework = mock(AuthorizationFramework.class);
        env.setAuthorizationFramework(authorizationFramework);
    }

    @AfterEach
    void tearDown() {
        env.setAuthorizationFramework(originalAuthorizationFramework);
        env.setProjects(originalProjects);
        env.setProjectsEnabled(originalProjectsEnabled);
        env.setSourceRoot(originalSourceRoot);
    }

    @Test
    void nullInjectedRequestIsIgnored() throws Exception {
        ContainerRequestContext context = mock(ContainerRequestContext.class);

        new PathAuthorizationFilter().filter(context);

        verifyNoInteractions(context);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "..")
    void invalidPathIsNotAcceptable(String path) throws Exception {
        ContainerRequestContext context = doFilter(path, mock(HttpServletRequest.class));

        assertAbortedWith(context, Response.Status.NOT_ACCEPTABLE);
        verifyNoInteractions(authorizationFramework);
    }

    @Test
    void missingFileIsReportedByFileResolution() throws Exception {
        PathAuthorizationFilter filter = getAuthorizationFilterWithRequest("missing",
                mock(HttpServletRequest.class));
        ContainerRequestContext context = mock(ContainerRequestContext.class);

        assertThrows(FileNotFoundException.class, () -> filter.filter(context));
        verifyNoInteractions(context, authorizationFramework);
    }

    @Test
    void fileOutsideAProjectIsAllowed() throws Exception {
        ContainerRequestContext context = doFilter("unassigned", mock(HttpServletRequest.class));

        verify(context, never()).abortWith(any(Response.class));
        verifyNoInteractions(authorizationFramework);
    }

    @Test
    void authorizedProjectIsAllowed() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authorizationFramework.isAllowed(request, allowedProject)).thenReturn(true);

        ContainerRequestContext context = doFilter("allowed/file", request);

        verify(context, never()).abortWith(any(Response.class));
        verify(authorizationFramework).isAllowed(request, allowedProject);
    }

    @Test
    void unauthorizedProjectIsForbidden() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authorizationFramework.isAllowed(request, deniedProject)).thenReturn(false);

        ContainerRequestContext context = doFilter("denied/file", request);

        assertAbortedWith(context, Response.Status.FORBIDDEN);
        verify(authorizationFramework).isAllowed(request, deniedProject);
    }

    @Test
    void pathIsCanonicalizedBeforeProjectAuthorization() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authorizationFramework.isAllowed(request, deniedProject)).thenReturn(false);

        ContainerRequestContext context = doFilter("allowed/../denied/file", request);

        assertAbortedWith(context, Response.Status.FORBIDDEN);
        verify(authorizationFramework).isAllowed(request, deniedProject);
        verify(authorizationFramework, never()).isAllowed(request, allowedProject);
    }

    private ContainerRequestContext doFilter(String path, HttpServletRequest request) throws Exception {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        getAuthorizationFilterWithRequest(path, request).filter(context);
        return context;
    }

    private PathAuthorizationFilter getAuthorizationFilterWithRequest(String path,
                                                                      HttpServletRequest request) throws Exception {
        when(request.getParameter(PathAuthorizationFilter.PATH_PARAM)).thenReturn(path);
        PathAuthorizationFilter filter = new PathAuthorizationFilter();
        Field requestField = PathAuthorizationFilter.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(filter, request);
        return filter;
    }

    private static void assertAbortedWith(ContainerRequestContext context, Response.Status expectedStatus) {
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(context).abortWith(responseCaptor.capture());
        assertEquals(expectedStatus.getStatusCode(), responseCaptor.getValue().getStatus());
    }
}
