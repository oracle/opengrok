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
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LdapFilterPluginTest {

    private LdapFilterPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new LdapFilterPlugin();
    }

    @Test
    void expandFilterTest1() {
        LdapUser ldapUser = new LdapUser();
        ldapUser.setAttribute("mail", new TreeSet<>(Collections.singletonList("james@bond")));
        ldapUser.setAttribute("uid", new TreeSet<>(Collections.singletonList("bondjame")));
        ldapUser.setAttribute("ou", new TreeSet<>(Arrays.asList("MI6", "MI7")));
        User user = new User("007", "123", null, true);

        assertEquals("(objectclass=james@bond)",
                plugin.expandFilter("(objectclass=%mail%)", ldapUser, user));
        assertEquals("(objectclass=bondjame)",
                plugin.expandFilter("(objectclass=%uid%)", ldapUser, user));
        assertEquals("(objectclass=007)",
                plugin.expandFilter("(objectclass=%username%)", ldapUser, user));
        assertEquals("(objectclass=123)",
                plugin.expandFilter("(objectclass=%guid%)", ldapUser, user));

        ldapUser.setAttribute("role", new TreeSet<>(Collections.singletonList("agent")));
        assertEquals("(objectclass=agent)",
                plugin.expandFilter("(objectclass=%role%)", ldapUser, user));

        // doesn't work for more than one value
        ldapUser.setAttribute("role", new TreeSet<>(Arrays.asList("agent", "double-agent")));
        assertEquals("(objectclass=%role%)",
                plugin.expandFilter("(objectclass=%role%)", ldapUser, user));
    }

    @Test
    void expandFilterTest2() {
        LdapUser ldapUser = new LdapUser();
        ldapUser.setAttribute("mail", new TreeSet<>(Collections.singletonList("james@bond")));
        ldapUser.setAttribute("uid", new TreeSet<>(Collections.singletonList("bondjame")));
        ldapUser.setAttribute("ou", new TreeSet<>(Arrays.asList("MI6", "MI7")));
        User user = new User("007", "123", null, true);

        assertEquals("(objectclass=%james@bond%)",
                plugin.expandFilter("(objectclass=%%mail%%)", ldapUser, user));

        assertEquals("(objectclass=%james@bond%)",
                plugin.expandFilter("(objectclass=\\%%mail%\\%)", ldapUser, user));

        assertEquals("(objectclass=%mail%)",
                plugin.expandFilter("(objectclass=\\%mail\\%)", ldapUser, user));

        assertEquals("(objectclass=%mail)",
                plugin.expandFilter("(objectclass=\\%mail)", ldapUser, user));

        assertEquals("(objectclass=mail)",
                plugin.expandFilter("(objectclass=mail)", ldapUser, user));

        assertEquals("(objectclass=%mail)",
                plugin.expandFilter("(objectclass=%mail)", ldapUser, user));

        assertEquals("(objectclass=%%%%)",
                plugin.expandFilter("(objectclass=\\%%\\%\\%)", ldapUser, user));
    }

    @Test
    void testLoadTransforms() {
        assertDoesNotThrow( () ->
                plugin.loadTransforms("foo:toUpperCase,bar:toLowerCase")
        );
    }

    @Test
    void testLoadTransformsNegative() {
        assertThrows(UnsupportedOperationException.class, () ->
                plugin.loadTransforms("foo:toUpperCase,ugly:nice")
        );
    }

    private Map<String, Object> getParamsMap() {
        Map<String, Object> params = new TreeMap<>();
        params.put(AbstractLdapPlugin.CONFIGURATION_PARAM,
                Objects.requireNonNull(getClass().getResource("config.yml")).getFile());

        return params;
    }

    @Test
    void loadTestNegativeNoFilterParam() {
        Map<String, Object> params = getParamsMap();
        assertNull(params.get(LdapFilterPlugin.FILTER_PARAM));
        assertThrows(NullPointerException.class, () -> plugin.load(params));
    }

    @Test
    void loadTestPositive() {
        Map<String, Object> params = getParamsMap();
        params.put(LdapFilterPlugin.FILTER_PARAM, "foo:toUpperCase");
        params.put(LdapFilterPlugin.INSTANCE, "42");
        assertDoesNotThrow(() -> plugin.load(params));
    }
}
