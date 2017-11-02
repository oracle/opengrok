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
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;
import javax.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;

public class LdapFilter extends AbstractLdapPlugin {

    private static final Logger LOGGER = Logger.getLogger(LdapFilter.class.getName());

    protected static final String FILTER_PARAM = "filter";
    protected String SESSION_ALLOWED = "opengrok-filter-plugin-allowed";

    private String ldapFilter;

    public LdapFilter() {
        SESSION_ALLOWED += "-" + nextId++;
    }

    @Override
    public void load(Map<String, Object> parameters) {
        super.load(parameters);

        if ((ldapFilter = (String) parameters.get(FILTER_PARAM)) == null) {
            throw new NullPointerException("Missing param [" + FILTER_PARAM + "] in the setup");
        }
    }

    @Override
    protected boolean sessionExists(HttpServletRequest req) {
        return super.sessionExists(req)
                && req.getSession().getAttribute(SESSION_ALLOWED) != null;
    }

    @Override
    public void fillSession(HttpServletRequest req, User user) {
        Boolean sessionAllowed = false;
        LdapUser ldapUser;
        Map<String, Set<String>> records;

        updateSession(req, sessionAllowed);

        if ((ldapUser = (LdapUser) req.getSession().getAttribute(LdapUserPlugin.SESSION_ATTR)) == null) {
            return;
        }

        if (ldapUser.getUid() == null) {
            return;
        }

        if ((records = getLdapProvider().lookupLdapContent(user,
                expandFilter(ldapFilter, ldapUser, user))) == null) {
            return;
        }

        if (records.isEmpty()) {
            return;
        }

        sessionAllowed = true;

        updateSession(req, sessionAllowed);
    }

    /**
     * Insert the special values into the filter.
     *
     * Special values are:
     * <ul>
     * <li>%uid% - to be replaced with LDAP uid value</li>
     * <li>%mail% - to be replaced with LDAP mail value</li>
     * <li>%username% - to be replaced with OSSO user value</li>
     * <li>%guid% - to be replaced with OSSO guid value</li>
     * </ul>
     *
     * Use \% for printing the '%̈́' character.
     *
     * @param filter basic filter containing the special values
     * @param ldapUser user from LDAP
     * @param user user from the request
     * @return replaced result
     */
    protected String expandFilter(String filter, LdapUser ldapUser, User user) {
        filter = filter.replaceAll("(?<!\\\\)%uid(?<!\\\\)%", ldapUser.getUid());
        filter = filter.replaceAll("(?<!\\\\)%mail(?<!\\\\)%", ldapUser.getMail());
        filter = filter.replaceAll("(?<!\\\\)%username(?<!\\\\)%", user.getUsername());
        filter = filter.replaceAll("(?<!\\\\)%guid(?<!\\\\)%", user.getId());
        for (Entry<String, Set<String>> entry : ldapUser.getAttributes().entrySet()) {
            if (entry.getValue().size() == 1) {
                try {
                    filter = filter.replaceAll(
                            "(?<!\\\\)%" + entry.getKey() + "(?<!\\\\)%",
                            entry.getValue().iterator().next());
                } catch (PatternSyntaxException ex) {
                    LOGGER.log(Level.WARNING, "The pattern for expanding is not valid", ex);
                }
            }

        }
        filter = filter.replaceAll("\\\\%", "%");
        return filter;
    }

    /**
     * Add a new allowed value into the session.
     *
     * @param req the request
     * @param allowed the new value
     */
    protected void updateSession(HttpServletRequest req, boolean allowed) {
        req.getSession().setAttribute(SESSION_ALLOWED, allowed);
    }

    @Override
    public boolean checkEntity(HttpServletRequest request, Project project) {
        return ((Boolean) request.getSession().getAttribute(SESSION_ALLOWED));
    }

    @Override
    public boolean checkEntity(HttpServletRequest request, Group group) {
        return ((Boolean) request.getSession().getAttribute(SESSION_ALLOWED));
    }
}
