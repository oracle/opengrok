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
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.util.ClassUtil;
import org.opengrok.web.api.ApiTask;
import org.opengrok.web.api.ApiTaskManager;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.opengrok.web.api.v1.controller.ConfigurationController.PATH;

@Path(PATH)
public class ConfigurationController {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    public static final String PATH = "/configuration";

    @Inject
    private SuggesterService suggesterService;

    @GET
    @Produces(MediaType.APPLICATION_XML)
    public String get() {
        return env.getConfigurationXML();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_XML)
    public Response set(@Context HttpServletRequest request,
                        @QueryParam("reindex") final boolean reindex) throws IOException {

        String body;
        try (InputStream inputStream = request.getInputStream()) {
            body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        return ApiTaskManager.getInstance().submitApiTask(PATH,
                new ApiTask(request.getRequestURI(), () -> {
                    env.applyConfig(body, reindex, CommandTimeoutType.RESTFUL);
                    suggesterService.refresh();
                    return null;
                }));
    }

    @GET
    @Path("/{field}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getField(@PathParam("field") final String field) {
        return getConfigurationValueException(field);
    }

    @PUT
    @Path("/{field}")
    public Response setField(@Context HttpServletRequest request,
                             @PathParam("field") final String field, final String value) {

        setConfigurationValueException(field, value);

        return ApiTaskManager.getInstance().submitApiTask(PATH,
                new ApiTask(request.getRequestURI(), () -> {
                    // apply the configuration - let the environment reload the configuration if necessary
                    env.applyConfig(false, CommandTimeoutType.RESTFUL);
                    suggesterService.refresh();
                    return null;
                }));
    }

    @POST
    @Path("/authorization/reload")
    public Response reloadAuthorization(@Context HttpServletRequest request) {
        return ApiTaskManager.getInstance().submitApiTask("authorization",
                new ApiTask(request.getRequestURI(),
                        () -> {
                            env.getAuthorizationFramework().reload();
                            return null;
                        },
                        Response.Status.NO_CONTENT));
    }

    private Object getConfigurationValueException(String fieldName) throws WebApplicationException {
        final IOException[] capture = new IOException[1];
        final int IOE_INDEX = 0;
        Object result = env.syncReadConfiguration(configuration -> {
            try {
                return ClassUtil.getFieldValue(configuration, fieldName);
            } catch (IOException ex) {
                capture[IOE_INDEX] = ex;
                return null;
            }
        });
        if (capture[IOE_INDEX] != null) {
            throw new WebApplicationException(capture[IOE_INDEX], Response.Status.BAD_REQUEST);
        }
        return result;
    }

    private void setConfigurationValueException(String fieldName, String value)
            throws WebApplicationException {

        final IOException[] capture = new IOException[1];
        final int IOE_INDEX = 0;
        env.syncWriteConfiguration(value, (configuration, v) -> {
            try {
                ClassUtil.setFieldValue(configuration, fieldName, v);
            } catch (IOException ex) {
                capture[IOE_INDEX] = ex;
            }
        });
        if (capture[IOE_INDEX] != null) {
            throw new WebApplicationException(capture[IOE_INDEX], Response.Status.BAD_REQUEST);
        }
    }
}
