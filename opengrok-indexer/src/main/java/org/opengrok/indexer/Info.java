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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class to get information of the OpenGrok version.
 *
 * @author Trond Norbye
 */
public final class Info {
    private static final Properties properties = new Properties();

    private static final String VERSION;
    private static final String REVISION;
    private static final String REVISION_SHORT;

    private static final String UNKNOWN = "unknown";

    static {
        try (InputStream in = Info.class.getResourceAsStream("info.properties")) {
            if (in != null) {
                properties.load(in);
            }
            VERSION = properties.getProperty("version", UNKNOWN);
            REVISION = properties.getProperty("changeset", UNKNOWN);
            REVISION_SHORT = properties.getProperty("changeset_short", UNKNOWN);
        } catch (IOException ioe) {
            throw new VersionInfoLoadException(ioe);
        }
    }

    /**
     * Get major version.
     *
     * @return major version
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Get full version (product vMajor revMinor).
     *
     * @return full version
     */
    public static String getFullVersion() {
        return "OpenGrok v" + VERSION + " rev " + REVISION;
    }

    /**
     * Get minor version.
     *
     * @return minor version
     */
    public static String getRevision() {
        return REVISION;
    }


    /**
     * Get short minor version.
     *
     * @return short minor version
     */
    public static String getShortRevision() {
        return REVISION_SHORT;
    }


    private Info() {
    }
}
