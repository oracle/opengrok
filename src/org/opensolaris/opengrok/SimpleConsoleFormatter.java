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

package org.opensolaris.opengrok;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Opengrok console formatter
 * Creates a logentry on the console using the following format
 * HH:MM:ss <loglevel>: <logmessage>
 * @author Lubos Kosco
 */
final public class SimpleConsoleFormatter extends Formatter {

   private final java.text.SimpleDateFormat formatter =
      new java.text.SimpleDateFormat("HH:mm:ss");
   private static final String lineSeparator = System.
      getProperty("line.separator");

   private String ts(Date date) {
      return formatter.format(date);
   }

    @Override
   public String format(LogRecord record) {
      StringBuilder sb = new StringBuilder();
      sb.append(ts(new Date(record.getMillis())));
      sb.append(" ");
      sb.append(record.getLevel().getName());
      sb.append(": ");
      sb.append(formatMessage(record));
      Throwable thrown = record.getThrown();
      if (null != thrown && record.getLevel().intValue() < Level.CONFIG.intValue()) {
         sb.append(lineSeparator);
         java.io.ByteArrayOutputStream ba=new java.io.ByteArrayOutputStream();
         thrown.printStackTrace(new java.io.PrintStream(ba, true));
         sb.append(ba.toString());
      }
      sb.append(lineSeparator);
      return sb.toString();
   }
}
