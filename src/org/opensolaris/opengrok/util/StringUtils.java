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

package org.opensolaris.opengrok.util;

import java.util.regex.Pattern;

/**
 * Various String utility methods.
 *
 * @author austvik
 */
public final class StringUtils {

    private StringUtils() {
        // Only static utility methods
    }

    /**
     * Returns true if the string is empty or only includes whitespace characters.
     *
     * @param str the string to be checked
     * @return true if string is empty or only contains whitespace charadcters
     */
    public static boolean isOnlyWhitespace(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static final Pattern javaClassPattern =
        Pattern.compile("([a-z][A-Za-z]*\\.)+[A-Z][A-Za-z0-9]*");
    /**
     * Returns true if the string is possibly a full java class name
     *
     * @param s the string to be checked
     * @return true if string could be a java class name
    */
    public static boolean isPossiblyJavaClass(String s) {
    // Only match a small subset of possible class names to prevent false
    // positives:
    //    - class must be qualified with a package name
    //    - only letters in package name, starting with lower case
    //    - class name must be in CamelCase, starting with upper case
    return javaClassPattern.matcher(s).matches();
  }

  /**
   * Convert value in milliseconds to readable time.
   * @param time delta in milliseconds
   * @returns human readable string
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
