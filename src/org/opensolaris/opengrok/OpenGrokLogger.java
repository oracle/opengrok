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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok;

import java.io.File;
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
public class OpenGrokLogger {
   
   private static int LOGFILESIZELIMIT = 1000000;
   private static int LOGFILESCOUNT = 30;
   private final static Logger log = Logger.getLogger("org.opensolaris.opengrok");
      
   public static String setupLogger(String logpath, Level filelevel, Level consolelevel) {
            
      //defaults to tmp
      if (logpath==null) {
         logpath = "%t";
      }
      System.out.println("Logging to " + logpath);
      
      try {
         File jlp = new File(logpath);
         jlp.mkdirs();
      }
      catch (Exception ex2) {
         log.severe("could not make logpath: " + ex2.getMessage());
      }

      clearForeignHandlers();
      String logfile = logpath + File.separatorChar + "opengrok%g.%u" + ".log";
      try {
            FileHandler fh = new FileHandler(logfile,
            LOGFILESIZELIMIT, // size (unlimited)
            LOGFILESCOUNT); // # rotations
            
            fh.setLevel(filelevel);
            fh.setFormatter(new FileLogFormatter());
            
         log.addHandler(fh);
         
         ConsoleHandler ch = new ConsoleHandler();
         ch.setLevel(consolelevel);
         ch.setFormatter(new ConsoleFormatter());
         log.addHandler(ch);
         
      }
      catch (Exception ex1) {
         System.err.println("Exception logging " + ex1);
      }
      log.setLevel(filelevel);
      return logpath;
   }
   
   private static void clearForeignHandlers()
   {
      for (Enumeration e = LogManager.getLogManager().getLoggerNames();
           e.hasMoreElements(); ) {
         String loggerName = (String)e.nextElement();
         Logger l = Logger.getLogger(loggerName);
         Handler[] h = l.getHandlers();
         if (!loggerName.startsWith("org.opensolaris.opengrok")) {
            for (int i=0; i<h.length; ++i) {
               l.removeHandler(h[i]);
            }
         }
         h = l.getHandlers();
      }
   }
   
}
