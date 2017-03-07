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
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 *
 * @author Krystof Tulinger
 */
public class AuthorizationCheck implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Enum for avaliable authorization roles.
     */
    public enum AuthorizationRole {
        /**
         * Failure of such a plugin will ultimately lead to the authorization
         * framework returning failure but only after the remaining plugins have
         * been invoked.
         *
         */
        REQUIRED("required"),
        /**
         * Like required, however, in the case that such a plugin returns a
         * failure, control is directly returned to the application. The return
         * value is that associated with the first required or requisite plugin
         * to fail.
         *
         */
        REQUISITE("requisite"),
        /**
         * If such a plugin succeeds and no prior required plugin has failed the
         * authorization framework returns success to the application
         * immediately without calling any further plugins in the stack. A
         * failure of a sufficient plugin is ignored and processing of the
         * plugin list continues unaffected.
         */
        SUFFICIENT("sufficient");

        private final String role;

        private AuthorizationRole(String role) {
            this.role = role;
        }

        @Override
        public String toString() {
            return this.role;
        }

        public static AuthorizationRole get(String role) {
            try {
                return AuthorizationRole.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException ex) {
                // role does not exist -> add some more info about which roles do exist
                throw new IllegalArgumentException(
                        String.format("No authorization role \"%s\", available roles are [%s]. %s",
                                role,
                                Arrays.asList(AuthorizationRole.values())
                                        .stream()
                                        .map(AuthorizationRole::toString)
                                        .collect(Collectors.joining(", ")),
                                ex.getLocalizedMessage()), ex);
            }
        }
    };

    /**
     * One of "required", "requisite", "sufficient".
     */
    private AuthorizationRole role;

    /**
     * Canonical name of a java class.
     */
    private String classname;

    public AuthorizationCheck() {
    }

    public AuthorizationCheck(AuthorizationRole role, String classname) {
        this.role = role;
        this.classname = classname;
    }

    /**
     * Get the value of classname
     *
     * @return the value of classname
     */
    public String getClassname() {
        return classname;
    }

    /**
     * Set the value of classname
     *
     * @param classname new value of classname
     */
    public void setClassname(String classname) {
        this.classname = classname;
    }

    /**
     * Get the value of role
     *
     * @return the value of role
     */
    public AuthorizationRole getRole() {
        return role;
    }

    /**
     * Set the value of role
     *
     * @param role new value of role
     */
    public void setRole(AuthorizationRole role) {
        this.role = role;
    }

    /**
     * Set the value of role
     *
     * @param role new value of role
     */
    public void setRole(String role) {
        this.role = AuthorizationRole.get(role);
    }
}
