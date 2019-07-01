package org.opengrok.indexer.util;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BooleanUtilTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  /* testedClasses: BooleanUtil */
  // Test written by Diffblue Cover.

  @Test
  public void constructorOutputVoid() {

    // Act, creating object to test constructor
    final BooleanUtil objectUnderTest = new BooleanUtil();

    // Method returns void, testing that no exception is thrown
  }

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
