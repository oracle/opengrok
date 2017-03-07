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
package org.opensolaris.opengrok.authorization;

import javax.servlet.http.HttpServletRequest;
import org.opensolaris.opengrok.authorization.AuthorizationCheck.AuthorizationRole;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;

/**
 * A wrapper for the plugin class delegating all plugin function to the
 * underlying plugin and containing some useful information about the plugin
 * itself - if it exists, if it is working.
 *
 * @author Krystof Tulinger
 */
public final class AuthorizationPluginWrapper {

    private AuthorizationCheck check;
    private IAuthorizationPlugin plugin;
    private boolean working = true;

    public AuthorizationPluginWrapper(AuthorizationCheck description, IAuthorizationPlugin plugin) {
        this.check = description;
        this.plugin = plugin;
    }

    public AuthorizationPluginWrapper(AuthorizationRole role, String classname, IAuthorizationPlugin plugin) {
        this(new AuthorizationCheck(role, classname), plugin);
    }

    /**
     * Check if the plugin exists and has not failed while loading.
     *
     * @return true if working, false otherwise
     */
    public boolean isWorking() {
        return working && hasPlugin();
    }

    public synchronized void setWorking() {
        working = true;
    }

    /**
     * Check if this plugin has failed during loading or is missing.
     *
     * This method has the same effect as !{@link isWorking()}.
     *
     * @return true if failed, true otherwise
     * @see #isWorking()
     */
    public boolean isFailed() {
        return !isWorking();
    }

    /**
     * Set this plugin as failed. This plugin will no more call the underlying
     * plugin isAllowed methods.
     *
     * @see IAuthorizationPlugin#isAllowed(HttpServletRequest, Group)
     * @see IAuthorizationPlugin#isAllowed(HttpServletRequest, Project)
     */
    public synchronized void setFailed() {
        working = false;
    }

    /**
     * Check if the plugin class was found for this plugin.
     *
     * @return true if was; false otherwise
     */
    public boolean hasPlugin() {
        return plugin != null;
    }

    /**
     * Check if this plugin is marked as required.
     *
     * @return true if is required; false otherwise
     */
    public boolean isRequired() {
        return check.getRole().equals(AuthorizationRole.REQUIRED);
    }

    /**
     * Check if this plugin is marked as sufficient.
     *
     * @return true if is sufficient; false otherwise
     */
    public boolean isSufficient() {
        return check.getRole().equals(AuthorizationRole.SUFFICIENT);
    }

    /**
     * Check if this plugin is marked as requisite.
     *
     * @return true if is requisite; false otherwise
     */
    public boolean isRequisite() {
        return check.getRole().equals(AuthorizationRole.REQUISITE);
    }

    /**
     * Call the load method on the underlying plugin if the plugin exists. Note
     * that the load method can throw any throwable from its body and it should
     * stop the application.
     *
     * @see IAuthorizationPlugin#load()
     */
    public synchronized void load() {
        if (!hasPlugin()) {
            setFailed();
            return;
        }
        plugin.load();
    }

    /**
     * Call the unload method on the underlying plugin if the plugin exists.
     * Note that the unload method can throw any throwable from its body and it
     * should stop the application.
     *
     * @see IAuthorizationPlugin#unload()
     */
    public synchronized void unload() {
        if (hasPlugin()) {
            plugin.unload();
        }
    }

    /**
     * Call the same method on the underlying plugin if and only if the plugin
     * is not marked as failed.
     *
     * @param request current http request
     * @param project a project to check
     * @return true if the plugin is not failed and the project is allowed;
     * false otherwise
     * @see #isFailed()
     * @see IAuthorizationPlugin#isAllowed(HttpServletRequest, Project)
     */
    public boolean isAllowed(HttpServletRequest request, Project project) {
        if (isFailed()) {
            return false;
        }
        return plugin.isAllowed(request, project);
    }

    /**
     * Call the same method on the underlying plugin if and only if the plugin
     * is not marked as failed.
     *
     *
     * @param request current http request
     * @param group a group to check
     * @return true if the plugin is not failed and the group is allowed; false
     * otherwise
     * @see #isFailed()
     * @see IAuthorizationPlugin#isAllowed(HttpServletRequest, Group)
     */
    public boolean isAllowed(HttpServletRequest request, Group group) {
        if (isFailed()) {
            return false;
        }
        return plugin.isAllowed(request, group);
    }

    /**
     * Set the value of plugin
     *
     * @param plugin new value of plugin
     */
    public synchronized void setPlugin(IAuthorizationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Get the value of classname
     *
     * @return the value of classname
     */
    public String getClassname() {
        return check.getClassname();
    }

    /**
     * Get the value of role
     *
     * @return the value of role
     */
    public AuthorizationRole getRole() {
        return check.getRole();
    }
}
