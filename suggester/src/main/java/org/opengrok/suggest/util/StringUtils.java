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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
 */

package org.opengrok.suggest.util;

/**
 * Various String utility methods taken from
 * org.opensolaris.opengrok.util.StringUtils
 *
 * @author austvik
 */
public final class StringUtils {

  /**
   * Convert value in milliseconds to readable time.
   * @param time_ms delta in milliseconds
   * @return human readable string
   */
  public static String getReadableTime(long time_ms) {
      String output = "";
      long time_delta = time_ms;

      int milliseconds = (int) (time_delta % 1000);
      time_delta /= 1000;
      int seconds = (int) (time_delta % 60);
      time_delta /= 60;
      int minutes = (int) (time_delta % 60);
      time_delta /= 60;
      int hours = (int) (time_delta % 24);
      int days = (int) (time_delta / 24);

      if (days != 0) {
          output += String.format("%d day", days);
          if (days > 1) {
              output += "s";
          }
          if ((hours != 0) || (minutes != 0) || (seconds != 0)) {
              output += " ";
          }
      }
      if ((hours != 0) || (minutes != 0)) {
          return output + String.format("%d:%02d:%02d", hours, minutes, seconds);
      }
      if (seconds != 0) {
          return output + String.format("%d.%d seconds", seconds, milliseconds);
      }
      if (milliseconds != 0) {
          return output + String.format("%d ms", milliseconds);
      }

      return (output.length() == 0 ? "0" : output);
  }
}
