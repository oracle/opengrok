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
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.ldap.AbstractLdapProvider;
import opengrok.auth.plugin.ldap.FakeLdapFacade;
import opengrok.auth.plugin.ldap.LdapException;
import opengrok.auth.plugin.ldap.LdapFacade;
import opengrok.auth.plugin.util.DummyHttpServletRequestLdap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LdapAttrPluginTest {

    private HttpServletRequest dummyRequest;
    private LdapAttrPlugin plugin;

    private static File whitelistFile;

    @BeforeAll
    public static void beforeClass() throws IOException {
        whitelistFile = Files.createTempFile("opengrok-auth-", "-check.tmp").toFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(whitelistFile))) {
            w.append("james@bond.com\n");
            w.append("random@email.com\n");
            w.append("just_a_text\n");
        }
    }

    @AfterAll
    public static void afterClass() {
        whitelistFile.delete();
    }

    @BeforeEach
    public void setUp() {
        plugin = new LdapAttrPlugin();
        Map<String, Object> parameters = new TreeMap<>();

        parameters.put(LdapAttrPlugin.FILE_PARAM, whitelistFile.getAbsolutePath());
        parameters.put(LdapAttrPlugin.ATTR_PARAM, "mail");

        plugin.load(parameters, new FakeLdapFacade());
    }

    private void prepareRequest(String username, String mail, String... ous) {
        dummyRequest = new DummyHttpServletRequestLdap();
        dummyRequest.setAttribute(UserPlugin.REQUEST_ATTR,
                new User(username, "123"));

        LdapUser ldapUser = new LdapUser();
        ldapUser.setAttribute("mail", new TreeSet<>(Collections.singletonList(mail)));
        ldapUser.setAttribute("uid", new TreeSet<>(Collections.singletonList("123")));
        ldapUser.setAttribute("ou", new TreeSet<>(Arrays.asList(ous)));

        dummyRequest.getSession().setAttribute(LdapUserPlugin.SESSION_ATTR, ldapUser);
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
     * Test of {@code isAllowed} method.
     */
    @Test
    void testIsAllowed() {
        /*
         * whitelist[mail] => [james@bond.com, random@email.com, just_a_text]
         */
        prepareRequest("007", "james@bond.com", "MI6", "MI7");

        assertTrue(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        assertTrue(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));

        prepareRequest("008", "james@bond.com", "MI6", "MI7");

        assertTrue(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        assertTrue(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));

        prepareRequest("009", "other@email.com", "MI6");

        assertFalse(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        assertFalse(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        assertFalse(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        assertFalse(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));

        prepareRequest("00A", "random@email.com", "MI6", "MI7");

        assertTrue(plugin.isAllowed(dummyRequest, makeProject("Random Project")));
        assertTrue(plugin.isAllowed(dummyRequest, makeProject("Project 1")));
        assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 1")));
        assertTrue(plugin.isAllowed(dummyRequest, makeGroup("Group 2")));
    }

    /**
     * Test the interaction between {@code LdapUserPlugin} and {@code LdapAttrPlugin}. Namely:
     * <ul>
     *     <li>use of DN from the <code>LdapUser</code> object cached in the session by <code>LdapUserPlugin</code></li>
     *     <li>configuration of the cached session attribute name</li>
     * </ul>
     */
    @Test
    void testAttrLookup() throws LdapException {
        String attr_to_get = "mail";
        String instance_num = "42";
        String mail_attr_value = "james@bond.com";

        // Create mock LDAP provider, simulating the work of LdapUserPlugin.
        AbstractLdapProvider mockprovider = mock(LdapFacade.class);
        Map<String, Set<String>> attrs = new HashMap<>();
        attrs.put(attr_to_get, Collections.singleton(mail_attr_value));
        final String dn = "cn=FOO_BAR,L=EMEA,DC=FOO,DC=COM";
        AbstractLdapProvider.LdapSearchResult<Map<String, Set<String>>> result =
                new AbstractLdapProvider.LdapSearchResult<>(dn, attrs);
        assertNotNull(result);
        // TODO use Mockito Argument captor ?
        when(mockprovider.lookupLdapContent(anyString(), any(String[].class))).
                thenReturn(result);

        // Load the LdapAttrPlugin using the mock LDAP provider.
        LdapAttrPlugin plugin = new LdapAttrPlugin();
        Map<String, Object> parameters = new TreeMap<>();
        parameters.put(LdapAttrPlugin.FILE_PARAM, whitelistFile.getAbsolutePath());
        parameters.put(LdapAttrPlugin.ATTR_PARAM, attr_to_get);
        parameters.put(LdapAttrPlugin.INSTANCE_PARAM, instance_num);
        plugin.load(parameters, mockprovider);

        LdapUser ldapUser = new LdapUser(dn, null);
        HttpServletRequest request = new DummyHttpServletRequestLdap();
        request.getSession().setAttribute(LdapUserPlugin.SESSION_ATTR + instance_num, ldapUser);

        // Here it comes all together.
        User user = new User("foo@bar.cz", "id");
        plugin.fillSession(request, user);

        // See if LdapAttrPlugin set its own session attribute based on the mocked query.
        assertTrue((Boolean) request.getSession().getAttribute(plugin.getSessionAllowedAttrName()));
        assertTrue(ldapUser.getAttribute(attr_to_get).contains(mail_attr_value));
    }
}
