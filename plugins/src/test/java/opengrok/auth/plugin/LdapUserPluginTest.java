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
package opengrok.auth.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.ldap.AbstractLdapProvider;
import opengrok.auth.plugin.ldap.LdapException;
import opengrok.auth.plugin.ldap.LdapFacade;
import opengrok.auth.plugin.util.DummyHttpServletRequestLdap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

import javax.servlet.http.HttpServletRequest;

import static opengrok.auth.plugin.LdapUserPlugin.SESSION_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 * @author Vladimir Kotal
 */
public class LdapUserPluginTest {
    @Spy
    private LdapUserPlugin plugin;

    @Before
    public void setUp() {
        plugin = new LdapUserPlugin();
    }

    private Map<String, Object> getParamsMap() {
        Map<String, Object> params = new TreeMap<>();
        params.put(AbstractLdapPlugin.CONFIGURATION_PARAM,
                getClass().getResource("config.xml").getFile());
        
        return params;
    }
    
    @Test(expected = NullPointerException.class)
    public void loadTestNegative1() {
        Map<String, Object> params = getParamsMap();
        params.put("foo", (Object)"bar");
        plugin.load(params);
    }
    
    @Test
    public void loadTestPositive() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, (Object)"mail");
        plugin.load(params);
    }
    
    @Test
    public void filterTest() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.LDAP_FILTER, (Object) "(&(objectclass=person)(mail=%username%))");
        params.put(LdapUserPlugin.ATTRIBUTES, (Object) "uid,mail");
        plugin.load(params);

        User user = new User("foo@bar.cz", "id", null, false);
        String filter = plugin.expandFilter(user);
        Assert.assertEquals("(&(objectclass=person)(mail=foo@bar.cz))", filter);
    }

    @Test
    public void testFillSessionWithDnOn() throws LdapException {
        AbstractLdapProvider mockprovider = mock(LdapFacade.class);
        Map<String, Set<String>> attrs = new HashMap<>();
        attrs.put("foo", null);
        AbstractLdapProvider.LdapSearchResult<Map<String, Set<String>>> result =
                new AbstractLdapProvider.LdapSearchResult<>("foo@bar.cz", attrs);
        assertNotNull(result);
        when(mockprovider.lookupLdapContent(isNull(), isNull(), any(String[].class))).
                thenReturn(result);

        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.ATTRIBUTES, (Object)"mail");
        params.put(LdapUserPlugin.USE_DN, (Object)false);
        LdapUserPlugin plugin = new LdapUserPlugin();
        plugin.load(params, mockprovider);
        assertEquals(mockprovider, plugin.getLdapProvider());

        HttpServletRequest request = new DummyHttpServletRequestLdap();
        User user = new User("foo@bar.cz", "id", null, false);
        plugin.fillSession(request, user);

        assertNotNull(request.getSession().getAttribute(SESSION_ATTR));
        assertEquals(user.getUsername(), ((LdapUser)request.getSession().getAttribute(SESSION_ATTR)).getId());
    }
}
