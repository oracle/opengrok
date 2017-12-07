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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.configuration;

/**
 * Represents a container for OpenGrok's names of Lucene lock modes.
 */
public class LuceneLockName {
    public static final String OFF = "off";
    /**
     * An alias for {@link #SIMPLE}
     */
    public static final String ON = "on";
    public static final String SIMPLE = "simple";
    public static final String NATIVE = "native";

    /** private to enforce static */
    private LuceneLockName() {
    }
}
