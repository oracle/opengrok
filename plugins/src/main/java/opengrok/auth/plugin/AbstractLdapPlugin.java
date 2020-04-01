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
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import opengrok.auth.plugin.configuration.Configuration;
import opengrok.auth.plugin.entity.User;
import opengrok.auth.plugin.ldap.AbstractLdapProvider;
import opengrok.auth.plugin.ldap.LdapFacade;
import org.opengrok.indexer.authorization.IAuthorizationPlugin;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

/**
 * Abstract class for all plug-ins working with LDAP. Takes care of
 * <p><ul>
 * <li>controlling the established session</li>
 * <li>controlling if the session belongs to the user</li>
 * </ul>
 *
 * <p>
 * The intended methods to implement are the
 * {@link #checkEntity(HttpServletRequest, Project)} and
 * {@link #checkEntity(HttpServletRequest, Group)}.
 *
 * @author Krystof Tulinger
 */
public abstract class AbstractLdapPlugin implements IAuthorizationPlugin {

    /**
     * This is used to ensure that every instance of this plug-in has its own
     * unique name for its session parameters.
     */
    public static long nextId = 1;

    protected static final String CONFIGURATION_PARAM = "configuration";

    private static final String SESSION_PREFIX = "opengrok-abstract-ldap-plugin-";
    protected String SESSION_USERNAME = SESSION_PREFIX + "username";
    protected String SESSION_ESTABLISHED = SESSION_PREFIX + "session-established";

    /**
     * Configuration for the LDAP servers.
     */
    private Configuration cfg;

    /**
     * Map of currently used configurations.<br>
     * file path => object.
     */
    private static final Map<String, Configuration> LOADED_CONFIGURATIONS = new ConcurrentHashMap<>();

    /**
     * LDAP lookup facade.
     */
    private AbstractLdapProvider ldapProvider;

    public AbstractLdapPlugin() {
        SESSION_USERNAME += "-" + nextId;
        SESSION_ESTABLISHED += "-" + nextId;
        nextId++;
    }

    /**
     * Fill the session with some information related to the subclass.
     *
     * @param req the current request
     * @param user user decoded from the headers
     */
    public abstract void fillSession(HttpServletRequest req, User user);

    /**
     * Decide if the project should be allowed for this request.
     *
     * @param request the request
     * @param project the project
     * @return true if yes; false otherwise
     */
    public abstract boolean checkEntity(HttpServletRequest request, Project project);

    /**
     * Decide if the group should be allowed for this request.
     *
     * @param request the request
     * @param group the group
     * @return true if yes; false otherwise
     */
    public abstract boolean checkEntity(HttpServletRequest request, Group group);

    // for testing
    void load(AbstractLdapProvider provider) {
        ldapProvider = provider;
    }

    /**
     * Loads the configuration into memory.
     */
    @Override
    public void load(Map<String, Object> parameters) {
        String configurationPath;

        if ((configurationPath = (String) parameters.get(CONFIGURATION_PARAM)) == null) {
            throw new NullPointerException("Missing param [" + CONFIGURATION_PARAM + "]");
        }

        try {
            cfg = getConfiguration(configurationPath);
            ldapProvider = new LdapFacade(cfg);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read the configuration", ex);
        }
    }

    /**
     * Return the configuration for the given path. If the configuration is
     * already loaded, use that one. Otherwise try to load the file into the
     * configuration.
     *
     * @param configurationPath the path to the file with the configuration
     * @return the object (new or from cache)
     * @throws IOException when any IO error occurs
     */
    protected Configuration getConfiguration(String configurationPath) throws IOException {
        if ((cfg = LOADED_CONFIGURATIONS.get(configurationPath)) == null) {
            LOADED_CONFIGURATIONS.put(configurationPath, cfg =
                    Configuration.read(new File(configurationPath)));
        }
        return cfg;
    }

    /**
     * Closes the LDAP connections.
     */
    @Override
    public void unload() {
        if (ldapProvider != null) {
            ldapProvider.close();
            ldapProvider = null;
        }
        cfg = null;
    }

    /**
     * Return the configuration object.
     *
     * @return the configuration
     */
    public Configuration getConfiguration() {
        return cfg;
    }

    /**
     * Return the LDAP provider.
     *
     * @return the LDAP provider
     */
    public AbstractLdapProvider getLdapProvider() {
        return ldapProvider;
    }

    /**
     * Check if the session user corresponds to the authenticated user.
     *
     * @param sessionUsername user from the session
     * @param authUser user from the request
     * @return true if it does; false otherwise
     */
    protected boolean isSameUser(String sessionUsername, String authUser) {
        return sessionUsername != null
                && sessionUsername.equals(authUser);
    }

    /**
     * Check if the session exists and contains all necessary fields required by
     * this plug-in.
     *
     * @param req the HTTP request
     * @return true if it does; false otherwise
     */
    protected boolean sessionExists(HttpServletRequest req) {
        return req != null && req.getSession() != null
                && req.getSession().getAttribute(SESSION_ESTABLISHED) != null
                && req.getSession().getAttribute(SESSION_USERNAME) != null;
    }

    /**
     * Ensures that after the call the session for the user will be created with
     * appropriate fields. If any error occurs during the call which might be:
     * <ul>
     * <li>The user has not been authenticated</li>
     * <li>The user can not be retrieved from LDAP</li>
     * <li>There are no records for authorization for the user</li>
     * </ul>
     * the session is established as an empty session to avoid any exception in
     * the caller.
     *
     * @param req the HTTP request
     */
    private void ensureSessionExists(HttpServletRequest req) {
        if (req.getSession() == null) {
            // old/invalid request (should not happen)
            return;
        }
        
        // The cast to User should not be problem as this object is stored
        // in the request itself (as opposed to in the session).
        User user;
        if ((user = (User) req.getAttribute(UserPlugin.REQUEST_ATTR)) == null) {
            updateSession(req, null, false);
            return;
        }

        if (sessionExists(req)
                // we've already filled the groups and projects
                && (boolean) req.getSession().getAttribute(SESSION_ESTABLISHED)
                // the session belongs to the user from the request
                && isSameUser((String) req.getSession().getAttribute(SESSION_USERNAME), user.getUsername())) {
            /**
             * The session is already filled so no need to
             * {@link #updateSession()}
             */
            return;
        }

        updateSession(req, user.getUsername(), false);

        if (ldapProvider == null) {
            return;
        }

        fillSession(req, user);

        updateSession(req, user.getUsername(), true);
    }

    /**
     * Fill the session with new values.
     *
     * @param req the request
     * @param username new username
     * @param established new value for established
     */
    protected void updateSession(HttpServletRequest req,
            String username,
            boolean established) {
        setSessionEstablished(req, established);
        setSessionUsername(req, username);
    }

    /**
     * Set session established flag into the session.
     *
     * @param req request containing the session
     * @param value the value
     */
    protected void setSessionEstablished(HttpServletRequest req, Boolean value) {
        req.getSession().setAttribute(SESSION_ESTABLISHED, value);
    }

    /**
     * Set session username for the user.
     *
     * @param req request containing the session
     * @param value the value
     */
    protected void setSessionUsername(HttpServletRequest req, String value) {
        req.getSession().setAttribute(SESSION_USERNAME, value);
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Project project) {
        ensureSessionExists(request);

        if (request.getSession() == null) {
            return false;
        }

        return checkEntity(request, project);
    }

    @Override
    public boolean isAllowed(HttpServletRequest request, Group group) {
        ensureSessionExists(request);

        if (request.getSession() == null) {
            return false;
        }

        return checkEntity(request, group);
    }
}
