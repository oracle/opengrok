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

import java.util.Map;
import java.util.TreeMap;
import opengrok.auth.plugin.entity.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Vladimir Kotal
 */
public class LdapUserPluginTest {
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
}
