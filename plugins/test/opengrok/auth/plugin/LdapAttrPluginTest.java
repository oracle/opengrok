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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.util.DummyHttpServletRequestLdap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

public class LdapAttrPluginTest {

    private HttpServletRequest dummyRequest;
    private LdapAttrPlugin plugin;

    private static File whitelistFile;

    @BeforeClass
    public static void beforeClass() throws IOException {
        whitelistFile = Files.createTempFile("opengrok-auth-", "-check.tmp").toFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(whitelistFile))) {
            w.append("james@bond.com\n");
            w.append("random@email.com\n");
            w.append("just_a_text\n");
        }
    }

    @AfterClass
    public static void afterClass() {
        whitelistFile.delete();
    }

    @Before
    public void setUp() {
        plugin = new LdapAttrPlugin();
        Map<String, Object> parameters = new TreeMap<>();
        
        parameters.put(AbstractLdapPlugin.FAKE_PARAM, true);
        parameters.put(LdapAttrPlugin.FILE_PARAM, whitelistFile.getAbsolutePath());
        parameters.put(LdapAttrPlugin.ATTR_PARAM, "mail");

        plugin.load(parameters);
    }

    private void prepareRequest(String username, String mail, String... ous) {
        dummyRequest = new DummyHttpServletRequestLdap();
        dummyRequest.setAttribute(UserPlugin.REQUEST_ATTR, new User(username, "123", null, false));
        dummyRequest.getSession().setAttribute(LdapUserPlugin.SESSION_ATTR, new LdapUser(mail, "123",
                new TreeSet<>(Arrays.asList(ous))));
        plugin.setSessionEstablished(dummyRequest, true);
        plugin.setSessionUsername(dummyRequest, username);
    }

    private Project makeProject(String name) {
        Project p = new Project();
        p.setName(name);
        return p;
    }

    private Group makeGroup(String name) {
        Group p = new Group();
        p.setName(name);
        return p;
    }

    /**
     * Test of isAllowed method, of class LdapFilter.
     */
    @Test
    public void testIsAllowed() {
        /**
         * whitelist[mail] => [james@bond.com, random@email.com, just_a_text]
         */
        prepareRequest("007", "james@bond.com", "MI6", "MI7");

        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));

        prepareRequest("008", "james@bond.com", "MI6", "MI7");

        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));

        prepareRequest("009", "other@email.com", "MI6");

        Assert.assertFalse(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        Assert.assertFalse(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        Assert.assertFalse(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        Assert.assertFalse(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));

        prepareRequest("00A", "random@email.com", "MI6", "MI7");

        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        Assert.assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));
    }
}
