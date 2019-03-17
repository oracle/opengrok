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
    
    @Test
    public void loadTestNegative1() {
        Map<String, Object> params = getParamsMap();
        params.put("foo", (Object)"bar");
        try {
            plugin.load(params);
            Assert.fail("should have caused exception");
        } catch (NullPointerException e) {
        }
    }
    
    @Test
    public void loadTestNegative2() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.OBJECT_CLASS, (Object)"#$@");
        try {
            plugin.load(params);
            Assert.fail("should have caused exception");
        } catch (NullPointerException e) {
        }
    }
    
    @Test
    public void loadTestPostitive() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapUserPlugin.OBJECT_CLASS, (Object)"posixUser");
        try {
            plugin.load(params);
        } catch (NullPointerException e) {
            Assert.fail("should not cause exception");
        }
    }
    
    @Test
    public void getFilterTest1() {
        Map<String, Object> params = getParamsMap();
        String cl = "posixUser";
        params.put(LdapUserPlugin.OBJECT_CLASS, (Object) cl);
        plugin.load(params);
        String cn = "cn=foo-foo_bar1";
        User user = new User(cn + ",l=EMEA,dc=foobar,dc=com", "id", null, false);
        String filter = plugin.getFilter(user);
        Assert.assertEquals("(&(" + LdapUserPlugin.OBJECT_CLASS + "=" + cl + ")(" + cn + "))",
                filter);
    }
}
