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
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.web.Scripts.Script;

/**
 *
 * @author Krystof Tulinger
 */
public class ScriptsTest {

    private Scripts scripts;

    @Before
    public void setUp() {
        scripts = new Scripts();
    }

    @Test
    public void testInstance() {
        scripts.addScript(new Scripts.FileScript("http://example.com/main1.js", 0));
        scripts.addScript(new Scripts.FileScript("http://example.com/main2.js", 0));
        scripts.addScript(new Scripts.FileScript("http://example.com/main3.js", 0));

        assertEquals(3, scripts.size());

        List<Script> listScripts = new ArrayList<>();
        scripts.forEach(listScripts::add);

        assertEquals(listScripts.get(0).getScriptData(), "http://example.com/main1.js");
        assertEquals(listScripts.get(0).getPriority(), 0);
        assertEquals(listScripts.get(1).getScriptData(), "http://example.com/main2.js");
        assertEquals(listScripts.get(1).getPriority(), 0);
        assertEquals(listScripts.get(2).getScriptData(), "http://example.com/main3.js");
        assertEquals(listScripts.get(2).getPriority(), 0);
    }

    @Test
    public void testSorted() {
        scripts.addScript(new Scripts.FileScript("http://example.com/main1.js", 3));
        scripts.addScript(new Scripts.FileScript("http://example.com/main2.js", 1));
        scripts.addScript(new Scripts.FileScript("http://example.com/main3.js", 2));

        assertEquals(3, scripts.size());

        List<Script> listScripts = new ArrayList<>();
        scripts.forEach(listScripts::add);

        assertEquals(listScripts.get(0).getScriptData(), "http://example.com/main2.js");
        assertEquals(listScripts.get(0).getPriority(), 1);
        assertEquals(listScripts.get(1).getScriptData(), "http://example.com/main3.js");
        assertEquals(listScripts.get(1).getPriority(), 2);
        assertEquals(listScripts.get(2).getScriptData(), "http://example.com/main1.js");
        assertEquals(listScripts.get(2).getPriority(), 3);
    }

    @Test
    public void testContent() {
        scripts.addScript(new Scripts.FileScript("http://example.com/main1.js", 0));
        scripts.addScript(new Scripts.FileScript("http://example.com/main2.js", 0));
        scripts.addScript(new Scripts.FileScript("http://example.com/main3.js", 0));

        assertEquals(3, scripts.size());

        assertTrue(scripts.toHtml()
                .contains("<script type=\"text/javascript\""
                        + " src=\"http://example.com/main1.js\""
                        + " data-priority=\"0\"></script>"));
        assertTrue(scripts.toHtml()
                .contains("<script type=\"text/javascript\""
                        + " src=\"http://example.com/main2.js\""
                        + " data-priority=\"0\"></script>"));
        assertTrue(scripts.toHtml()
                .contains("<script type=\"text/javascript\""
                        + " src=\"http://example.com/main3.js\""
                        + " data-priority=\"0\"></script>"));
    }

    @Test
    public void testLookup() {
        scripts.addScript("", "utils", Scripts.Type.MINIFIED);
        scripts.addScript("", "jquery", Scripts.Type.MINIFIED);
        scripts.addScript("", "diff", Scripts.Type.MINIFIED);
        scripts.addScript("", "jquery-tablesorter", Scripts.Type.MINIFIED);

        assertEquals(4, scripts.size());

        int prev = -1;
        for (Script s : scripts) {
            if (prev > s.getPriority()) {
                fail("The scripts must be sorted in ascending order by the priority, " + prev + " > " + s.getPriority());
            }
            prev = s.getPriority();
        }

        for (Entry<String, Script> s : Scripts.SCRIPTS.entrySet()) {
            if (!s.getKey().equals("utils")
                    && !s.getKey().equals("jquery")
                    && !s.getKey().equals("jquery-tablesorter")
                    && !s.getKey().equals("diff")) {
                continue;
            }
            assertTrue(scripts.toHtml() + " must contain <script type=\"text/javascript\""
                    + " src=\"/" + s.getValue().getScriptData() + "\""
                    + " data-priority=\"" + s.getValue().getPriority() + "\"></script>",
                    scripts.toHtml()
                            .contains("<script type=\"text/javascript\""
                                    + " src=\"/" + s.getValue().getScriptData() + "\""
                                    + " data-priority=\"" + s.getValue().getPriority() + "\"></script>"));
        }
    }

    @Test
    public void testLookupWithContextPath() {
        String contextPath = "/source";
        scripts.addScript(contextPath, "utils", Scripts.Type.MINIFIED);
        scripts.addScript(contextPath, "jquery", Scripts.Type.MINIFIED);
        scripts.addScript(contextPath, "diff", Scripts.Type.MINIFIED);
        scripts.addScript(contextPath, "jquery-tablesorter", Scripts.Type.MINIFIED);

        assertEquals(4, scripts.size());

        int prev = -1;
        for (Script s : scripts) {
            if (prev > s.getPriority()) {
                fail("The scripts must be sorted in ascending order by the priority, " + prev + " > " + s.getPriority());
            }
            prev = s.getPriority();
        }

        for (Entry<String, Script> s : Scripts.SCRIPTS.entrySet()) {
            if (!s.getKey().equals("utils")
                    && !s.getKey().equals("jquery")
                    && !s.getKey().equals("jquery-tablesorter")
                    && !s.getKey().equals("diff")) {
                continue;
            }
            assertTrue(scripts.toHtml() + " must contain <script type=\"text/javascript\""
                    + " src=\"" + contextPath + '/' + s.getValue().getScriptData() + "\""
                    + " data-priority=\"" + s.getValue().getPriority() + "\"></script>",
                    scripts.toHtml()
                            .contains("<script type=\"text/javascript\""
                                    + " src=\"" + contextPath + '/' + s.getValue().getScriptData() + "\""
                                    + " data-priority=\"" + s.getValue().getPriority() + "\"></script>"));
        }
    }

    @Test
    public void testAddMinified() {
        scripts.addScript("", "utils", Scripts.Type.MINIFIED);
        assertTrue(scripts.iterator().next().scriptData.endsWith("min.js"));
    }

    @Test
    public void testAddDebug() {
        scripts.addScript("", "utils", Scripts.Type.DEBUG);
        assertFalse(scripts.iterator().next().scriptData.endsWith("min.js"));
    }

}
