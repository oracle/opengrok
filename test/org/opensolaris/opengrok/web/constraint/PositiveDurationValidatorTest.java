package org.opensolaris.opengrok.web.constraint;

import org.junit.Test;
import org.opensolaris.opengrok.web.constraints.PositiveDurationValidator;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PositiveDurationValidatorTest {

    private PositiveDurationValidator validator = new PositiveDurationValidator();

    @Test
    public void testNull() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    public void testNegative() {
        assertFalse(validator.isValid(Duration.ofMinutes(-10), null));
    }

    @Test
    public void testZero() {
        assertFalse(validator.isValid(Duration.ofMinutes(0), null));
    }

    @Test
    public void testValid() {
        assertTrue(validator.isValid(Duration.ofMinutes(5), null));
    }

}
