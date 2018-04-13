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
package opengrok.auth.plugin.ldap;

import java.util.Map;
import java.util.Set;
import opengrok.auth.plugin.entity.User;

public abstract class AbstractLdapProvider {

    /**
     * Lookups user's records - mail address - ou records - uid
     *
     * @param user find LDAP information about this user
     * @return set of important attributes for the user
     *
     * @see #lookupLdapContent(opengrok.auth.plugin.entity.User,
     * java.lang.String)
     */
    public Map<String, Set<String>> lookupLdapContent(User user) {
        // calling the lookupLdapContent(user, filter)
        return lookupLdapContent(user, (String) null);
    }

    /**
     * Lookups user's records - mail address - ou records - uid
     *
     * @param user find LDAP information about this user
     * @param filter the LDAP filter
     * @return set of important attributes for the user
     *
     * @see #lookupLdapContent(opengrok.auth.plugin.entity.User,
     * java.lang.String, java.lang.String[])
     */
    public Map<String, Set<String>> lookupLdapContent(User user, String filter) {
        return lookupLdapContent(user, filter, null);
    }

    /**
     * Lookups user's records - mail address - ou records - uid
     *
     * @param user find LDAP information about this user
     * @param values match these LDAP value
     * @return set of important attributes for the user
     *
     * @see #lookupLdapContent(opengrok.auth.plugin.entity.User,
     * java.lang.String, java.lang.String[])
     */
    public Map<String, Set<String>> lookupLdapContent(User user, String[] values) {
        return lookupLdapContent(user, null, values);
    }

    /**
     * Lookups user's records - mail address - ou records - uid
     *
     * @param user find LDAP information about this user
     * @param filter the LDAP filter
     * @param values match these LDAP value
     * @return set of important attributes for the user
     */
    abstract public Map<String, Set<String>> lookupLdapContent(User user, String filter, String[] values);

    /**
     * @return if the provider is correctly configured
     */
    abstract public boolean isConfigured();

    /**
     * Closes the LDAP provider.
     */
    abstract public void close();

}
