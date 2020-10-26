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
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GroupsTest {

    Configuration cfg;

    @Before
    public void setUp() {
        try {
            cfg = Configuration.makeXMLStringAsConfiguration(BASIC_CONFIGURATION);
        } catch (IOException ex) {
            Assert.fail("setUp method should not throw an exception");
        }
    }

    @Test
    public void testBasicConfiguration() {
        Assert.assertEquals("Initial configuration should contain 6 groups",
                6, cfg.getGroups().size());
    }

    @Test
    public void testDeleteGroup() {
        Set<Group> groups = cfg.getGroups();

        invokeMethod("deleteGroup",
                new Class<?>[]{Set.class, String.class},
                new Object[]{groups, "random not existing group"});

        Assert.assertEquals(6, cfg.getGroups().size());

        invokeMethod("deleteGroup",
                new Class<?>[]{Set.class, String.class},
                new Object[]{groups, "apache"});

        Assert.assertEquals(5, cfg.getGroups().size());

        invokeMethod("deleteGroup",
                new Class<?>[]{Set.class, String.class},
                new Object[]{groups, "ctags"});

        Assert.assertEquals(1, cfg.getGroups().size());
    }

    @Test
    public void testAddGroup() {
        Set<Group> groups = cfg.getGroups();
        Group grp = findGroup(groups, "new fantastic group");
        Assert.assertNull(grp);

        invokeMethod("modifyGroup",
                new Class<?>[]{Set.class, String.class, String.class, String.class},
                new Object[]{groups, "new fantastic group", "some pattern", null});

        Assert.assertEquals(7, groups.size());

        grp = findGroup(groups, "new fantastic group");
        Assert.assertNotNull(grp);
        Assert.assertEquals(grp.getName(), "new fantastic group");
        Assert.assertEquals(grp.getPattern(), "some pattern");
    }

    @Test
    public void testAddGroupToParent() {
        Set<Group> groups = cfg.getGroups();
        Group grp = findGroup(groups, "apache");
        Assert.assertNotNull(grp);

        grp = findGroup(groups, "new fantastic group");
        Assert.assertNull(grp);

        invokeMethod("modifyGroup",
                new Class<?>[]{Set.class, String.class, String.class, String.class},
                new Object[]{groups, "new fantastic group", "some pattern", "apache"});

        Assert.assertEquals(7, groups.size());

        grp = findGroup(groups, "apache");
        Assert.assertNotNull(grp);
        Assert.assertEquals(1, grp.getSubgroups().size());
        Assert.assertEquals(1, grp.getDescendants().size());

        grp = findGroup(groups, "new fantastic group");
        Assert.assertNotNull(grp);
        Assert.assertNotNull(grp.getParent());
        Assert.assertEquals(grp.getName(), "new fantastic group");
        Assert.assertEquals(grp.getPattern(), "some pattern");
    }

    @Test
    public void testModifyGroup() {
        Set<Group> groups = cfg.getGroups();
        Group grp = findGroup(groups, "apache");
        Assert.assertNotNull(grp);
        Assert.assertEquals(grp.getName(), "apache");
        Assert.assertEquals(grp.getPattern(), "apache-.*");

        invokeMethod("modifyGroup",
                new Class<?>[]{Set.class, String.class, String.class, String.class},
                new Object[]{groups, "apache", "different pattern", null});

        grp = findGroup(groups, "apache");
        Assert.assertNotNull(grp);
        Assert.assertEquals(grp.getName(), "apache");
        Assert.assertEquals(grp.getPattern(), "different pattern");
    }

    @Test
    public void testMatchGroup() {
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
        Set<Group> groups = cfg.getGroups();

        for (Object[] test : tests) {
            testSingleMatch(groups, (int) test[1], (String) test[0]);
        }
    }

    private void testSingleMatch(Set<Group> groups, int expectedlines, String match) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(os);
        invokeMethod("matchGroups",
                new Class<?>[]{PrintStream.class, Set.class, String.class},
                new Object[]{out, groups, match});

        String output = os.toString();

        Assert.assertEquals(
                "it expects that \"" + match + "\" will match " + expectedlines + " records",
                expectedlines + 1,
                output.split("\\r?\\n").length
        );
    }

    private void invokeMethod(String name, Class<?>[] params, Object[] values) {
        try {
            Method method = Groups.class.getDeclaredMethod(name, params);
            method.setAccessible(true);
            method.invoke(null, values);
        } catch (Exception ex) {
            ex.printStackTrace();
            Assert.fail("invokeMethod should not throw an exception");
        }
    }

    private Group findGroup(Set<Group> groups, String needle) {
        for (Group g : groups) {
            if (g.getName().equals(needle)) {
                return g;
            }
        }
        return null;
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
