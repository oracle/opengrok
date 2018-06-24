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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.api.controller;

import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.history.RepositoryInfo;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path("/repositories")
public class RepositoriesController {

    private RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @GET
    @Path("/types")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getType(@QueryParam("repositories") List<String> repositories) {
        List<String> types = new ArrayList<>();

        for (String tag: repositories) {
            boolean found = false;
            for (RepositoryInfo ri : env.getRepositories()) {
                if (ri.getDirectoryNameRelative().equals(tag)) {
                    types.add(tag + ":" + ri.getType());
                    found = true;
                    break;
                }
            }
            if (!found) {
                types.add(tag + ":N/A");
            }
        }
        return types;
    }

}
