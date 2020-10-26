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
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.logger;

import java.util.logging.Logger;

/**
 * Factory for creating {@link Logger} instances.
 */
public class LoggerFactory {

    private static LoggerFactoryBridge loggerFactoryBridge = new PackageBasedLoggerFactoryBridge();

    private LoggerFactory() {
    }

    /**
     * Returns {@link Logger} for given class.
     * @param clazz class
     * @return logger for given class
     */
    public static Logger getLogger(Class<?> clazz) {
        return loggerFactoryBridge.getLogger(clazz);
    }

    /**
     * Injecting bridge to use different logger factory (e.g. in tests).
     * Default access forces to use this method only in the same package.
     * @param loggerFactoryBridge bridge implementation
     */
    static void setLoggerFactoryBridge(LoggerFactoryBridge loggerFactoryBridge) {
        LoggerFactory.loggerFactoryBridge = loggerFactoryBridge;
    }
}
