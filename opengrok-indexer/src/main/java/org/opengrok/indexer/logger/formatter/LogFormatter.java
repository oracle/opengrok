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
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.logger.formatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Formats log in given pattern.
 * following parameters are used with appropriate value. See {@link java.util.Formatter} for further details.
 * %1: date/time
 * %2: full source classname[space]method name
 * %3: logger name
 * %4: localized level
 * %5: formatted and localized message
 * %6: throwable stacktrace with leading newline
 * %7: full source classname
 * %8: full source method name
 * %9: simplified source classname
 * %10: thread id
 * %11: not-formatted message
 * %12: version (see constructor parameter)
 */
public class LogFormatter extends Formatter {

    private static final String DEFAULT_FORMAT = "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n";

    private final String format;
    private final String version;

    public LogFormatter() {
        this(DEFAULT_FORMAT, "unknown");
    }

    public LogFormatter(String format, String version) {
        this.format = format;
        this.version = version;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String format(LogRecord logRecord) {
        Date dat = new Date(logRecord.getMillis());
        StringBuilder source = new StringBuilder();
        if (logRecord.getSourceClassName() != null) {
            source.append(logRecord.getSourceClassName());
            if (logRecord.getSourceMethodName() != null) {
                source.append(' ').append(logRecord.getSourceMethodName());
            }
        } else {
            source.append(logRecord.getLoggerName());
        }

        StringBuilder throwable = new StringBuilder();
        if (logRecord.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            logRecord.getThrown().printStackTrace(pw);
            pw.close();
            throwable.append(sw.toString());
        }

        return String.format(format,
                dat,                                   //%1
                source.toString(),                     //%2
                logRecord.getLoggerName(),                //%3
                logRecord.getLevel().getLocalizedName(),  //%4
                formatMessage(logRecord),                 //%5
                throwable,                             //%6 (till here the same as JDK7's SimpleFormatter)
                logRecord.getSourceClassName(),           //%7
                logRecord.getSourceMethodName(),          //%8
                className(logRecord.getSourceClassName()), //%9
                logRecord.getThreadID(),                  //%10
                logRecord.getMessage(),                   //%11
                version
                );
    }

    private String className(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
