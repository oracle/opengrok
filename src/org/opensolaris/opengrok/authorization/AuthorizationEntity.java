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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Nameable;

/**
 * This class covers authorization entities used in opengrok.
 *
 * Currently there are two:
 * <ul>
 * <li>stack of plugins</li>
 * <li>plugin</li>
 * </ul>
 *
 * The purpose is to extract common member variables and methods into an class,
 * namely:
 * <ul>
 * <li>name</li>
 * <li>role - sufficient/required/requisite</li>
 * <li>state - working/failed</li>
 * <li>setup - from configuration</li>
 * </ul>
 * and let the subclasses implement the important abstract methods.
 *
 * This class is intended to be read from a configuration.
 *
 * @author Krystof Tulinger
 */
public abstract class AuthorizationEntity implements Nameable, Serializable, Cloneable {

    /**
     * Predicate specialized for the the plugin decisions. The caller should
     * implement the <code>decision</code> method. Returning true if the plugin
     * allows the action or false when the plugin forbids the action.
     */
    public static abstract class PluginDecisionPredicate implements Predicate<IAuthorizationPlugin> {

        @Override
        public boolean test(IAuthorizationPlugin t) {
            return decision(t);
        }

        /**
         * Perform the authorization check for this plugin.
         *
         * @param t the plugin
         * @return true if plugin allows the action; false otherwise
         */
        public abstract boolean decision(IAuthorizationPlugin t);

    }

    /**
     * Predicate specialized for the the entity skipping decisions. The caller
     * should implement the <code>shouldSkip</code> method. Returning true if
     * the entity should be skipped for this action and false if the entity
     * should be used.
     */
    public static abstract class PluginSkippingPredicate implements Predicate<AuthorizationEntity> {

        @Override
        public boolean test(AuthorizationEntity t) {
            return shouldSkip(t);
        }

        /**
         * Decide if the entity should be skipped in this step of authorization.
         *
         * @param t the entity
         * @return true if skipped (authorization decision will not be affected
         * by this entity) or false if it should be used (authorization decision
         * will be affected by this entity)
         */
        public abstract boolean shouldSkip(AuthorizationEntity t);
    }

    private static final long serialVersionUID = 1L;
    /**
     * One of "required", "requisite", "sufficient".
     */
    protected AuthControlFlag flag;
    protected String name;
    protected Map<String, Object> setup = new TreeMap<>();

    private Set<String> forProjects = new TreeSet<>();
    private Set<String> forGroups = new TreeSet<>();

    protected transient boolean working = true;

    public AuthorizationEntity() {
    }

    /**
     * Copy constructor for the entity:
     * <ul>
     * <li>copy flag</li>
     * <li>copy name</li>
     * <li>deep copy of the setup</li>
     * <li>copy the working attribute</li>
     * </ul>
     *
     * @param x the entity to be copied
     */
    public AuthorizationEntity(AuthorizationEntity x) {
        flag = x.flag;
        name = x.name;
        setup = new TreeMap<>(x.setup);
        working = x.working;
        forGroups = new TreeSet<>(x.forGroups);
        forProjects = new TreeSet<>(x.forProjects);
    }

    public AuthorizationEntity(AuthControlFlag flag, String name) {
        this.flag = flag;
        this.name = name;
    }

    /**
     * Load this entity with given parameters.
     *
     * @param parameters given parameters passed to the plugin's load method
     *
     * @see IAuthorizationPlugin#load(java.util.Map)
     */
    abstract public void load(Map<String, Object> parameters);

    /**
     * Unload this entity.
     *
     * @see IAuthorizationPlugin#unload()
     */
    abstract public void unload();

    /**
     * Test the given entity if it should be allowed with this authorization
     * check.
     *
     * @param entity the given entity - this is either group or project and is
     * passed just for the logging purposes.
     * @param pluginPredicate predicate returning true or false for the given
     * entity which determines if the authorization for such entity is
     * successful or failed
     * @param skippingPredicate predicate returning true if this authorization
     * entity should be omitted from the authorization process
     * @return true if successful; false otherwise
     */
    abstract public boolean isAllowed(Nameable entity,
            PluginDecisionPredicate pluginPredicate,
            PluginSkippingPredicate skippingPredicate);

    /**
     * Set the plugin to all classes which requires this class in the
     * configuration. This creates a new instance of the plugin for each class
     * which needs it.
     *
     * @param plugin the new instance of a plugin
     * @return true if there is such case; false otherwise
     */
    abstract public boolean setPlugin(IAuthorizationPlugin plugin);

    /**
     * Perform a deep copy of the entity.
     *
     * @return the new instance of this entity
     */
    @Override
    abstract public AuthorizationEntity clone();

