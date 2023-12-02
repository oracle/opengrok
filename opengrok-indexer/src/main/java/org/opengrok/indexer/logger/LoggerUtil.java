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
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.logger;

import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Utilities to maintain logging.
 */
public class LoggerUtil {

    public static final String BASE_LOGGER = "org.opengrok";

    private LoggerUtil() {
    }

    private static Logger getBaseLogger() {
        return Logger.getLogger(BASE_LOGGER);
    }

    public static void setBaseConsoleLogLevel(Level level) {
        Arrays.stream(getBaseLogger().getHandlers())
                .filter(ConsoleHandler.class::isInstance)
                .forEach(handler -> handler.setLevel(level));
    }

    public static String getFileHandlerPattern() {
        return LogManager.getLogManager().getProperty("java.util.logging.FileHandler.pattern");
    }
}
