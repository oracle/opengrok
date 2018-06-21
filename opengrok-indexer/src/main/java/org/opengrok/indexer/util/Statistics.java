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
 * Copyright (c) 2014, 2018 Oracle and/or its affiliates. All rights reserved.
 */

package org.opengrok.indexer.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import static org.opengrok.indexer.util.StringUtils.getReadableTime;

public class Statistics {
        
  private final long startTime;  

  public Statistics() {
      startTime = System.currentTimeMillis();    
  }

  public void report(Logger log, String msg) {
      long stopTime = System.currentTimeMillis();
      String time_str = StringUtils.getReadableTime(stopTime - startTime);
      log.log(Level.INFO, msg + " (took {0})", time_str);
  }

  public void report(Logger log) {
    long stopTime = System.currentTimeMillis() - startTime;
    log.log(Level.INFO, "Total time: {0}", getReadableTime(stopTime));

    System.gc();
    Runtime r = Runtime.getRuntime();
    long mb = 1024L * 1024;
    log.log(Level.INFO, "Final Memory: {0}M/{1}M", 
            new Object[]{(r.totalMemory() - r.freeMemory()) / 
                    mb, r.totalMemory() / mb});    
  }    
}
