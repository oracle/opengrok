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
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.entity;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class User {

    private String id;
    private String username;
    private Date cookieTimestamp;
    private boolean timeouted;
    private final Map<String, Object> attrs = new HashMap<>();

    public User(String username) {
        this.username = username;
    }

    /**
     * Construct User object.
     * @param username username
     * @param id user ID
     */
    public User(String username, String id) {
        this.username = username;
        this.id = id;
    }

    /**
     * Construct User object.
     * @param username username
     * @param id user ID
     * @param cookieTimestamp cookie time stamp
     * @param timeouted is the user timed out
     */
    public User(String username, String id, Date cookieTimestamp, boolean timeouted) {
        this(username, id);
        this.cookieTimestamp = cookieTimestamp;
        this.timeouted = timeouted;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Date getCookieTimestamp() {
        return cookieTimestamp;
    }

    public void setCookieTimestamp(Date cookieTimestamp) {
        this.cookieTimestamp = cookieTimestamp;
    }

    public boolean getTimeouted() {
        return isTimeouted();
    }

    public void setTimeouted(boolean timeouted) {
        this.timeouted = timeouted;
    }

    public boolean isTimeouted() {
        return timeouted;
    }

    /**
     * Implemented for the forced authentication as described in
     * <a href="https://docs.oracle.com/cd/B28196_01/idmanage.1014/b15997/mod_osso.htm#i1006381">mod_osso documentation</a>.
     *
     * @param forcedAuthDate the date of the forced authentication trigger
     * @param newLoginDate the date of the new login
     * @return true if login date was before forced auth date or cookie timestamp
     */
    public boolean isForcedTimeouted(Date forcedAuthDate, Date newLoginDate) {
        if (cookieTimestamp == null || forcedAuthDate == null || newLoginDate == null) {
            return true;
        }

        return newLoginDate.before(forcedAuthDate) || newLoginDate.before(cookieTimestamp);
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
    public Object setAttribute(String key, Object value) {
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

    @Override
    public String toString() {
        return "User{" + "id=" + id + ", username=" + username + ", cookieTimestamp=" + cookieTimestamp +
                ", timeouted=" + timeouted + ", attrs=" + attrs + '}';
    }
}
