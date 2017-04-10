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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.configuration.Nameable;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 *
 * @author Krystof Tulinger
 */
public class AuthorizationStack extends AuthorizationEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationStack.class);

    private List<AuthorizationEntity> stack = new ArrayList<>();

    public AuthorizationStack() {
    }

    public AuthorizationStack(AuthControlFlag role, String name) {
        this.role = role;
        this.name = name;
    }

    /**
     * Get the value of stack
     *
     * @return the current stack
     */
    public List<AuthorizationEntity> getStack() {
        return stack;
    }

    /**
     * Set the value of stack
     *
     * @param s the new stack
     */
    public void setStack(List<AuthorizationEntity> s) {
        this.stack = s;
    }

    /**
     * Add a new authorization check in this stack.
     *
     * @param s new check
     */
    public void add(AuthorizationEntity s) {
        this.stack.add(s);
    }

    /**
     * Remove the given authorization check from this stack.
     *
     * @param s the check to remove
     */
    public void remove(AuthorizationEntity s) {
        s.unload();
        this.stack.remove(s);
    }

    /**
     * Load all authorization checks in this stack.
     *
     * <p>
     * If the method is unable to load all the checks contained in this stack
     * then any authorization check should fail for this stack in the future.
     * </p>
     *
     * @param parameters parameters given in the configuration
     *
     * @see IAuthorizationPlugin#load(java.util.Map)
     */
    @Override
    public void load(Map<String, Object> parameters) {
        Map<String, Object> s = new TreeMap<>();
        s.putAll(parameters);
        s.putAll(getSetup());

        LOGGER.log(Level.INFO, "[{0}] Stack \"{1}\" is loading.",
                new Object[]{
                    getRole().toString().toUpperCase(),
                    getName()});

        setWorking();

        int cnt = 0;
        for (AuthorizationEntity plugin : getStack()) {
            plugin.load(s);
            if (plugin.isWorking()) {
                cnt++;
            }
        }

        if (getStack().size() > 0 && cnt == 0) {
            setFailed();
        }

        LOGGER.log(Level.INFO, "[{0}] Stack \"{1}\" is {2}.",
                new Object[]{
                    getRole().toString().toUpperCase(),
                    getName(),
                    isWorking() ? "working" : "failed"});
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
     * @param entity the given entity
     * @param predicate predicate returning true or false for the given entity
     * which determines if the authorization for such entity is successful or
     * failed for particular request and plugin
     * @return true if successful; false otherwise
     */
    @Override
    public boolean isAllowed(Nameable entity, Predicate<IAuthorizationPlugin> predicate) {
        boolean overallDecision = true;

        LOGGER.log(Level.FINEST, "Authorization for \"{0}\"",
                new Object[]{entity.getName()});
        for (AuthorizationEntity plugin : getStack()) {
            // run the plugin's test method
            try {
                LOGGER.log(Level.FINEST, "Plugin \"{0}\" [{1}] testing a name \"{2}\"",
                        new Object[]{plugin.getName(), plugin.getRole(), entity.getName()});

                boolean pluginDecision = plugin.isAllowed(entity, predicate);

                LOGGER.log(Level.FINEST, "Plugin \"{0}\" [{1}] testing a name \"{2}\" => {3}",
                        new Object[]{plugin.getName(), plugin.getRole(), entity.getName(),
                            pluginDecision ? "true" : "false"});

                if (!pluginDecision && plugin.isRequired()) {
                    // required sets a failure but still invokes all other plugins
                    overallDecision = false;
                    continue;
                } else if (!pluginDecision && plugin.isRequisite()) {
                    // requisite sets a failure and immediately returns the failure
                    overallDecision = false;
                    break;
                } else if (overallDecision && pluginDecision && plugin.isSufficient()) {
                    // sufficient immediately returns the success
                    overallDecision = true;
                    break;
                }
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING,
                        String.format("Plugin \"%s\" has failed the testing of \"%s\" with an exception.",
                                plugin.getName(),
                                entity.getName()),
                        ex);

                LOGGER.log(Level.FINEST, "Plugin \"{0}\" [{1}] testing a name \"{2}\" => {3}",
                        new Object[]{plugin.getName(), plugin.getRole(), entity.getName(),
                            "false (failed)"});

                // set the return value to false for this faulty plugin
                if (!plugin.isSufficient()) {
                    overallDecision = false;
                }
                // requisite plugin may immediately return the failure
                if (plugin.isRequisite()) {
                    break;
                }
            }
        }
        LOGGER.log(Level.FINEST, "Authorization for \"{0}\" => {1}",
                new Object[]{entity.getName(), overallDecision ? "true" : "false"});
        return overallDecision;
    }

    /**
     * Set the plugin to all classes in this stack which requires this class in
     * the configuration.
     *
     * @param plugin the new instance of a plugion
     * @return true if there is such case; false otherwise
     */
    @Override
    public boolean setPlugin(IAuthorizationPlugin plugin) {
        boolean ret = false;
        for (AuthorizationEntity p : getStack()) {
            ret = ret || p.setPlugin(plugin);
        }
        return ret;
    }
}
