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
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
 */

package opengrok.auth.plugin.util;

import opengrok.auth.plugin.entity.User;

public class FilterUtil {

    private FilterUtil() {
        // utility class
    }

    /**
     * Expand {@code User} object attribute values into the filter.
     *
     * Special values are:
     * <ul>
     * <li>%username% - to be replaced with username value from the User object</li>
     * <li>%guid% - to be replaced with guid value from the User object</li>
     * </ul>
     *
     * @param user User object from the request (created by {@code UserPlugin})
     * @return replaced result
     */
    public static String expandUserFilter(User user, String filter) {
        if (user.getUsername() != null) {
            filter = filter.replaceAll("(?<!\\\\)%username(?<!\\\\)%", user.getUsername());
        }
        if (user.getId() != null) {
            filter = filter.replaceAll("(?<!\\\\)%guid(?<!\\\\)%", user.getId());
        }

        return filter;
    }
}
