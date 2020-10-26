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
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.authorization;

import java.io.Serializable;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Nameable;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.logger.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationEntity.class);

    /**
     * Predicate specialized for the the plugin decisions. The caller should
     * implement the <code>decision</code> method. Returning true if the plugin
     * allows the action or false when the plugin forbids the action.
     */
    public abstract static class PluginDecisionPredicate implements Predicate<IAuthorizationPlugin> {

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
    public abstract static class PluginSkippingPredicate implements Predicate<AuthorizationEntity> {

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
    /**
     * Hold current setup - merged with all ancestor's stacks.
     */
    protected transient Map<String, Object> currentSetup = new TreeMap<>();

    private Set<String> forProjects = new TreeSet<>();
    private Set<String> forGroups = new TreeSet<>();

    protected transient boolean working = true;

    public AuthorizationEntity() {
    }

    /**
     * Copy constructor for the entity.
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
    public abstract void load(Map<String, Object> parameters);

    /**
     * Unload this entity.
     *
     * @see IAuthorizationPlugin#unload()
     */
    public abstract void unload();

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
    public abstract boolean isAllowed(Nameable entity,
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
    public abstract boolean setPlugin(IAuthorizationPlugin plugin);

    /**
     * Perform a deep copy of the entity.
     *
     * @return the new instance of this entity
     */
    @Override
    public abstract AuthorizationEntity clone();

    /**
     * Print the entity hierarchy.
     *
     * @param prefix this prefix should be prepended to every line produced by
     * this entity
     * @param colorElement a possible element where any occurrence of %color%
     * will be replaced with a HTML HEX color representing this entity state.
     * @return the string containing this entity representation
     */
    public abstract String hierarchyToString(String prefix, String colorElement);

    /**
     * Get the value of {@code flag}.
     *
     * @return the value of flag
     */
    public AuthControlFlag getFlag() {
        return flag;
    }

    /**
     * Set the value of {@code flag}.
     *
     * @param flag new value of flag
     */
    public void setFlag(AuthControlFlag flag) {
        this.flag = flag;
    }

    /**
     * Set the value of {@code flag}.
     *
     * @param flag new value of flag
     */
    public void setFlag(String flag) {
        this.flag = AuthControlFlag.get(flag);
    }

    /**
     * Get the value of {@code name}.
     *
     * @return the value of name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Set the value of {@code name}.
     *
     * @param name new value of name
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the value of {@code setup}.
     *
     * @return the value of setup
     */
    public Map<String, Object> getSetup() {
        return setup;
    }

    /**
     * Set the value of {@code setup}.
     *
     * @param setup new value of setup
     */
    public void setSetup(Map<String, Object> setup) {
        this.setup = setup;
    }

    /**
     * Get the value of current setup.
     *
     * @return the value of current setup
     */
    public Map<String, Object> getCurrentSetup() {
        return currentSetup;
    }

    /**
     * Set the value of current setup.
     *
     * @param currentSetup new value of current setup
     */
    public void setCurrentSetup(Map<String, Object> currentSetup) {
        this.currentSetup = currentSetup;
    }

    /**
     * Get the value of {@code forProjects}.
     *
     * @return the value of forProjects
     */
    public Set<String> forProjects() {
        return getForProjects();
    }

    /**
     * Get the value of {@code forProjects}.
     *
     * @return the value of forProjects
     */
    public Set<String> getForProjects() {
        return forProjects;
    }

    /**
     * Set the value of {@code forProjects}.
     *
     * @param forProjects new value of forProjects
     */
    public void setForProjects(Set<String> forProjects) {
        this.forProjects = forProjects;
    }

    /**
     * Set the value of {@code forProjects}.
     *
     * @param project add this project into the set
     */
    public void setForProjects(String project) {
        this.forProjects.add(project);
    }

    /**
     * Set the value of {@code forProjects}.
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
     * Get the value of {@code forGroups}.
     *
     * @return the value of forGroups
     */
    public Set<String> forGroups() {
        return getForGroups();
    }

    /**
     * Get the value of {@code forGroups}.
     *
     * @return the value of forGroups
     */
    public Set<String> getForGroups() {
        return forGroups;
    }

    /**
     * Set the value of {@code forGroups}.
     *
     * @param forGroups new value of forGroups
     */
    public void setForGroups(Set<String> forGroups) {
        this.forGroups = forGroups;
    }

    /**
     * Set the value of {@code forGroups}.
     *
     * @param group add this group into the set
     */
    public void setForGroups(String group) {
        this.forGroups.add(group);
    }

    /**
     * Set the value of {@code forGroups}.
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
     * <li>issue a warning for non-existent groups</li>
     * <li>issue a warning for non-existent projects</li>
     * </ul>
     */
    protected void processTargetGroupsAndProjects() {
        Set<String> groups = new TreeSet<>();

        for (String x : forGroups()) {
            /**
             * Full group discovery takes place here. All projects/repositories
             * in the group are added into "forProjects" and all subgroups
             * (including projects/repositories) and parent groups (excluding
             * the projects/repositories) are added into "forGroups".
             *
             * If the group does not exist then a warning is issued.
             */
            Group g;
            if ((g = Group.getByName(x)) != null) {
                forProjects().addAll(g.getAllProjects().stream().map((t) -> t.getName()).collect(Collectors.toSet()));
                groups.addAll(g.getRelatedGroups().stream().map((t) -> t.getName()).collect(Collectors.toSet()));
                groups.add(x);
            } else {
                LOGGER.log(Level.WARNING, "Configured group \"{0}\" in forGroups section"
                        + " for name \"{1}\" does not exist",
                        new Object[]{x, getName()});
            }
        }
        setForGroups(groups);

        forProjects().removeIf((t) -> {
            /**
             * Check the existence of the projects and issue a warning if there
             * is no such project.
             */
            Project p;
            if ((p = Project.getByName(t)) == null) {
                LOGGER.log(Level.WARNING, "Configured project \"{0}\" in forProjects"
                        + " section for name \"{1}\" does not exist",
                        new Object[]{t, getName()});
                return true;
            }
            return false;
        });
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

    /**
     * Print the entity hierarchy.
     *
     * @return the string containing this entity representation
     */
    public String hierarchyToString() {
        return hierarchyToString("", "<span style=\"background-color: %color%;\"> </span>");
    }

    /**
     * Print the color element for this entity. Replace all occurrences of
     * %color% in the input string by the current state color in the HTML HEX
     * format.
     *
     * @param colorElement the string, possibly an HTML element, describing the
     * color (can use %color%) to inject the true color of this entity state.
     * @return the color element with filled color
     */
    protected String colorToString(String colorElement) {
        StringBuilder builder = new StringBuilder(colorElement.length() + 10);
        String tmp;
        try {
            // #66ff33 - green
            // #ff0000 - red
            tmp = colorElement.replaceAll("(?<!\\\\)%color(?<!\\\\)%", isWorking() ? "#66ff33" : "#ff0000");
            if (tmp.isEmpty()) {
                builder.append(" ");
            } else {
                builder.append(tmp);
            }
        } catch (PatternSyntaxException ex) {
            builder.append(" ");
        }
        return builder.toString();
    }

    /**
     * Print the basic information about this entity.
     *
     * @param prefix prepend this value to each line produced
     * @return the string containing the information.
     */
    protected String infoToString(String prefix) {
        StringBuilder builder = new StringBuilder(40);
        String flup = getFlag().toString().toUpperCase(Locale.ROOT);
        String nm = getName();
        builder.append(" ").append(flup).append(" '").append(nm).append("'");
        return builder.toString();
    }

    /**
     * Print the setup into a string.
     *
     * @param prefix prepend this value to each line produced
     * @return the string representing the entity setup
     */
    protected String setupToString(String prefix) {
        StringBuilder builder = new StringBuilder();
        if (!currentSetup.isEmpty()) {
            builder.append(prefix).append("      setup:\n");
            for (Entry<String, Object> entry : currentSetup.entrySet()) {
                builder.append(prefix)
                        .append("          ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * Print the targets for groups and projects into a string.
     *
     * @param prefix prepend this value to each line produced
     * @return the string representing targeted the groups and projects
     */
    protected String targetsToString(String prefix) {
        StringBuilder builder = new StringBuilder();
        if (forGroups().size() > 0) {
            builder.append(prefix).append("      only for groups:\n");
            for (String x : forGroups()) {
                builder.append(prefix).append("          ").append(x).append("\n");
            }
        }
        if (forProjects().size() > 0) {
            builder.append(prefix).append("      only for projects:\n");
            for (String x : forProjects()) {
                builder.append(prefix).append("          ").append(x).append("\n");
            }
        }
        return builder.toString();
    }
}