    /**
     * Get the value of flag
     *
     * @return the value of flag
     */
    public AuthControlFlag getFlag() {
        return flag;
    }

    /**
     * Set the value of flag
     *
     * @param flag new value of flag
     */
    public void setFlag(AuthControlFlag flag) {
        this.flag = flag;
    }

    /**
     * Set the value of flag
     *
     * @param flag new value of flag
     */
    public void setFlag(String flag) {
        this.flag = AuthControlFlag.get(flag);
    }

    /**
     * Get the value of name
     *
     * @return the value of name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Set the value of name
     *
     * @param name new value of name
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the value of setup
     *
     * @return the value of setup
     */
    public Map<String, Object> getSetup() {
        return setup;
    }

    /**
     * Set the value of setup
     *
     * @param setup new value of setup
     */
    public void setSetup(Map<String, Object> setup) {
        this.setup = setup;
    }

    /**
     * Get the value of forProjects
     *
     * @return the value of forProjects
     */
    public Set<String> forProjects() {
        return getForProjects();
    }

    /**
     * Get the value of forProjects
     *
     * @return the value of forProjects
     */
    public Set<String> getForProjects() {
        return forProjects;
    }

    /**
     * Set the value of forProjects
     *
     * @param forProjects new value of forProjects
     */
    public void setForProjects(Set<String> forProjects) {
        this.forProjects = forProjects;
    }

    /**
     * Set the value of forProjects
     *
     * @param project add this project into the set
     */
    public void setForProjects(String project) {
        this.forProjects.add(project);
    }

    /**
     * Set the value of forProjects
     *
     * @param projects add all projects in this array into the set
     *
     * @see #setForProjects(java.lang.String)
     */
    public void setForProjects(String[] projects) {
        for (String project : projects) {
            setForProjects(project);
        }
    }

    /**
     * Get the value of forGroups
     *
     * @return the value of forGroups
     */
    public Set<String> forGroups() {
        return getForGroups();
    }

    /**
     * Get the value of forGroups
     *
     * @return the value of forGroups
     */
    public Set<String> getForGroups() {
        return forGroups;
    }

    /**
     * Set the value of forGroups
     *
     * @param forGroups new value of forGroups
     */
    public void setForGroups(Set<String> forGroups) {
        this.forGroups = forGroups;
    }

    /**
     * Set the value of forGroups
     *
     * @param group add this group into the set
     */
    public void setForGroups(String group) {
        this.forGroups.add(group);
    }

    /**
     * Set the value of forGroups
     *
     * @param groups add all groups in this array into the set
     *
     * @see #setForGroups(java.lang.String)
     */
    public void setForGroups(String[] groups) {
        for (String group : groups) {
            setForGroups(group);
        }
    }

    /**
     * Discover all targeted groups and projects for every group given by
     * {@link #forGroups()}.
     *
     * <ul>
     * <li>add to the {@link #forGroups()} all groups which are descendant
     * groups to the group</li>
     * <li>add to the {@link #forGroups()} all groups which are parent groups to
     * the group</li>
     * <li>add to the {@link #forProjects()} all projects and repositories which
     * are in the descendant groups or in the group itself</li>
     * </ul>
     */
    protected void discoverGroups() {
        Set<String> groups = new TreeSet<>();
        for (String x : forGroups()) {
            /**
             * Full group discovery takes place here. All projects/repositories
             * in the group are added into "forProjects" and all subgroups
             * (including projects/repositories) and parent groups (excluding
             * the projects/repositories) are added into "forGroups".
             */
            Group g;
            if ((g = Group.getByName(x)) != null) {
                forProjects().addAll(g.getAllProjects().stream().map((t) -> t.getName()).collect(Collectors.toSet()));
                groups.addAll(g.getRelatedGroups().stream().map((t) -> t.getName()).collect(Collectors.toSet()));
                groups.add(x);
            }
        }
        setForGroups(groups);
    }

    /**
     * Check if the plugin exists and has not failed while loading.
     *
     * @return true if working, false otherwise
     */
    public boolean isWorking() {
        return working;
    }

    /**
     * Mark this entity as working.
     */
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
     * Check if this plugin is marked as required.
     *
     * @return true if is required; false otherwise
     */
    public boolean isRequired() {
        return getFlag().isRequired();
    }

    /**
     * Check if this plugin is marked as sufficient.
     *
     * @return true if is sufficient; false otherwise
     */
    public boolean isSufficient() {
        return getFlag().isSufficient();
    }

    /**
     * Check if this plugin is marked as requisite.
     *
     * @return true if is requisite; false otherwise
     */
    public boolean isRequisite() {
        return getFlag().isRequisite();
    }
}
