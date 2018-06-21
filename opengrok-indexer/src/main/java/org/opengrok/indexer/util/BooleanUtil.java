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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

/**
 * boolean utility functions
 * 
 * @author Krystof Tulinger
 */
public class BooleanUtil {
    /**
     * Validate the string if it contains a boolean value.
     *
     * <p>
     * Boolean values are (case insensitive):
     * <ul>
     * <li>false</li>
     * <li>off</li>
     * <li>0</li>
     * <li>true</li>
     * <li>on</li>
     * <li>1</li>
     * </ul>
     *
     * @param value the string value
     * @return if the value is boolean or not
     */
    public static boolean isBoolean(String value) {
        return "false".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value)
                || "0".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value)
                || "1".equalsIgnoreCase(value);

    }

    /**
     * Cast a boolean value to integer.
     *
     * @param b boolean value
     * @return 0 for false and 1 for true
     */
    public static int toInteger(boolean b) {
        return b ? 1 : 0;
    }
}
