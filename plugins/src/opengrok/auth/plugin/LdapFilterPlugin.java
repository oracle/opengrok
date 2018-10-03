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
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

/**
 * Authorization plug-in to check if given user matches configured LDAP filter.
 *
 * @author Krystof Tulinger
 */
public class LdapFilterPlugin extends AbstractLdapPlugin {

    private static final Logger LOGGER = Logger.getLogger(LdapFilterPlugin.class.getName());

    protected static final String FILTER_PARAM = "filter";
    private static final String SESSION_ALLOWED_PREFIX = "opengrok-filter-plugin-allowed";
    private String sessionAllowed = SESSION_ALLOWED_PREFIX;

    private String ldapFilter;

    public LdapFilterPlugin() {
        sessionAllowed += "-" + nextId++;
    }

    @Override
    public void load(Map<String, Object> parameters) {
        super.load(parameters);

        if ((ldapFilter = (String) parameters.get(FILTER_PARAM)) == null) {
            throw new NullPointerException("Missing param [" + FILTER_PARAM + "] in the setup");
        }
        LOGGER.log(Level.FINE, "LdapFilter plugin loaded");
    }

    @Override
    protected boolean sessionExists(HttpServletRequest req) {
        return super.sessionExists(req)
                && req.getSession().getAttribute(sessionAllowed) != null;
    }

    @Override
    public void fillSession(HttpServletRequest req, User user) {
        Boolean sessionAllowed = false;
        LdapUser ldapUser;
        Map<String, Set<String>> records;
        String dn[] = {"dn"};

        updateSession(req, sessionAllowed);

        if ((ldapUser = (LdapUser) req.getSession().getAttribute(LdapUserPlugin.SESSION_ATTR)) == null) {
            LOGGER.log(Level.FINER, "failed to get attribute " + LdapUserPlugin.SESSION_ATTR);
            return;
        }

        if (ldapUser.getUid() == null) {
            LOGGER.log(Level.FINER, "failed to get uid");
            return;
        }

        String expandedFilter = expandFilter(ldapFilter, ldapUser, user);
        LOGGER.log(Level.FINER, "expanded filter for user {0} into ''{1}''",
                new Object[]{user, expandedFilter});
        if ((records = getLdapProvider().lookupLdapContent(null, expandedFilter, dn)) == null) {
            LOGGER.log(Level.FINER, "failed to get content for user from LDAP server");
            return;
        }

        LOGGER.log(Level.FINER, "got {0} records", records.size());
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
     * Use \% for printing the '%' character.
     * Also replaces any other LDAP attribute that would not be ambiguous.
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
        req.getSession().setAttribute(sessionAllowed, allowed);
    }

    @Override
    public boolean checkEntity(HttpServletRequest request, Project project) {
        return ((Boolean) request.getSession().getAttribute(sessionAllowed));
    }

    @Override
    public boolean checkEntity(HttpServletRequest request, Group group) {
        return ((Boolean) request.getSession().getAttribute(sessionAllowed));
    }
}
