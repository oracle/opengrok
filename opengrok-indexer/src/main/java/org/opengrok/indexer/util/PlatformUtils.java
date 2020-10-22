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
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2011, Jens Elkner.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.util;

import java.util.Locale;

public class PlatformUtils {

    private static String OS;
    private static Boolean IS_UNIX;
    private static Boolean IS_WINDOWS;

    /** Private to enforce static. */
    private PlatformUtils() {
    }

    /**
     * Gets a value indicating the operating system name.
     * @return the name in lowercase
     */
    public static String getOsName() {
        if (OS == null) {
            OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        }
        return OS;
    }

    /**
     * Gets a value indicating if the operating system name indicates Windows.
     * @return {@code true} if Windows
     */
    public static boolean isWindows() {
        if (IS_WINDOWS == null) {
            String osName = getOsName();
            IS_WINDOWS = osName.startsWith("windows");
        }
        return IS_WINDOWS;
    }

    /**
     * Gets a value indicating if the operating system name is Unix-like
     * (Solaris, SunOS, Linux, Mac, BSD).
     * @return {@code true} if Unix-like
     */
    public static boolean isUnix() {
        if (IS_UNIX == null) {
            String osName = getOsName();
            IS_UNIX = osName.startsWith("linux") || osName.startsWith("solaris") ||
                    osName.contains("bsd") || osName.startsWith("mac") ||
                    osName.startsWith("sunos");
        }
        return IS_UNIX;
    }
}
