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

package org.opensolaris.opengrok.util;

import java.util.regex.Pattern;

/**
 * Various String utility methods.
 * 
 * @author austvik
 */
public final class StringUtils {

    private StringUtils() {
        // Only static utility methods
    }
    
    /**
     * Returns true if the string is empty or only includes whitespace characters.
     * 
     * @param str the string to be checked
     * @return true if string is empty or only contains whitespace charadcters
     */
    public static boolean isOnlyWhitespace(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static final Pattern javaClassPattern = 
        Pattern.compile("([a-z][A-Za-z]*\\.)+[A-Z][A-Za-z0-9]*");
    /**
     * Returns true if the string is possibly a full java class name
     *
     * @param s the string to be checked
     * @return true if string could be a java class name
    */
    public static boolean isPossiblyJavaClass(String s) {
    // Only match a small subset of possible class names to prevent false
    // positives:
    //    - class must be qualified with a package name
    //    - only letters in package name, starting with lower case
    //    - class name must be in CamelCase, starting with upper case
    return javaClassPattern.matcher(s).matches();
  }
    
}
