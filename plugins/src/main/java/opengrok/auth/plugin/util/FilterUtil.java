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
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.util;

import opengrok.auth.plugin.entity.User;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FilterUtil {

    private FilterUtil() {
        // utility class
    }

    private static final String LOWER_CASE = "toLowerCase";
    private static final String UPPER_CASE = "toUpperCase";

    /**
     * Verify the names of the transforms in the map.
     * @param transforms map of attribute transforms. Valid values: <code>toLowerCase, toUpperCase</code>
     * @throws UnsupportedOperationException in case of invalid transform name
     */
    public static void checkTransforms(Map<String, String> transforms) {
        Set<String> possibleTransforms = new HashSet<>(Arrays.asList(LOWER_CASE, UPPER_CASE));
        for (String transform : transforms.values()) {
            if (!possibleTransforms.contains(transform)) {
                throw new UnsupportedOperationException(String.format("invalid transform: %s", transform));
            }
        }
    }

    static String doTransform(String value, String transform) {
        switch (transform) {
            case LOWER_CASE:
                return value.toLowerCase(Locale.ROOT);
            case UPPER_CASE:
                return value.toUpperCase(Locale.ROOT);
            default:
                throw new UnsupportedOperationException(String.format("transform '%s' is unsupported", transform));
        }
    }

    /**
     * Expand attributes in filter string.
     * @param filter input string
     * @param name attribute name
     * @param value value to replace
     * @param transforms map of transformations to be potentially applied on the value
     * @return new value of the string
     */
    public static String replace(String filter, String name, String value, Map<String, String> transforms) {
        if (transforms != null) {
            String transform;
            if ((transform = transforms.get(name)) != null) {
                value = doTransform(value, transform);
            }
        }

        return filter.replaceAll("(?<!\\\\)%" + name + "(?<!\\\\)%", value);
    }

    /**
     * Replace attribute names with values in filter string.
     * @param user User object
     * @param filter filter string
     * @return filter with the values replaced
     * @see #expandUserFilter(User, String, Map)
     */
    public static String expandUserFilter(User user, String filter) {
        return expandUserFilter(user, filter, null);
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
     * @param filter filter
     * @param transforms map of transforms
     * @return replaced result
     */
    public static String expandUserFilter(User user, String filter, Map<String, String> transforms) {
        if (user.getUsername() != null) {
            filter = replace(filter, "username", user.getUsername(), transforms);
        }
        if (user.getId() != null) {
            filter = replace(filter, "guid", user.getId(), transforms);
        }

        return filter;
    }
}
