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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import opengrok.auth.entity.LdapUser;
import opengrok.auth.plugin.entity.User;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Project;

/**
 * Authorization plug-in to check user's LDAP attribute against whitelist.
 *
 * @author Krystof Tulinger
 */
public class LdapAttrPlugin extends AbstractLdapPlugin {

    protected static final String ATTR_PARAM = "attribute";
    protected static final String FILE_PARAM = "file";

    private static final String SESSION_ALLOWED_PREFIX = "opengrok-attr-plugin-allowed";
    private String sessionAllowed = SESSION_ALLOWED_PREFIX;

    private String ldapAttr;
    private final Set<String> whitelist = new TreeSet<>();

    public LdapAttrPlugin() {
        sessionAllowed += "-" + nextId++;
    }

    @Override
    public void load(Map<String, Object> parameters) {
        super.load(parameters);
        String filePath;

        if ((ldapAttr = (String) parameters.get(ATTR_PARAM)) == null) {
            throw new NullPointerException("Missing param [" + ATTR_PARAM + "] in the setup");
        }

        if ((filePath = (String) parameters.get(FILE_PARAM)) == null) {
            throw new NullPointerException("Missing param [" + FILE_PARAM + "] in the setup");
        }

        try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
            stream.forEach(whitelist::add);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Unable to read the file \"%s\"", filePath), e);
        }
    }

    @Override
    protected boolean sessionExists(HttpServletRequest req) {
        return super.sessionExists(req)
                && req.getSession().getAttribute(sessionAllowed) != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void fillSession(HttpServletRequest req, User user) {
        Boolean sessionAllowed = false;
        LdapUser ldapUser;
        Map<String, Set<String>> records;
        Set<String> attributes;

        updateSession(req, sessionAllowed);

        if ((ldapUser = (LdapUser) req.getSession().getAttribute(LdapUserPlugin.SESSION_ATTR)) == null) {
            return;
        }

        if (ldapAttr.equals("mail")) {
            sessionAllowed = whitelist.contains(ldapUser.getMail());
        } else if (ldapAttr.equals("uid")) {
            sessionAllowed = whitelist.contains(ldapUser.getUid());
        } else if (ldapAttr.equals("ou")) {
            sessionAllowed = ldapUser.getOu().stream().anyMatch((t) -> whitelist.contains(t));
        } else if ((attributes = (Set<String>) ldapUser.getAttribute(ldapAttr)) != null) {
            sessionAllowed = attributes.stream().anyMatch((t) -> whitelist.contains(t));
        } else {
            if ((records = getLdapProvider().lookupLdapContent(user, new String[]{ldapAttr})) == null) {
                return;
            }

            if (records.isEmpty() || (attributes = records.get(ldapAttr)) == null) {
                return;
            }

            ldapUser.setAttribute(ldapAttr, attributes);
            sessionAllowed = attributes.stream().anyMatch((t) -> whitelist.contains(t));
        }

        updateSession(req, sessionAllowed);
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
