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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.util.StringUtils;

/**
 * Authorization plug-in to extract user's LDAP attributes.
 *
 * @author Krystof Tulinger
 */
public class LdapUserPlugin extends AbstractLdapPlugin {

    private static final Logger LOGGER = Logger.getLogger(LdapUserPlugin.class.getName());
    
    public static final String SESSION_ATTR = "opengrok-ldap-plugin-user";
    protected static final String OBJECT_CLASS = "objectclass";
    
    private String objectClass;
    private final Pattern usernameCnPattern = Pattern.compile("(cn=[a-zA-Z0-9_-]+)");

    @Override
    public void load(Map<String, Object> parameters) {
        super.load(parameters);

        if ((objectClass = (String) parameters.get(OBJECT_CLASS)) == null) {
            throw new NullPointerException("Missing param [" + OBJECT_CLASS +
                    "] in the setup");
        }

        if (!StringUtils.isAlphanumeric(objectClass)) {
            throw new NullPointerException("object class '" + objectClass +
                    "' contains non-alphanumeric characters");
        }
    
        LOGGER.log(Level.FINE, "LdapUser plugin loaded with objectclass={0}",
                objectClass);
    }
    
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

    protected String getFilter(User user) {
        String filter = null;
        String commonName;

        Matcher matcher = usernameCnPattern.matcher(user.getUsername());
        if (matcher.find()) {
            commonName = matcher.group(1);
            LOGGER.log(Level.FINEST, "extracted common name {0} from {1}",
                new Object[]{commonName, user.getUsername()});
        } else {
            LOGGER.log(Level.WARNING, "cannot get common name out of {0}",
                    user.getUsername());
            return filter;
        }
        
        filter = "(&(objectclass=" + this.objectClass + ")(" + commonName + "))";
        
        return filter;
    }
    
    @Override
    public void fillSession(HttpServletRequest req, User user) {
        Map<String, Set<String>> records;
        
        updateSession(req, null);

        if (getLdapProvider() == null) {
            return;
        }

        String filter = getFilter(user);
        if ((records = getLdapProvider().lookupLdapContent(null, filter,
                new String[]{"uid", "mail", "ou"})) == null) {
            LOGGER.log(Level.WARNING, "failed to get LDAP contents for user ''{0}'' with filter ''{1}''",
                    new Object[]{user, filter});
            return;
        }

        if (records.isEmpty()) {
            LOGGER.log(Level.WARNING, "LDAP records for user {0} are empty",
                    user);
            return;
        }

        if (!records.containsKey("uid") || records.get("uid").isEmpty()) {
            LOGGER.log(Level.WARNING, "uid record for user {0} is not present or empty",
                    user);
            return;
        }

        if (!records.containsKey("mail") || records.get("mail").isEmpty()) {
            LOGGER.log(Level.WARNING, "mail record for user {0} is not present or empty",
                    user);
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
