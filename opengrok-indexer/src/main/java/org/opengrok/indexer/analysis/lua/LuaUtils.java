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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.lua;

/**
 * Represents a container for Lua-related utility methods.
*/
public class LuaUtils {

    /**
     * Private to enforce singleton.
     */
    private  LuaUtils() {
    }
    
    /**
     * Counts the level of a Lua opening long bracket specified in
     * {@code capture}.
     * @param capture the opening long bracket
     * @return the bracket level
     */
    public static int countOpeningLongBracket(String capture) {
        if (!capture.startsWith("[") || !capture.endsWith("[")) {
            throw new IllegalArgumentException(
                "Invalid opening long bracket: " + capture);
        }

        int n = 0;
        for (int i = 1; i + 1 < capture.length(); ++i) {
            if (capture.charAt(i) != '=') {
                throw new IllegalArgumentException(
                    "Invalid opening long bracket: " + capture);
            }
            ++n;
        }
        return n;
    }

    /**
     * Determines if the specified {@code capture} is a closing long bracket of
     * the specified {@code level}.
     * @param capture the possible Lua closing long bracket
     * @param level the required level of closing long bracket
     * @return true if the {@code capture} is determined to be the required
     * closing long bracket or false otherwise.
     */
    public static boolean isClosingLongBracket(String capture, int level) {
        if (!capture.startsWith("]") || !capture.endsWith("]")) {
            throw new IllegalArgumentException(
                "Invalid opening long bracket: " + capture);
        }

        int n = 0;
        for (int i = 1; i + 1 < capture.length(); ++i) {
            if (capture.charAt(i) != '=') {
                throw new IllegalArgumentException(
                    "Invalid opening long bracket: " + capture);
            }
            ++n;
        }
        return n == level;
    }
}
