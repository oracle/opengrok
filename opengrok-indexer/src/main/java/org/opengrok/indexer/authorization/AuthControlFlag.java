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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Enum for available authorization roles.
 *
 * @author Krystof Tulinger
 */
public enum AuthControlFlag {
    /**
     * Failure of such a plugin will ultimately lead to the authorization
     * framework returning failure but only after the remaining plugins have
     * been invoked.
     *
     */
    REQUIRED("required"),
    /**
     * Like required, however, in the case that such a plugin returns a failure,
     * control is directly returned to the application. The return value is that
     * associated with the first required or requisite plugin to fail.
     *
     */
    REQUISITE("requisite"),
    /**
     * If such a plugin succeeds and no prior required plugin has failed the
     * authorization framework returns success to the application immediately
     * without calling any further plugins in the stack. A failure of a
     * sufficient plugin is ignored and processing of the plugin list continues
     * unaffected.
     */
    SUFFICIENT("sufficient");

    private final String flag;

    AuthControlFlag(String flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return this.flag;
    }

    public boolean isRequired() {
        return REQUIRED.equals(this);
    }

    public boolean isRequisite() {
        return REQUISITE.equals(this);
    }

    public boolean isSufficient() {
        return SUFFICIENT.equals(this);
    }

    /**
     * Get the enum value for the string parameter.
     *
     * @param flag parameter describing the desired enum value
     * @return the flag representing the parameter value
     *
     * @throws IllegalArgumentException when there is no such value in the enum
     */
    public static AuthControlFlag get(String flag) throws IllegalArgumentException {
        try {
            return AuthControlFlag.valueOf(flag.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // flag does not exist -> add some more info about which flags do exist
            throw new IllegalArgumentException(
                    String.format("No control flag \"%s\", available flags are [%s]. %s",
                            flag,
                            Arrays.asList(AuthControlFlag.values())
                                    .stream()
                                    .map(AuthControlFlag::toString)
                                    .collect(Collectors.joining(", ")),
                            ex.getLocalizedMessage()), ex);
        }
    }
}
