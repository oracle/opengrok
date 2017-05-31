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
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.logger;

import org.opensolaris.opengrok.logger.formatter.ConsoleFormatter;
import org.opensolaris.opengrok.logger.formatter.FileLogFormatter;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Utilities to maintain logging.
 */
public class LoggerUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerUtil.class);

    public static final String BASE_LOGGER = "org.opensolaris.opengrok";
    private static final int DEFAULT_FILEHANDLER_LIMIT = 52428800;
    private static final int DEFAULT_FILEHANDLER_COUNT = 3;

    private static volatile String loggerFile = null;

    public static Logger getBaseLogger() {
        return Logger.getLogger(BASE_LOGGER);
    }

    public static void setBaseConsoleLogLevel(Level level) {
        setBaseLogLevel(ConsoleHandler.class, level);
    }

    public static Level getBaseConsoleLogLevel() {
        return getBaseLogLevel(ConsoleHandler.class);
    }

    public static void setBaseFileLogLevel(Level level) {
        setBaseLogLevel(FileHandler.class, level);
    }

    public static Level getBaseFileLogLevel() {
        return getBaseLogLevel(FileHandler.class);
    }

    private static void setBaseLogLevel(Class<? extends Handler> handlerClass, Level level) {
        for (Handler handler : getBaseLogger().getHandlers()) {
            if (handlerClass.isInstance(handler)) {
                handler.setLevel(level);
            }
        }
    }

    private static Level getBaseLogLevel(Class<? extends Handler> handlerClass) {
        for (Handler handler : getBaseLogger().getHandlers()) {
            if (handlerClass.isInstance(handler)) {
                return handler.getLevel();
            }
        }
        return Level.OFF;
    }

    public static String getFileHandlerPattern() {
        return LogManager.getLogManager().getProperty("java.util.logging.FileHandler.pattern");
    }

    public static void setFileHandlerLogPath(String path) throws IOException {
        if (path != null) {
            File jlp = new File(path);
            if (!jlp.exists() && !jlp.mkdirs()) {
                throw new IOException("could not make logpath: "
                        + jlp.getAbsolutePath());
            }
        }

        StringBuilder logfile = new StringBuilder();
        logfile.append(path == null ? "%t" : path);
        logfile.append(File.separatorChar).append("opengrok%g.%u.log");

        for (Handler handler : getBaseLogger().getHandlers()) {
            if (handler instanceof FileHandler) {
                FileHandler fileHandler = (FileHandler) handler;
                FileHandler newFileHandler;
                try {
                    int logFilesSizeLimit = loggerIntProperty("java.util.logging.FileHandler.limit", DEFAULT_FILEHANDLER_LIMIT);
                    int logFilesCount = loggerIntProperty("java.util.logging.FileHandler.count", DEFAULT_FILEHANDLER_COUNT);
                    newFileHandler = new FileHandler(logfile.toString(), logFilesSizeLimit, logFilesCount);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Cannot create new logger FileHandler: " + logfile.toString(), e);
                    return;
                }
                String formatter = LogManager.getLogManager().getProperty("java.util.logging.FileHandler.formatter");
                newFileHandler.setLevel(fileHandler.getLevel());
                try {
                    newFileHandler.setFormatter((Formatter) Class.forName(formatter).newInstance());
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    newFileHandler.setFormatter(new FileLogFormatter());
                }
                getBaseLogger().addHandler(newFileHandler);
                getBaseLogger().removeHandler(fileHandler);
                loggerFile = logfile.toString();
            }
        }
    }

    public static String getFileHandlerLogPath() {
        return loggerFile != null ? loggerFile : LogManager.getLogManager().getProperty("java.util.logging.FileHandler.pattern");
    }

    private static int loggerIntProperty(String name, int def) {
        String val = LogManager.getLogManager().getProperty(name);
        if (val == null) {
            return def;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String initLogger(String logpath, Level filelevel, Level consolelevel) throws IOException {
        if (logpath != null) {
            File jlp = new File(logpath);
            if (!jlp.exists() && !jlp.mkdirs()) {
                throw new RuntimeException("could not make logpath: "
                        + jlp.getAbsolutePath());
            }
            if (!jlp.canWrite() && !Level.OFF.equals(filelevel)) {
                throw new IOException("logpath not writeable " + jlp.getAbsolutePath());
            }
        }

        Logger.getGlobal().setLevel(Level.OFF);
        getBaseLogger().setLevel(Level.ALL);
        StringBuilder logfile = new StringBuilder();
        logfile.append(logpath == null ? "%t" : logpath);
        logfile.append(File.separatorChar).append("opengrok%g.%u.log");
        try {
            FileHandler fh = new FileHandler(logfile.toString(),
                    loggerIntProperty("java.util.logging.FileHandler.limit", DEFAULT_FILEHANDLER_LIMIT),
                    loggerIntProperty("java.util.logging.FileHandler.count", DEFAULT_FILEHANDLER_COUNT));

            fh.setLevel(filelevel);
            String formatter = LogManager.getLogManager().getProperty("java.util.logging.FileHandler.formatter");
            try {
                fh.setFormatter((Formatter) Class.forName(formatter).newInstance());
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                fh.setFormatter(new FileLogFormatter());
            }

            getBaseLogger().addHandler(fh);
            loggerFile = logfile.toString();

            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(consolelevel);
            ch.setFormatter(new ConsoleFormatter());
            getBaseLogger().addHandler(ch);

        } catch (IOException | SecurityException ex1) {
            LOGGER.log(Level.SEVERE, "Exception logging", ex1);
            throw new IOException("Exception setting up logging " + ex1);
        }
        return logpath;
    }
}
