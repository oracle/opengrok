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
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import opengrok.auth.plugin.entity.User;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.web.DummyHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Represents a container for tests of {@link FalsePlugin}.
 */
class FalsePluginTest {

    private FalsePlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new FalsePlugin();
    }

    @Test
    void shouldNotThrowOnLoadIfNullArgument() {
        assertDoesNotThrow(() ->
                plugin.load(null)
        );
    }

    @Test
    void shouldUnload() {
        assertDoesNotThrow(() ->
                plugin.unload()
        );
    }

    @Test
    void shouldNotAllowRandomUserForAnyProject() {
        DummyHttpServletRequest req = new DummyHttpServletRequest();
        req.setAttribute(UserPlugin.REQUEST_ATTR, new User(RandomStringUtils.secure().nextAlphanumeric(8)));

        Project randomProject = new Project(RandomStringUtils.secure().nextAlphanumeric(10));
        boolean projectAllowed = plugin.isAllowed(req, randomProject);
        assertFalse(projectAllowed, "should not allow rando for random project 1");

        randomProject = new Project(RandomStringUtils.secure().nextAlphanumeric(10));
        projectAllowed = plugin.isAllowed(req, randomProject);
        assertFalse(projectAllowed, "should not allow rando for random project 2");
    }

    @Test
    void shouldNotAllowRandomUserForAnyGroup() {
        DummyHttpServletRequest req = new DummyHttpServletRequest();
        req.setAttribute(UserPlugin.REQUEST_ATTR, new User(RandomStringUtils.secure().nextAlphanumeric(8)));

        Group randomGroup = new Group(RandomStringUtils.secure().nextAlphanumeric(10));
        boolean projectAllowed = plugin.isAllowed(req, randomGroup);
        assertFalse(projectAllowed, "should not allow rando for random group 1");

        randomGroup = new Group(RandomStringUtils.secure().nextAlphanumeric(10));
        projectAllowed = plugin.isAllowed(req, randomGroup);
        assertFalse(projectAllowed, "should not allow rando for random group 2");
    }
}
