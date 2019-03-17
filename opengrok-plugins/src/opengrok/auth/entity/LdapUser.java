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
import java.util.TreeSet;

/**
 *
 * @author Krystof Tulinger
 */
public class LdapUser implements Serializable {

    private String mail;
    private String uid;
    private Set<String> ou;
    private final Map<String, Set<String>> attrs = new HashMap<>();

    // Use default serial ID value. If the serialized form of the object
    // changes, feel free to start from 1L.
    private static final long serialVersionUID = -8207597677599370334L;

    public LdapUser(String mail, String uid, Set<String> ou) {
        this.mail = mail;
        this.uid = uid;
        this.ou = ou == null ? new TreeSet<>() : ou;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Set<String> getOu() {
        return ou;
    }

    public void setOu(Set<String> ou) {
        this.ou = ou == null ? new TreeSet<>() : ou;
    }

    public boolean hasOu(String name) {
        return this.ou.contains(name);
    }

    /**
     * Get custom user property.
     *
     * @param key the key
     * @return the the value associated with the key
     */
    public Object getAttribute(String key) {
        return attrs.get(key);
    }

    /**
     * Set custom user property.
     *
     * @param key the key
     * @param value the value
     * @return the value previously associated with the key
     */
    public Object setAttribute(String key, Set<String> value) {
        return attrs.put(key, value);
    }

    /**
     * Remote custom user property.
     *
     * @param key the key
     * @return the value previously associated with the key
     */
    public Object removeAttribute(String key) {
        return attrs.remove(key);
    }

    public Map<String, Set<String>> getAttributes() {
        return this.attrs;
    }

    @Override
    public String toString() {
        return "LdapUser{" + "mail=" + mail + ", uid=" + uid + ", ou=" + ou + '}';
    }
}
