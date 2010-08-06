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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Util class to set up Logging using the Console and FileLog formatter classes
 * 
 * @author Jan S Berg
 */
@SuppressWarnings({"PMD.MoreThanOneLogger", "PMD.SystemPrintln", "PMD.AvoidThrowingRawExceptionTypes"})
public final class OpenGrokLogger {

    private static int LOGFILESIZELIMIT = 1000000;
    private static int LOGFILESCOUNT = 30;
    private final static Logger log = Logger.getLogger("org.opensolaris.opengrok");
    private static Level consoleLevel = Level.WARNING;
    private static Level fileLevel = Level.FINE;
    private static String filepath = "";

    public static String getFileLogPath() {
        return filepath;
    }
    
    public static Logger getLogger() {
        return log;
    }

    public static void setConsoleLogLevel(Level level) {
        Handler[] handlers = log.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            Handler h = handlers[i];
            if (h instanceof ConsoleHandler) {
                h.setLevel(level);
                consoleLevel = level;
            }
        }
    }
        
    public static Level getConsoleLogLevel() {
        return consoleLevel;
    }

    /**
     *
     * @param level new level for console
     */
    public static void setOGConsoleLogLevel(Level level) {
        for (Enumeration e = LogManager.getLogManager().getLoggerNames();
                e.hasMoreElements();) {
            String loggerName = (String) e.nextElement();
            Logger l = Logger.getLogger(loggerName);
            Handler[] h = l.getHandlers();
            if (!loggerName.startsWith("org.opensolaris.opengrok")) {
                for (int i = 0; i < h.length; ++i) {
                    Handler hi = h[i];
                    if (hi instanceof ConsoleHandler) {
                        hi.setLevel(level);
                    }
                }
            }
            h = l.getHandlers();
        }
    }

    public static void setFileLogLevel(Level level) {
        Handler[] handlers = log.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            Handler h = handlers[i];
            if (h instanceof FileHandler) {
                h.setLevel(level);
                fileLevel = level;
            }
        }
    }

    public static Level getFileLogLevel() {
        return fileLevel;
    }

    public static void setFileLogPath(String path) throws IOException {
        if (path != null) {
            File jlp = new File(path);
            if (!jlp.exists() && !jlp.mkdirs()) {
                throw new IOException("could not make logpath: " +
                        jlp.getAbsolutePath());
            }
        }

        StringBuffer logfile;
        if (path == null) {
            logfile = new StringBuffer("%t");
        } else {
            logfile = new StringBuffer(path);
        }
        filepath = logfile.toString();
        logfile.append(File.separatorChar).append("opengrok%g.%u.log");

        Handler[] handlers = log.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            Handler h = handlers[i];
            if (h instanceof FileHandler) {
                FileHandler fh = (FileHandler) h;
                FileHandler nfh = new FileHandler(logfile.toString(),
                        LOGFILESIZELIMIT, // size (unlimited)
                        LOGFILESCOUNT); // # rotations

                nfh.setLevel(fh.getLevel());
                nfh.setFormatter(new FileLogFormatter());

                log.addHandler(nfh);
                log.removeHandler(fh);
            }
        }
    }
    
    public static String setupLogger(String logpath, Level filelevel, Level consolelevel) throws IOException {
        System.out.println("Logging to " + logpath);
        if (logpath != null) {
            File jlp = new File(logpath);
            if (!jlp.exists() && !jlp.mkdirs()) {
                throw new RuntimeException("could not make logpath: " +
                        jlp.getAbsolutePath());
            }
            if (!jlp.canWrite() && !Level.OFF.equals(filelevel)) {
                throw new IOException("logpath not writeable " + jlp.getAbsolutePath());
            }
       }

        clearForeignHandlers();
        StringBuffer logfile;
        if (logpath == null) {
            logfile = new StringBuffer("%t");
        } else {
            logfile = new StringBuffer(logpath);
        }
        filepath = logfile.toString();
        logfile.append(File.separatorChar).append("opengrok%g.%u.log");
        try {
            FileHandler fh = new FileHandler(logfile.toString(),
                    LOGFILESIZELIMIT, // size (unlimited)
                    LOGFILESCOUNT); // # rotations

            fh.setLevel(filelevel);
            fileLevel = filelevel;
            fh.setFormatter(new FileLogFormatter());

            log.addHandler(fh);

            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(consolelevel);
            consoleLevel = consolelevel;
            ch.setFormatter(new ConsoleFormatter());
            log.addHandler(ch);

        } catch (Exception ex1) {
            System.err.println("Exception logging " + ex1);
            throw new IOException("Exception setting up logging " + ex1);
        }
        log.setLevel(filelevel);
        return logpath;
    }

    private static void clearForeignHandlers() {
        for (Enumeration e = LogManager.getLogManager().getLoggerNames();
                e.hasMoreElements();) {
            String loggerName = (String) e.nextElement();
            Logger l = Logger.getLogger(loggerName);
            Handler[] h = l.getHandlers();
            if (!loggerName.startsWith("org.opensolaris.opengrok")) {
                for (int i = 0; i < h.length; ++i) {
                    l.removeHandler(h[i]);
                }
            }
            h = l.getHandlers();
        }
    }

    private OpenGrokLogger() {
    }
}
