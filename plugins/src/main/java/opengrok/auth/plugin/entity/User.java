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
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Date getCookieTimestamp() {
        return cookieTimestamp;
    }

    public boolean getTimeouted() {
        return isTimeouted();
    }

    public boolean isTimeouted() {
        return timeouted;
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

    @Override
    public String toString() {
        return "User{" + "id=" + id + ", username=" + username + ", cookieTimestamp=" + cookieTimestamp +
                ", timeouted=" + timeouted + ", attrs=" + attrs + '}';
    }
}
