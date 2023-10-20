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
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the {@link Groups} command.
 */
class GroupsTest {

    Configuration cfg;

    @BeforeEach
    void setUp() throws IOException {
        cfg = Configuration.makeXMLStringAsConfiguration(BASIC_CONFIGURATION);
    }

    @Test
    void testBasicConfiguration() {
        assertEquals(6, cfg.getGroups().size(), "Initial configuration should contain 6 groups");
    }

    @Test
    void testDeleteGroup() {
        Map<String, Group> groups = cfg.getGroups();
        final int origSize = groups.size();
        invokeMethod("deleteGroup",
                new Class<?>[]{Map.class, String.class},
                new Object[]{groups, "random not existing group"});

        assertEquals(origSize, groups.size());

        invokeMethod("deleteGroup",
                new Class<?>[]{Map.class, String.class},
                new Object[]{groups, "apache"});

        assertEquals(origSize - 1, groups.size());

        invokeMethod("deleteGroup",
                new Class<?>[]{Map.class, String.class},
                new Object[]{groups, "ctags"});

        assertEquals(1, groups.size());
    }

    @Test
    void testAddGroup() {
        Map<String, Group> groups = cfg.getGroups();
        final int origSize = groups.size();
        Group grp = findGroup(groups, "new fantastic group");
        assertNull(grp);

        invokeMethod("modifyGroup",
                new Class<?>[]{Map.class, String.class, String.class, String.class},
                new Object[]{groups, "new fantastic group", "some pattern", null});

        assertEquals(origSize + 1, groups.size());

        grp = findGroup(groups, "new fantastic group");
        assertNotNull(grp);
        assertEquals("new fantastic group", grp.getName());
        assertEquals("some pattern", grp.getPattern());
    }

    @Test
    void testAddGroupToParent() {
        Map<String, Group> groups = cfg.getGroups();
        final int origSize = groups.size();
        Group grp = findGroup(groups, "apache");
        assertNotNull(grp);

        grp = findGroup(groups, "new fantastic group");
        assertNull(grp);

        invokeMethod("modifyGroup",
                new Class<?>[]{Map.class, String.class, String.class, String.class},
                new Object[]{groups, "new fantastic group", "some pattern", "apache"});

        assertEquals(origSize + 1, groups.size());

        grp = findGroup(groups, "apache");
        assertNotNull(grp);
        assertEquals(1, grp.getSubgroups().size());
        assertEquals(1, grp.getDescendants().size());

        grp = findGroup(groups, "new fantastic group");
        assertNotNull(grp);
        assertNotNull(grp.getParent());
        assertEquals("new fantastic group", grp.getName());
        assertEquals("some pattern", grp.getPattern());
    }

    @Test
    void testModifyGroup() {
        Map<String, Group> groups = cfg.getGroups();
        Group grp = findGroup(groups, "apache");
        assertNotNull(grp);
        assertEquals("apache", grp.getName());
        assertEquals("apache-.*", grp.getPattern());

        invokeMethod("modifyGroup",
                new Class<?>[]{Map.class, String.class, String.class, String.class},
                new Object[]{groups, "apache", "different pattern", null});

        grp = findGroup(groups, "apache");
        assertNotNull(grp);
        assertEquals("apache", grp.getName());
        assertEquals("different pattern", grp.getPattern());
    }

    @Test
    void testMatchGroup() {
        Object[][] tests = new Object[][]{
            {"null", 0},
            {"apache", 0},
            {"apache-2.2", 1},
            {"ctags 5.6.6.7.4", 1},
            {"ctags", 0},
            {"opengrok", 1},
            {"opengrok-12.0-rc3", 1},
            {"opengrk", 0}
        };
        Map<String, Group> groups = cfg.getGroups();

        for (Object[] test : tests) {
            testSingleMatch(groups, (int) test[1], (String) test[0]);
        }
    }

    private void testSingleMatch(Map<String, Group> groups, int expectedlines, String match) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);
        invokeMethod("matchGroups",
                new Class<?>[]{PrintStream.class, Map.class, String.class},
                new Object[]{out, groups, match});

        String output = os.toString();

        assertEquals(expectedlines + 1, output.split("\\r?\\n").length,
                "it expects that \"" + match + "\" will match " + expectedlines + " records"
        );
    }

    private void invokeMethod(String name, Class<?>[] params, Object[] values) {
        try {
            Method method = Groups.class.getDeclaredMethod(name, params);
            method.setAccessible(true);
            method.invoke(null, values);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Group findGroup(Map<String, Group> groups, String needle) {
        return groups.get(needle);
    }

    static final String BASIC_CONFIGURATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<java version=\"1.8.0_65\" class=\"java.beans.XMLDecoder\">\n"
            + " <object class=\"org.opengrok.indexer.configuration.Configuration\" id=\"Configuration0\">\n"
            + "    <void method=\"addGroup\">\n"
            + "        <object class=\"org.opengrok.indexer.configuration.Group\">\n"
            + "            <void property=\"name\">\n"
            + "                <string>ctags</string>\n"
            + "            </void>\n"
            + "            <void property=\"pattern\">\n"
            + "                <string></string>\n"
            + "            </void>\n"
            + "            <void method=\"addGroup\">\n"
            + "                <object class=\"org.opengrok.indexer.configuration.Group\">\n"
            + "                    <void property=\"name\">\n"
            + "                        <string>ctags 5.6</string>\n"
            + "                    </void>\n"
            + "                    <void property=\"pattern\">\n"
            + "                        <string>ctags 5.6.*</string>\n"
            + "                    </void>\n"
            + "                </object>\n"
            + "            </void>\n"
            + "            <void method=\"addGroup\">\n"
            + "                <object class=\"org.opengrok.indexer.configuration.Group\">\n"
            + "                    <void property=\"name\">\n"
            + "                        <string>ctags 5.7</string>\n"
            + "                    </void>\n"
            + "                    <void property=\"pattern\">\n"
            + "                        <string>ctags 5.7</string>\n"
            + "                    </void>\n"
            + "                </object>\n"
            + "            </void>\n"
            + "            <void method=\"addGroup\">\n"
            + "                <object class=\"org.opengrok.indexer.configuration.Group\">\n"
            + "                    <void property=\"name\">\n"
            + "                        <string>ctags 5.8</string>\n"
            + "                    </void>\n"
            + "                    <void property=\"pattern\">\n"
            + "                        <string>ctags 5.8</string>\n"
            + "                    </void>\n"
            + "                </object>\n"
            + "            </void>\n"
            + "        </object>\n"
            + "    </void>\n"
            + "    <void method=\"addGroup\">\n"
            + "        <object class=\"org.opengrok.indexer.configuration.Group\">\n"
            + "            <void property=\"name\">\n"
            + "                <string>apache</string>\n"
            + "            </void>\n"
            + "            <void property=\"pattern\">\n"
            + "                <string>apache-.*</string>\n"
            + "            </void>\n"
            + "        </object>\n"
            + "    </void>\n"
            + "    <void method=\"addGroup\">\n"
            + "        <object class=\"org.opengrok.indexer.configuration.Group\">\n"
            + "            <void property=\"name\">\n"
            + "                <string>opengrok</string>\n"
            + "            </void>\n"
            + "            <void property=\"pattern\">\n"
            + "                <string>opengrok.*</string>\n"
            + "            </void>\n"
            + "        </object>\n"
            + "    </void>\n"
            + " </object>\n"
            + "</java>";
}
