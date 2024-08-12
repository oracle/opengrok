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
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.ldap.AbstractLdapProvider;
import opengrok.auth.plugin.ldap.LdapException;
import opengrok.auth.plugin.ldap.LdapFacade;
import opengrok.auth.plugin.util.DummyHttpServletRequestLdap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

import static opengrok.auth.plugin.LdapUserPlugin.SESSION_ATTR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Vladimir Kotal
 */
class LdapUserPluginTest {

    private LdapUserPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new LdapUserPlugin();
    }

    private Map<String, Object> getParamsMap() {
        Map<String, Object> params = new TreeMap<>();
        params.put(AbstractLdapPlugin.CONFIGURATION_PARAM,
                getClass().getResource("config.yml").getFile());

        return params;
    }

    @Test
    void loadTestNegative1() {
        Map<String, Object> params = getParamsMap();
        params.put("foo", "bar");
        assertThrows(NullPointerException.class, () -> plugin.load(params));
    }

    @Test
    void loadTestPositive() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, "mail");
        assertDoesNotThrow(() ->
                plugin.load(params)
        );
    }

    @Test
    void filterTest() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.LDAP_FILTER, "(&(objectclass=person)(mail=%username%))");
        params.put(LdapUserPlugin.ATTRIBUTES, "uid,mail");
        plugin.load(params);

        User user = new User("foo@example.com", "id", null, false);
        String filter = plugin.expandFilter(user);
        assertEquals("(&(objectclass=person)(mail=foo@example.com))", filter);
    }

    @Test
    void testFillSessionWithDnOff() throws LdapException {
        AbstractLdapProvider mockprovider = mock(LdapFacade.class);
        Map<String, Set<String>> attrs = new HashMap<>();
        attrs.put("mail", Collections.singleton("foo@example.com"));
        final String dn = "cn=FOO_BAR,L=EMEA,DC=EXAMPLE,DC=COM";
        AbstractLdapProvider.LdapSearchResult<Map<String, Set<String>>> result =
                new AbstractLdapProvider.LdapSearchResult<>(dn, attrs);
        assertNotNull(result);
        when(mockprovider.lookupLdapContent(isNull(), isNull(), any(String[].class))).
                thenReturn(result);

        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, "mail");
        params.put(LdapUserPlugin.USE_DN, false);
        LdapUserPlugin plugin = new LdapUserPlugin();
        plugin.load(params, mockprovider);
        assertSame(mockprovider, plugin.getLdapProvider());

        HttpServletRequest request = new DummyHttpServletRequestLdap();
        User user = new User("foo@example.com", "id");
        plugin.fillSession(request, user);

        assertNotNull(request.getSession().getAttribute(SESSION_ATTR));
        assertEquals(dn, ((LdapUser) request.getSession().getAttribute(SESSION_ATTR)).getDn());
    }

    /**
     * Test that supplied LDAP filter is expanded for the LDAP query.
     */
    @Test
    void testFilterExpansion() throws Exception {
        AbstractLdapProvider mockprovider = mock(LdapFacade.class);
        HttpServletRequest request = new DummyHttpServletRequestLdap();
        User user = new User("foo@example.com", "id");
        LdapUserPlugin plugin = new LdapUserPlugin();
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, "mail");
        params.put(LdapUserPlugin.LDAP_FILTER, "%guid%");
        plugin.load(params, mockprovider);
        plugin.fillSession(request, user);
        final String expectedFilter = plugin.expandFilter(user);
        verify(mockprovider).lookupLdapContent(eq(null), eq(expectedFilter), any(String[].class));
    }

    @Test
    void testNegativeCache() throws LdapException {
        AbstractLdapProvider mockprovider = mock(LdapFacade.class);
        when(mockprovider.lookupLdapContent(isNull(), isNull(), any(String[].class))).thenReturn(null);

        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, "mail");
        params.put(LdapUserPlugin.USE_DN, false);
        LdapUserPlugin origPlugin = new LdapUserPlugin();
        LdapUserPlugin plugin = Mockito.spy(origPlugin);
        plugin.load(params, mockprovider);
        assertSame(mockprovider, plugin.getLdapProvider());

        HttpServletRequest dummyRequest = new DummyHttpServletRequestLdap();
        User user = new User("foo@example.com", "id");
        dummyRequest.setAttribute(UserPlugin.REQUEST_ATTR, new User("foo", "123"));
        plugin.fillSession(dummyRequest, user);

        assertNotNull(dummyRequest.getSession().getAttribute(SESSION_ATTR));
        assertFalse(plugin.isAllowed(dummyRequest, new Project("foo")));
        assertFalse(plugin.isAllowed(dummyRequest, new Group("bar")));
        // Make sure that the session was filled so that the second call to isAllowed() did not fill it again.
        verify(plugin, times(2)).updateSession(eq(dummyRequest), anyString(), anyBoolean());
    }

    @Test
    void testInstance() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, "mail");
        params.put(LdapUserPlugin.INSTANCE, "42");
        plugin.load(params);

        HttpServletRequest request = new DummyHttpServletRequestLdap();
        LdapUser ldapUser = new LdapUser();
        plugin.updateSession(request, ldapUser);
        assertEquals(request.getSession().getAttribute(SESSION_ATTR + "42"), ldapUser);
    }

    @Test
    void testInvalidInstance() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, "mail");
        params.put(LdapUserPlugin.INSTANCE, "foobar");
        assertThrows(NumberFormatException.class, () -> plugin.load(params));
    }
}
