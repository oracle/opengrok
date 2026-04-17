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
package org.opengrok.indexer.web;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiUtilsTest {

    @Test
    void waitForAsyncApiReturnsImmediatelyWhenNotAccepted() throws Exception {
        Response initial = mock(Response.class);
        when(initial.getStatus()).thenReturn(Response.Status.OK.getStatusCode());

        Response result = ApiUtils.waitForAsyncApi(initial);
        assertSame(initial, result);
    }

    @Test
    void waitForAsyncApiThrowsWhenNoLocationHeader() {
        Response initial = mock(Response.class);
        when(initial.getStatus()).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        when(initial.getHeaderString(HttpHeaders.LOCATION)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> ApiUtils.waitForAsyncApi(initial));
    }

    @Test
    void waitForAsyncApiCompletesImmediately() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setApiTimeout(3);
        env.setConnectTimeout(1);

        String location = "http://example.com/api/status/123";

        Response initial = mock(Response.class);
        when(initial.getStatus()).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        when(initial.getHeaderString(HttpHeaders.LOCATION)).thenReturn(location);

        Response statusResponse = mock(Response.class);
        when(statusResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(statusResponse.getStatusInfo()).thenReturn(Response.Status.OK);

        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatusInfo()).thenReturn(Response.Status.OK);

        ClientBuilder firstBuilder = mock(ClientBuilder.class);
        Client firstClient = mock(Client.class);
        WebTarget firstTarget = mock(WebTarget.class);
        Invocation.Builder getInvocationBuilder = mock(Invocation.Builder.class);

        ClientBuilder secondBuilder = mock(ClientBuilder.class);
        Client secondClient = mock(Client.class);
        WebTarget secondTarget = mock(WebTarget.class);
        Invocation.Builder deleteInvocationBuilder = mock(Invocation.Builder.class);

        when(firstBuilder.connectTimeout(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(firstBuilder);
        when(firstBuilder.build()).thenReturn(firstClient);
        when(firstClient.target(location)).thenReturn(firstTarget);
        when(firstTarget.request()).thenReturn(getInvocationBuilder);
        when(getInvocationBuilder.get()).thenReturn(statusResponse);

        when(secondBuilder.connectTimeout(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(secondBuilder);
        when(secondBuilder.build()).thenReturn(secondClient);
        when(secondClient.target(location)).thenReturn(secondTarget);
        when(secondTarget.request()).thenReturn(deleteInvocationBuilder);
        when(deleteInvocationBuilder.delete()).thenReturn(deleteResponse);

        try (MockedStatic<ClientBuilder> clientBuilderStatic = org.mockito.Mockito.mockStatic(ClientBuilder.class)) {
            // First newBuilder() call is for GET polling, second one is for DELETE cleanup.
            clientBuilderStatic.when(ClientBuilder::newBuilder).thenReturn(firstBuilder, secondBuilder);

            Response result = ApiUtils.waitForAsyncApi(initial);

            assertSame(statusResponse, result);
            verify(getInvocationBuilder).get();
            verify(deleteInvocationBuilder).delete();
        }
    }

    @Test
    void waitForAsyncApiTimesOutWhileStillAccepted() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();

        // Random timeout between 1 and 8 seconds
        int apiTimeout = 1 + (int) (Math.random() * 8);
        env.setApiTimeout(apiTimeout);
        env.setConnectTimeout(1);

        String location = "http://example.com/api/status/timeout";

        Response initial = mock(Response.class);
        when(initial.getStatus()).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        when(initial.getHeaderString(HttpHeaders.LOCATION)).thenReturn(location);

        Response stillAccepted = mock(Response.class);
        when(stillAccepted.getStatus()).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        when(stillAccepted.getStatusInfo()).thenReturn(Response.Status.ACCEPTED);

        ClientBuilder builder = mock(ClientBuilder.class);
        Client client = mock(Client.class);
        WebTarget target = mock(WebTarget.class);
        Invocation.Builder getInvocationBuilder = mock(Invocation.Builder.class);

        // Delete-related mocks – we expect them not to be used
        Invocation.Builder deleteInvocationBuilder = mock(Invocation.Builder.class);

        when(builder.connectTimeout(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.target(location)).thenReturn(target);
        when(target.request()).thenReturn(getInvocationBuilder);
        when(getInvocationBuilder.get()).thenReturn(stillAccepted);

        try (MockedStatic<ClientBuilder> clientBuilderStatic =
                     org.mockito.Mockito.mockStatic(ClientBuilder.class)) {
            clientBuilderStatic.when(ClientBuilder::newBuilder).thenReturn(builder);

            long startNanos = System.nanoTime();
            Response result = ApiUtils.waitForAsyncApi(initial);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertSame(stillAccepted, result);
            // GET must be called apiTimeout times
            verify(getInvocationBuilder, Mockito.times(apiTimeout)).get();
            // DELETE must never be called when we only see ACCEPTED responses
            verify(deleteInvocationBuilder, never()).delete();

            long expectedMillis = apiTimeout * 1000L;
            long lowerBound = Math.max(0, expectedMillis - 300L);
            long upperBound = expectedMillis + 2000L;

            assertAll(
                    () -> Assertions.assertTrue(
                            elapsedMillis >= lowerBound,
                            "elapsed=" + elapsedMillis + "ms, expected at least " + lowerBound +
                                    "ms for apiTimeout=" + apiTimeout
                    ),
                    () -> Assertions.assertTrue(
                            elapsedMillis <= upperBound,
                            "elapsed=" + elapsedMillis + "ms, expected at most " + upperBound +
                                    "ms for apiTimeout=" + apiTimeout
                    )
            );
        }
    }

    @Test
    void waitForAsyncApiReturnsCompletedResponseWhenDeleteFails() throws Exception {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setApiTimeout(3);
        env.setConnectTimeout(1);

        String location = "http://example.com/api/status/456";

        Response initial = mock(Response.class);
        when(initial.getStatus()).thenReturn(Response.Status.ACCEPTED.getStatusCode());
        when(initial.getHeaderString(HttpHeaders.LOCATION)).thenReturn(location);

        Response statusResponse = mock(Response.class);
        when(statusResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(statusResponse.getStatusInfo()).thenReturn(Response.Status.OK);

        Response deleteResponse = mock(Response.class);
        Response.StatusType deleteStatusType = mock(Response.StatusType.class);
        when(deleteStatusType.getFamily()).thenReturn(Response.Status.Family.SERVER_ERROR);
        when(deleteResponse.getStatusInfo()).thenReturn(deleteStatusType);

        ClientBuilder firstBuilder = mock(ClientBuilder.class);
        Client firstClient = mock(Client.class);
        WebTarget firstTarget = mock(WebTarget.class);
        Invocation.Builder getInvocationBuilder = mock(Invocation.Builder.class);

        ClientBuilder secondBuilder = mock(ClientBuilder.class);
        Client secondClient = mock(Client.class);
        WebTarget secondTarget = mock(WebTarget.class);
        Invocation.Builder deleteInvocationBuilder = mock(Invocation.Builder.class);

        when(firstBuilder.connectTimeout(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(firstBuilder);
        when(firstBuilder.build()).thenReturn(firstClient);
        when(firstClient.target(location)).thenReturn(firstTarget);
        when(firstTarget.request()).thenReturn(getInvocationBuilder);
        when(getInvocationBuilder.get()).thenReturn(statusResponse);

        when(secondBuilder.connectTimeout(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(secondBuilder);
        when(secondBuilder.build()).thenReturn(secondClient);
        when(secondClient.target(location)).thenReturn(secondTarget);
        when(secondTarget.request()).thenReturn(deleteInvocationBuilder);
        when(deleteInvocationBuilder.delete()).thenReturn(deleteResponse);

        try (MockedStatic<ClientBuilder> clientBuilderStatic = org.mockito.Mockito.mockStatic(ClientBuilder.class)) {
            // First newBuilder() call is for GET polling, second one is for DELETE cleanup.
            clientBuilderStatic.when(ClientBuilder::newBuilder).thenReturn(firstBuilder, secondBuilder);

            Response result = ApiUtils.waitForAsyncApi(initial);

            assertSame(statusResponse, result);
            verify(getInvocationBuilder).get();
            verify(deleteInvocationBuilder).delete();
        }
    }
}
