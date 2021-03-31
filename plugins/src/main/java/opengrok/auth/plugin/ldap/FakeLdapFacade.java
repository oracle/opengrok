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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.ldap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Krystof Tulinger
 */
public class FakeLdapFacade extends AbstractLdapProvider {

    @Override
    public LdapSearchResult<Map<String, Set<String>>> lookupLdapContent(String dn, String filter, String[] values) {
        Map<String, Set<String>> map = new TreeMap<>();

        filter = filter == null ? "objectclass=*" : filter;
        values = values == null ? new String[]{"dn", "ou"} : values;

        if ("objectclass=*".equals(filter)) {
            List<String> v = Arrays.asList(values);
            if (v.isEmpty()) {
                map.put("mail", new TreeSet<>(Arrays.asList("james@bond.com")));
                map.put("ou", new TreeSet<>(Arrays.asList("MI6")));
            } else {
                for (String x : v) {
                    if (x.equals("uid")) {
                        map.put("uid", new TreeSet<>(Arrays.asList("bondjame")));
                    }
                }
            }
            return new LdapSearchResult<>("fakedn", map);
        }

        if (filter.contains("objectclass")) {
            map.put("dn", new TreeSet<>(Arrays.asList("cn=mi6,cn=mi6,cn=james,dc=bond,dc=com",
                    "cn=mi7,cn=mi7,cn=james,dc=bond,dc=com")));
        }

        return new LdapSearchResult<>("fakedn", map);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void close() {
    }

}
