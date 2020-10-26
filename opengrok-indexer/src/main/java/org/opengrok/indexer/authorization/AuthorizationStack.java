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
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.authorization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opengrok.indexer.configuration.Nameable;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Subclass of {@link AuthorizationEntity}. It implements the methods to
 * be able to contain and making decision for:
 * <ul>
 * <li>other stacks</li>
 * <li>plugins</li>
 * </ul>
 *
 * @author Krystof Tulinger
 */
public class AuthorizationStack extends AuthorizationEntity {

    private static final long serialVersionUID = -2116160303238347415L;

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationStack.class);

    private List<AuthorizationEntity> stack = new ArrayList<>();

    public AuthorizationStack() {
    }

    /**
     * Copy constructor from another stack.
     * <ul>
     * <li>copy the superclass {@link AuthorizationEntity}</li>
     * <li>perform a deep copy of the contained stack (using
     * {@link AuthorizationEntity#clone()}</li>
     * </ul>
     *
     * @param x the stack to be copied
     */
    public AuthorizationStack(AuthorizationStack x) {
        super(x);
        stack = new ArrayList<>(x.stack.size());
        for (AuthorizationEntity e : x.getStack()) {
            stack.add(e.clone());
        }
    }

    public AuthorizationStack(AuthControlFlag flag, String name) {
        super(flag, name);
    }

    /**
     * Get the value of {@code stack}.
     *
     * @return the current stack
     */
    public List<AuthorizationEntity> getStack() {
        return stack;
    }

    /**
     * Set the value of {@code stack}.
     *
     * @param s the new stack
     */
    public void setStack(List<AuthorizationEntity> s) {
        this.stack = s;
    }

    /**
     * Add a new authorization entity into this stack.
     *
     * @param s new entity
     */
    public void add(AuthorizationEntity s) {
        this.stack.add(s);
    }

    /**
     * Remove the given authorization entity from this stack.
     *
     * @param s the entity to remove
     */
    public void remove(AuthorizationEntity s) {
        s.unload();
        this.stack.remove(s);
    }

    /**
     * Load all authorization entities in this stack.
     * <p>
     * <p>If the method is unable to load any of the entities contained in this
     * stack then this stack is marked as failed. Note that it does not affect
     * the authorization decision made by this stack.
     *
     * @param parameters parameters given in the configuration
     *
     * @see IAuthorizationPlugin#load(java.util.Map)
     */
    @Override
    public void load(Map<String, Object> parameters) {
        setCurrentSetup(new TreeMap<>());
        getCurrentSetup().putAll(parameters);
        getCurrentSetup().putAll(getSetup());

        LOGGER.log(Level.INFO, "[{0}] Stack \"{1}\" is loading.",
                new Object[]{getFlag().toString().toUpperCase(Locale.ROOT),
                getName()});

        // fill properly the "forGroups" and "forProjects" fields
        processTargetGroupsAndProjects();

        setWorking();

        int cnt = 0;
        for (AuthorizationEntity authEntity : getStack()) {
            authEntity.load(getCurrentSetup());
            if (authEntity.isWorking()) {
                cnt++;
            }
        }

        if (getStack().size() > 0 && cnt < getStack().size()) {
            setFailed();
        }

        LOGGER.log(Level.INFO, "[{0}] Stack \"{1}\" is {2}.",
                new Object[]{
                    getFlag().toString().toUpperCase(Locale.ROOT),
                    getName(),
                    isWorking() ? "ready" : "not fully ok"});
    }

    /**
     * Unload all plugins contained in this stack.
     *
     * @see IAuthorizationPlugin#unload()
     */
    @Override
    public void unload() {
        for (AuthorizationEntity plugin : getStack()) {
            plugin.unload();
        }
    }

    /**
     * Test the given entity if it should be allowed with in this stack context
     * if and only if the stack is not marked as failed.
     *
     * @param entity the given entity - this is either group or project and is
     * passed just for the logging purposes.
     * @param pluginPredicate predicate returning true or false for the given
     * entity which determines if the authorization for such entity is
     * successful or failed for particular request and plugin
     * @param skippingPredicate predicate returning true if this authorization
     * entity should be omitted from the authorization process
     * @return true if successful; false otherwise
     */
    @Override
    public boolean isAllowed(Nameable entity,
            PluginDecisionPredicate pluginPredicate,
            PluginSkippingPredicate skippingPredicate) {
        boolean overallDecision = true;
        LOGGER.log(Level.FINER, "Authorization for \"{0}\" in \"{1}\" [{2}]",
                new Object[]{entity.getName(), this.getName(), this.getFlag()});

        if (skippingPredicate.shouldSkip(this)) {
            LOGGER.log(Level.FINER, "AuthEntity \"{0}\" [{1}] skipping testing of name \"{2}\"",
                    new Object[]{this.getName(), this.getFlag(), entity.getName()});
        } else {
            overallDecision = processStack(entity, pluginPredicate, skippingPredicate);
        }

        LOGGER.log(Level.FINER, "Authorization for \"{0}\" in \"{1}\" [{2}] => {3}",
                new Object[]{entity.getName(), this.getName(), this.getFlag(), overallDecision ? "true" : "false"});
        return overallDecision;
    }

    /**
     * Process the stack.
     *
     * @param entity the given entity
     * @param pluginPredicate predicate returning true or false for the given
     * entity which determines if the authorization for such entity is
     * successful or failed for particular request and plugin
     * @param skippingPredicate predicate returning true if this authorization
     * entity should be omitted from the authorization process
     * @return true if entity is allowed; false otherwise
     */
    protected boolean processStack(Nameable entity,
            PluginDecisionPredicate pluginPredicate,
            PluginSkippingPredicate skippingPredicate) {

        boolean overallDecision = true;
        for (AuthorizationEntity authEntity : getStack()) {

            if (skippingPredicate.shouldSkip(authEntity)) {
                LOGGER.log(Level.FINEST, "AuthEntity \"{0}\" [{1}] skipping testing of name \"{2}\"",
                        new Object[]{authEntity.getName(), authEntity.getFlag(), entity.getName()});
                continue;
            }
            // run the plugin's test method
            try {
                LOGGER.log(Level.FINEST, "AuthEntity \"{0}\" [{1}] testing a name \"{2}\"",
                        new Object[]{authEntity.getName(), authEntity.getFlag(), entity.getName()});

                boolean pluginDecision = authEntity.isAllowed(entity, pluginPredicate, skippingPredicate);

                LOGGER.log(Level.FINEST, "AuthEntity \"{0}\" [{1}] testing a name \"{2}\" => {3}",
                        new Object[]{authEntity.getName(), authEntity.getFlag(), entity.getName(),
                                pluginDecision ? "true" : "false"});

                if (!pluginDecision && authEntity.isRequired()) {
                    // required sets a failure but still invokes all other plugins
                    overallDecision = false;
                    continue;
                } else if (!pluginDecision && authEntity.isRequisite()) {
                    // requisite sets a failure and immediately returns the failure
                    overallDecision = false;
                    break;
                } else if (overallDecision && pluginDecision && authEntity.isSufficient()) {
                    // sufficient immediately returns the success
                    overallDecision = true;
                    break;
                }
            } catch (AuthorizationException ex) {
                // Propagate up so that proper HTTP error can be given.
                LOGGER.log(Level.FINEST, "got authorization exception: " + ex.getMessage());
                throw ex;
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING,
                        String.format("AuthEntity \"%s\" has failed the testing of \"%s\" with an exception.",
                                authEntity.getName(),
                                entity.getName()),
                        ex);

                LOGGER.log(Level.FINEST, "AuthEntity \"{0}\" [{1}] testing a name \"{2}\" => {3}",
                        new Object[]{authEntity.getName(), authEntity.getFlag(), entity.getName(),
                            "false (failed)"});

                // set the return value to false for this faulty plugin
                if (!authEntity.isSufficient()) {
                    overallDecision = false;
                }
                // requisite plugin may immediately return the failure
                if (authEntity.isRequisite()) {
                    break;
                }
            }
        }

        return overallDecision;
    }

    /**
     * Set the plugin to all classes in this stack which requires this class in
     * the configuration. This creates a new instance of the plugin for each
     * class which needs it.
     * <p>
     * <p>This is where the loaded plugin classes get to be a part of the
     * authorization process. When the {@link AuthorizationPlugin} does not get
     * its {@link IAuthorizationPlugin} it is marked as failed and returns false
     * to all authorization decisions.
     *
     * @param plugin the new instance of a plugin
     * @return true if there is such case; false otherwise
     */
    @Override
    public boolean setPlugin(IAuthorizationPlugin plugin) {
        boolean ret = false;
        for (AuthorizationEntity p : getStack()) {
            ret = p.setPlugin(plugin) || ret;
        }
        return ret;
    }

    /**
     * Clones the stack. Performs:
     * <ul>
     * <li>copy the superclass {@link AuthorizationEntity}</li>
     * <li>perform a deep copy of the contained stack</li>
     * </ul>
     *
     * @return new instance of {@link AuthorizationStack}
     */
    @Override
    public AuthorizationStack clone() {
        return new AuthorizationStack(this);
    }

    /**
     * Print the stack hierarchy. Process also all contained plugins and
     * substacks.
     *
     * @param prefix this prefix should be prepended to every line produced by
     * this stack
     * @param colorElement a possible element where any occurrence of %color%
     * will be replaced with a HTML HEX color representing this entity state.
     * @return the string containing this stack representation
     */
    @Override
    public String hierarchyToString(String prefix, String colorElement) {
        StringBuilder builder = new StringBuilder(prefix);

        builder.append(colorToString(colorElement));
        builder.append(infoToString(prefix));
        builder.append(" (stack ").append(isWorking() ? "ok" : "not fully ok").append(")");
        builder.append("\n");

        builder.append(setupToString(prefix));
        builder.append(targetsToString(prefix));

        for (AuthorizationEntity authEntity : getStack()) {
            builder.append(authEntity.hierarchyToString(prefix + "    ", colorElement));
        }
        return builder.toString();
    }
}
