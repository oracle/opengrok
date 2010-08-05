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
import java.util.logging.LogRecord;

/**
 * Opengrok logfile formatter
 * Creates a logentry in the logfile on the following format
 * YYYY-MM-DD HH:MM:ss.SSSZ <loglevel> t<threadnumber> <Class.method>: <logmessage>
 * @author Lubos Kosco
 */
final public class SimpleFileLogFormatter extends Formatter {
   
   private final java.text.SimpleDateFormat formatter =
      new java.text.SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSZ");
   private final static String lineSeparator = System.
      getProperty("line.separator");
   Date dat = new Date();
   
   private String ts(Date date) {
      return formatter.format(date);
   }
   
   private String classNameOnly(String name) {
      int index = name.lastIndexOf('.') + 1;
      return name.substring(index);
   }
   
    @Override
   public String format(LogRecord record) {
      StringBuilder sb = new StringBuilder();
      dat.setTime(record.getMillis());
      sb.append(ts(dat));
      sb.append(" ");
      String loglevel = record.getLevel().getName();
      sb.append(loglevel);
      sb.append(" ");
      sb.append("t");
      sb.append(record.getThreadID());
      sb.append(" ");
      sb.append(classNameOnly(record.getSourceClassName()));
      sb.append('.');
      sb.append(record.getSourceMethodName());
      sb.append(": ");
      sb.append(formatMessage(record));
      Throwable thrown = record.getThrown();
      if (null != thrown) {
         sb.append(lineSeparator);
         java.io.ByteArrayOutputStream ba=new java.io.ByteArrayOutputStream();
         thrown.printStackTrace(new java.io.PrintStream(ba, true));
         sb.append(ba.toString());
      }      
      sb.append(lineSeparator);
      return sb.toString();
   }
}
