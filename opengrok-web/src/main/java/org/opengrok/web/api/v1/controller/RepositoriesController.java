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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.history.RepositoryInfo;
import org.opengrok.indexer.util.ClassUtil;
import org.opengrok.web.util.DTOUtil;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/repositories")
public class RepositoriesController {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private Object getRepositoryInfoData(String repositoryPath) {
        for (RepositoryInfo ri : env.getRepositories()) {
            if (ri.getDirectoryNameRelative().equals(repositoryPath)) {
                return DTOUtil.createDTO(ri);
            }
        }

        return null;
    }

    @GET
    @Path("/property/{field}")
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(@QueryParam("repository") final String repositoryPath, @PathParam("field") final String field)
            throws IOException {

        Object ri = getRepositoryInfoData(repositoryPath);
        if (ri == null) {
            throw new WebApplicationException("cannot find repository with path: " + repositoryPath,
                    Response.Status.NOT_FOUND);
        }

        return ClassUtil.getFieldValue(ri, field);
    }
}
