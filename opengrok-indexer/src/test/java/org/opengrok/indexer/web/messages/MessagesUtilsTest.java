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
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.indexer.web.messages;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@net.jcip.annotations.NotThreadSafe
public class MessagesUtilsTest {
    private static RuntimeEnvironment env;

    @BeforeClass
    public static void setUpClass() {
        env = RuntimeEnvironment.getInstance();
    }

    @Test
    public void testGetMessagesForNonExistentProject() {
        String projectName = "nonexistent";
        Project foo = new Project(projectName, "/doesnotexist");
        HashMap<String, Project> projects = new HashMap<>();
        projects.put(projectName, foo);
        env.setProjectsEnabled(true);
        env.setProjects(projects);

        String jsonString = MessagesUtils.messagesToJson(projectName);
        assertNotNull(jsonString);
        assertEquals("", jsonString);
    }
}
