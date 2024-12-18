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
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.util;


public class RuntimeUtil {
    private RuntimeUtil() {
        // private for static
    }

    /*
     * interval of supported Java versions
     */
    static final int JAVA_VERSION_MIN = 11;
    static final int JAVA_VERSION_MAX = 21;

    /**
     * @throws RuntimeException if the Java runtime version is outside
     * {@link #JAVA_VERSION_MIN} and {@link #JAVA_VERSION_MAX}.
     */
    public static void checkJavaVersion() throws RuntimeException {
        Runtime.Version javaVersion = Runtime.version();
        int majorVersion = javaVersion.version().get(0);
        if (majorVersion < JAVA_VERSION_MIN || majorVersion > JAVA_VERSION_MAX) {
            throw new RuntimeException(String.format("unsupported Java version %d [%d,%d)",
                    majorVersion, JAVA_VERSION_MIN, JAVA_VERSION_MAX));
        }
    }

    /**
     * @return string representing Java version and some properties
     */
    public static String getJavaVersion() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("version: ").append(Runtime.version());
        stringBuilder.append(", name: ").append(System.getProperty("java.vm.name"));
        stringBuilder.append(", vendor: ").append(System.getProperty("java.vendor"));
        stringBuilder.append(", arch: ").append(System.getProperty("os.arch"));
        return stringBuilder.toString();
    }

    private static String bytesToHumanReadable(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Invalid size: " + size);
        }
        if (size < 1024) {
            return size + " bytes";
        }
        String units = " KMGTPE";
        int idx = 0;
        float value = size;
        while (value > 1024) {
            value /= 1024;
            idx++;
        }
        return String.format("%.1f %siB", value, units.substring(idx, idx + 1));
    }

    /**
     * @return string representing Java properties of interest
     */
    public static String getJavaProperties() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ncpu: ").append(Runtime.getRuntime().availableProcessors());
        stringBuilder.append(", maxMemory: ").append(bytesToHumanReadable(Runtime.getRuntime().maxMemory()));
        return stringBuilder.toString();
    }
}
