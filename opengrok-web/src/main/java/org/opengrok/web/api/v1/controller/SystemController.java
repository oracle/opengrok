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
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.EftarFile;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.web.PathDescription;
import org.opengrok.web.api.v1.suggester.provider.service.SuggesterService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/system")
public class SystemController {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemController.class);

    @Inject
    private SuggesterService suggester;

    @PUT
    @Path("/refresh")
    @Consumes(MediaType.TEXT_PLAIN)
    public void refresh(final String project) {
        env.maybeRefreshIndexSearchers(Collections.singleton(project));
        CompletableFuture.runAsync(() -> suggester.rebuild(project));
    }

    @PUT
    @Path("/includes/reload")
    public void reloadIncludes() {
        env.getIncludeFiles().reloadIncludeFiles();
    }

    @POST
    @Path("/pathdesc")
    @Consumes(MediaType.APPLICATION_JSON)
    public void loadPathDescriptions(@Valid final PathDescription[] descriptions) throws IOException {
        EftarFile ef = new EftarFile();
        ef.create(Set.of(descriptions), env.getDtagsEftarPath().toString());
        LOGGER.log(Level.INFO, "reloaded path descriptions with {0} entries", descriptions.length);
    }
}
