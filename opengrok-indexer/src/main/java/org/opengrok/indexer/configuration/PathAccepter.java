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
 * Copyright (c) 2008, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import org.opengrok.indexer.logger.LoggerFactory;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a gatekeeper that decides whether a particular file or directory
 * is acceptable for history or code analysis with respect to configuration of
 * ignored files and directories or of specified inclusive filtering.
 */
public class PathAccepter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathAccepter.class);

    private final IgnoredNames ignoredNames;
    private final Filter includedNames;

    /**
     * Package-private to be initialized from the runtime environment.
     */
    PathAccepter(IgnoredNames ignoredNames, Filter includedNames) {
        this.ignoredNames = ignoredNames;
        this.includedNames = includedNames;
    }

    /**
     * Evaluates the specified {@code file} versus the runtime configuration of
     * ignored files and directories or of specified inclusive filtering, and
     * returns a value whether the {@code file} is to be accepted.
     * @param file a defined instance under the source root
     */
    public boolean accept(File file) {
        if (!includedNames.isEmpty()
                && // the filter should not affect directory names
                (!(file.isDirectory() || includedNames.match(file)))) {
            LOGGER.log(Level.FINER, "not including {0}", file.getAbsolutePath());
            return false;
        }

        if (ignoredNames.ignore(file)) {
            LOGGER.log(Level.FINER, "ignoring {0}", file.getAbsolutePath());
            return false;
        }

        return true;
    }
}
