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
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.entity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * LDAP user represented as Distinguished Name and a set of attributes.
 *
 * @author Krystof Tulinger
 */
public class LdapUser implements Serializable {

    private String dn; // Distinguished Name
    private final transient Map<String, Set<String>> attributes;

    private static final long serialVersionUID = 1L;

    public LdapUser() {
        this(null, null);
    }

    public LdapUser(String dn, Map<String, Set<String>> attrs) {
        this.dn = dn;
        this.attributes = Objects.requireNonNullElseGet(attrs, HashMap::new);
    }

    /**
     * Set attribute value.
     *
     * @param key the key
     * @param value set of values
     * @return the value previously associated with the key
     */
    public Object setAttribute(String key, Set<String> value) {
        return attributes.put(key, value);
    }

    public Set<String> getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, Set<String>> getAttributes() {
        return this.attributes;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getDn() {
        return dn;
    }

    @Override
    public String toString() {
        return "LdapUser{dn=" + dn + "; attributes=" + attributes + '}';
    }
}
