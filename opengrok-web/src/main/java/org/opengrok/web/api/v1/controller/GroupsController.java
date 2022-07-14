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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.api.v1.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.Laundromat;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Path(GroupsController.GROUPS_PATH)
public final class GroupsController {
    public static final String GROUPS_PATH = "/groups";

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listGroups() {
        if (env.hasGroups()) {
            return Objects.requireNonNull(env.getGroups()).stream().map(Group::getName).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @GET
    @Path("/{group}/allprojects")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllProjectsForGroup(@PathParam("group") String groupName) {
        groupName = Laundromat.launderInput(groupName);
        Group group;
        group = Group.getByName(groupName);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (group.getAllProjects().isEmpty()) {
            return Response.ok().entity(Collections.emptyList()).build();
        } else {
            List<String> projectNameList = group.getAllProjects().stream().
                    map(Project::getName).collect(Collectors.toList());
            return Response.ok().entity(projectNameList).build();
        }
    }
}
