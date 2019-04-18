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
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.ldap;

import java.util.Map;
import java.util.Set;

public abstract class AbstractLdapProvider {

    /**
     * Lookups user's records.
     *
     * @param dn LDAP DN
     * @return set of attributes for the user or null
     *
     * @see #lookupLdapContent(java.lang.String, java.lang.String)
     */
    public Map<String, Set<String>> lookupLdapContent(String dn) throws LdapException {
        return lookupLdapContent(dn, (String) null);
    }

    /**
     * Lookups user's records.
     *
     * @param dn LDAP DN
     * @param filter the LDAP filter
     * @return set of attributes for the user or null
     *
     * @see #lookupLdapContent(java.lang.String, java.lang.String, java.lang.String[])
     */
    public Map<String, Set<String>> lookupLdapContent(String dn, String filter) throws LdapException {
        return lookupLdapContent(dn, filter, null);
    }

    /**
     * Lookups user's records.
     *
     * @param dn LDAP DN
     * @param values match these LDAP value
     * @return set of attributes for the user or null
     *
     * @see #lookupLdapContent(java.lang.String, java.lang.String, java.lang.String[])
     */
    public Map<String, Set<String>> lookupLdapContent(String dn, String[] values) throws LdapException {
        return lookupLdapContent(dn, null, values);
    }

    /**
     * Lookups user's records.
     *
     * @param dn LDAP DN
     * @param filter the LDAP filter
     * @param values match these LDAP value
     * @return set of attributes for the user or null
     */
    public abstract Map<String, Set<String>> lookupLdapContent(String dn, String filter, String[] values) throws LdapException;

    /**
     * @return if the provider is correctly configured
     */
    public abstract boolean isConfigured();

    /**
     * Closes the LDAP provider.
     */
    public abstract void close();
}
