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

package org.opengrok.indexer.util;

import org.junit.Assert;
import org.junit.Test;

public class BooleanUtilTest {

  // Test written by Diffblue Cover.
  @Test
  public void isBooleanInputNotNullOutputFalse() {

    // Arrange
    final String value = "3";

    // Act
    final boolean actual = BooleanUtil.isBoolean(value);

    // Assert result
    Assert.assertFalse(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isBooleanInputNotNullOutputTrue() {

    // Arrange
    final String value = "1";

    // Act
    final boolean actual = BooleanUtil.isBoolean(value);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void isBooleanInputNotNullOutputTrue2() {

    // Arrange
    final String value = "faLSe";

    // Act
    final boolean actual = BooleanUtil.isBoolean(value);

    // Assert result
    Assert.assertTrue(actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void toIntegerInputFalseOutputZero() {

    // Arrange
    final boolean b = false;

    // Act
    final int actual = BooleanUtil.toInteger(b);

    // Assert result
    Assert.assertEquals(0, actual);
  }

  // Test written by Diffblue Cover.
  @Test
  public void toIntegerInputTrueOutputPositive() {

    // Arrange
    final boolean b = true;

    // Act
    final int actual = BooleanUtil.toInteger(b);

    // Assert result
    Assert.assertEquals(1, actual);
  }
}
