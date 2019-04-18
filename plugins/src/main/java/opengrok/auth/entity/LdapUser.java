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
package opengrok.auth.entity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * LDAP user represented as a set of attributes.
 *
 * @author Krystof Tulinger
 */
public class LdapUser implements Serializable {

    private String DN;
    private final Map<String, Set<String>> attributes;

    // Use default serial ID value. If the serialized form of the object
    // changes, feel free to start from 1L.
    private static final long serialVersionUID = 1161431688782569843L;

    public LdapUser() {
        this(null,null);
    }

    public LdapUser(String dn, Map<String, Set<String>> attrs) {
        this.DN = dn;

        if (attrs == null) {
            this.attributes = new HashMap<>();
        } else {
            this.attributes = attrs;
        }
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

    public void setDN(String dn) {
        this.DN = dn;
    }

    public String getDN() {
        return DN;
    }

    @Override
    public String toString() {
        return "LdapUser{DN=" + DN + ",attributes=" + attributes + '}';
    }
}
