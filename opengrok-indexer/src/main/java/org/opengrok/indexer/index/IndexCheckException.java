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
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Common exception for all check modes.
 */
public class IndexCheckException extends Exception {
    private static final long serialVersionUID = 5693446916108385595L;

    private final Set<Path> failedPaths = new HashSet<>();

    public IndexCheckException(String s, Path path) {
        super(s);
        failedPaths.add(path);
    }

    public IndexCheckException(String s, Set<Path> paths) {
        super(s);
        failedPaths.addAll(paths);
    }

    public Set<Path> getFailedPaths() {
        return Collections.unmodifiableSet(failedPaths);
    }
}
