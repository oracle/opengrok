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
 * Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import java.io.File;

/**
 * Represents a validator of a --canonicalRoot value.
 */
public class CanonicalRootValidator {

    /**
     * Validates the specified setting, returning an error message if validation
     * fails.
     * @return a defined instance if not valid or {@code null} otherwise
     */
    public static String validate(String setting, String elementDescription) {
        // Test that the value ends with the system separator.
        if (!setting.endsWith(File.separator)) {
            return elementDescription + " must end with a separator";
        }
        // Test that the value is not like a Windows root (e.g. C:\ or Z:/).
        if (setting.matches("\\w:(?:[/\\\\]*)")) {
            return elementDescription + " cannot be a root directory";
        }
        // Test that some character other than separators is in the value.
        if (!setting.matches(".*[^/\\\\].*")) {
            return elementDescription + " cannot be the root directory";
        }

        /*
         * There is no need to validate that the caller has not specified e.g.
         * "/./" (i.e. an alias to the root "/") because --canonicalRoot values
         * are matched via string comparison against true canonical values got
         * via File.getCanonicalPath(). An alias like "/./" is therefore a
         * never-matching value and hence inactive.
         */
        return null;
    }

    /** Private to enforce static. */
    private CanonicalRootValidator() {

    }
}
