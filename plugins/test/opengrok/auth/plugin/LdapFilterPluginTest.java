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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.util.Arrays;
import java.util.TreeSet;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LdapFilterPluginTest {

    private LdapFilterPlugin plugin;

    @Before
    public void setUp() {
        plugin = new LdapFilterPlugin();
    }

    @Test
    public void expandFilterTest1() {
        LdapUser ldapUser = new LdapUser("james@bond", "bondjame",
                new TreeSet<>(Arrays.asList(new String[]{"MI6", "MI7"})));
        User user = new User("007", "123", null, true);

        assertEquals("(objectclass=james@bond)",
                plugin.expandFilter("(objectclass=%mail%)", ldapUser, user));
        assertEquals("(objectclass=bondjame)",
                plugin.expandFilter("(objectclass=%uid%)", ldapUser, user));
        assertEquals("(objectclass=007)",
                plugin.expandFilter("(objectclass=%username%)", ldapUser, user));
        assertEquals("(objectclass=123)",
                plugin.expandFilter("(objectclass=%guid%)", ldapUser, user));

        ldapUser.setAttribute("role", new TreeSet<>(Arrays.asList(new String[]{"agent"})));
        assertEquals("(objectclass=agent)",
                plugin.expandFilter("(objectclass=%role%)", ldapUser, user));

        // doesn't work for more than one value
        ldapUser.setAttribute("role", new TreeSet<>(Arrays.asList(new String[]{"agent", "double-agent"})));
        assertEquals("(objectclass=%role%)",
                plugin.expandFilter("(objectclass=%role%)", ldapUser, user));
    }

    @Test
    public void expandFilterTest2() {
        LdapUser ldapUser = new LdapUser("james@bond", "bondjame",
                new TreeSet<>(Arrays.asList(new String[]{"MI6", "MI7"})));
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
}
