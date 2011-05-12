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
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class to get information of the OpenGrok version.
 *
 * @author Trond Norbye
 */
@SuppressWarnings("PMD.AvoidThrowingRawExceptionTypes")
public final class Info {
    private static final Properties properties = new Properties();

    private static final String VERSION;
    private static final String REVISION;

    static {
        InputStream in = null;
        try {
            in = Info.class.getResourceAsStream("info.properties");
            if (in != null) {
                properties.load(in);
            }
            VERSION = properties.getProperty("version", "unknown");
            REVISION = properties.getProperty("changeset", "unknown");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage()); //NOPMD
            }
        }
    }

    /**
     * get major version
     * @return major version
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * get full version (product vMajor revMinor)
     * @return full version
     */
    public static String getFullVersion() {
        return "OpenGrok v" + VERSION + " rev " + REVISION;
    }

    /**
     * get minor version
     * @return minor version
     */
    public static String getRevision() {
        return REVISION;
    }

    private Info() {
    }
}
