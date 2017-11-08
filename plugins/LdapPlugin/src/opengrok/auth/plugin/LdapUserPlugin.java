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
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;

/**
 * Authorization plug-in to extract user's LDAP attributes.
 *
 * @author Krystof Tulinger
 */
public class LdapUserPlugin extends AbstractLdapPlugin {

    public static final String SESSION_ATTR = "opengrok-ldap-plugin-user";

    /**
     * Check if the session exists and contains all necessary fields required by
     * this plug-in.
     *
     * @param req the HTTP request
     * @return true if it does; false otherwise
     */
    @Override
    protected boolean sessionExists(HttpServletRequest req) {
        return super.sessionExists(req)
                && req.getSession().getAttribute(SESSION_ATTR) != null;
    }

    @Override
    public void fillSession(HttpServletRequest req, User user) {
        Map<String, Set<String>> records;
        updateSession(req, null);

        if (getLdapProvider() == null) {
            return;
        }

        if ((records = getLdapProvider().lookupLdapContent(user, new String[]{"uid", "mail", "ou"})) == null) {
            return;
        }

        if (records.isEmpty()
                || !records.containsKey("uid")
                || !records.containsKey("mail")) {
            return;
        }

        if (records.get("uid").isEmpty() || records.get("mail").isEmpty()) {
            return;
        }

        updateSession(req, new LdapUser(
                records.get("mail").iterator().next(),
                records.get("uid").iterator().next(),
                records.get("ou")));
    }

    /**
     * Add a new user value into the session.
     *
     * @param req the request
     * @param user the new value for user
     */
    protected void updateSession(HttpServletRequest req, LdapUser user) {
        req.getSession().setAttribute(SESSION_ATTR, user);
    }

    @Override
    public boolean checkEntity(HttpServletRequest request, Project project) {
        return request.getSession().getAttribute(SESSION_ATTR) != null;
    }

    @Override
    public boolean checkEntity(HttpServletRequest request, Group group) {
        return request.getSession().getAttribute(SESSION_ATTR) != null;
    }
}
