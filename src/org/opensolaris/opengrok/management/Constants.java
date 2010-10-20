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
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 */

package org.opensolaris.opengrok.management;

/**
 * Constants used by the JMX agent and the client.
 */
public final class Constants {
    /** Private constructor to prevent instantiation. */
    private Constants() {
        // do nothing
    }

    /**
     * Protocol name used in URLs for the RMI JMX protocol. JMXServiceURL
     * always converts the protocol name to lower case (as per class javadoc).
     */
    public final static String RMI_PROTOCOL = "rmi";

    /** Property specifying path where log files should be written. */
    public static final String LOG_PATH =
            "org.opensolaris.opengrok.management.logging.path";

    /**
     * Property specifying location of OpenGrok configuration file
     * (configuration.xml).
     */
    public static final String CONFIG_FILE =
            "org.opensolaris.opengrok.configuration.file";

    /**
     * Property specifying URL to JMX service. If this property is
     * not set, an URL using the RMI protocol will be generated from
     * {@link #JMX_HOST}, {@link #JMX_PORT} and {@link #RMI_PORT}.
     */
    public static final String JMX_URL =
            "org.opensolaris.opengrok.management.url";

    /** Property specifying JMX server host. We use localhost by default. */
    public static final String JMX_HOST =
            "org.opensolaris.opengrok.management.host";

    /** Property specifying JMX server port. We use 9292 by default. */
    public static final String JMX_PORT =
            "org.opensolaris.opengrok.management.port";

    /**
     * Property specifying port on which the RMI registry is listening. By
     * default, we generate the RMI port by adding one to the JMX port.
     */
    public static final String RMI_PORT =
            "org.opensolaris.opengrok.management.rmi.port";

    /**
     * Property specifying whether an embedded RMI registry should be started
     * for a server that uses the RMI JMX protocol.
     */
    public static final String RMI_START =
            "org.opensolaris.opengrok.management.rmi.startRegistry";
}
