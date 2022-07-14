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

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.GenericType;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupsControllerTest extends OGKJerseyTest {

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    @Override
    protected Application configure() {
        return new ResourceConfig(GroupsController.class);
    }

    private List<String> listGroups() {
        GenericType<List<String>> type = new GenericType<>() {
        };

        return target("groups")
                .request()
                .get(type);
    }

    @Test
    void emptyGroups() {
        env.setGroups(new HashSet<>());
        assertFalse(env.hasGroups());
        List<String> groups = listGroups();
        assertNotNull(groups);
        assertEquals(0, groups.size());
    }

    @Test
    void testGroupList() {
        Set<Group> groups = new TreeSet<>();
        Group group;
        group = new Group("group-foo", "project-(1|2|3)");
        groups.add(group);
        group = new Group("group-bar", "project-(7|8|9)");
        groups.add(group);
        env.setGroups(groups);
        assertTrue(env.hasGroups());

        List<String> groupsResult = listGroups();
        assertEquals(groups.stream().map(Group::getName).collect(Collectors.toSet()), new HashSet<>(groupsResult));
    }
}
