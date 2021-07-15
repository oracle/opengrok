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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;

public class JavaVersionUtil {
    private JavaVersionUtil() {
        // private to enforce static
    }

    /**
     * Return major numeric Java version, e.g. 11 for Java 11
     * @return integer
     */
    private static int getVersion() {
        return Runtime.version().feature();
    }

    /**
     * @return true if given Java version is supported, false otherwise
     */
    public static boolean isSupportedVersion() {
        int version = getVersion();
        return version <= 12 && version >= 11;
    }
}
