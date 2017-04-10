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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.authorization.plugins;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.web.DummyHttpServletRequest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Krystof Tulinger
 */
public class ProjectPluginTest {

    /**
     * Test of load method, of class ProjectPlugin.
     */
    @Test(expected = Exception.class)
    public void testLoad1() {
        Map<String, Object> parameters = null;
        ProjectPlugin instance = new ProjectPlugin();
        instance.load(null);
    }

    /**
     * Test of load method, of class ProjectPlugin.
     */
    @Test(expected = Exception.class)
    public void testLoad2() {
        Map<String, Object> parameters = new TreeMap<>();
        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);
    }

    /**
     * Test of load method, of class ProjectPlugin.
     */
    @Test(expected = Exception.class)
    public void testLoad3() {
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("xxxx", "some");
        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);
    }

    /**
     * Test of load method, of class ProjectPlugin.
     */
    @Test
    public void testLoad4() {
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("projects", "Awesome project");
        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);
    }

    /**
     * Test of load method, of class ProjectPlugin.
     */
    @Test
    public void testLoad5() {
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("projects", new String[]{"Awesome project", "God project"});
        parameters.put("xxx", new Object());
        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);
    }

    /**
     * Test of load method, of class ProjectPlugin.
     */
    @Test
    public void testLoad6() {
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("projects", Arrays.asList(new String[]{"Awesome project", "God project"}));
        parameters.put("xxx xxx", new Object());
        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);
    }

    /**
     * Test of isAllowed method, of class GroupPlugin.
     */
    @Test
    public void testIsAllowedProject() {
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("projects", new String[]{"Awesome project", "God project"});

        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);

        Project p = new Project();
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Awesome project");
        assertTrue(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("God project");
        assertTrue(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Not an awesome project");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Awesome group");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("God group");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Not an awesome group");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
    }

    /**
     * Test of isAllowed method, of class GroupPlugin.
     */
    @Test
    public void testIsAllowedGroup() {
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("projects", new String[]{"Awesome project", "God project"});

        ProjectPlugin instance = new ProjectPlugin();
        instance.load(parameters);

        Group p = new Group();

        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Awesome project");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("God project");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Not an awesome project");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Awesome group");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("God group");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
        p.setName("Not an awesome group");
        assertFalse(instance.isAllowed(new DummyHttpServletRequest(), p));
    }
}
