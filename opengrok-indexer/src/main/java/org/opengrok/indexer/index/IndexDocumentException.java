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
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.index;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Exception thrown when index document check fails.
 */
public class IndexDocumentException extends IndexCheckException {
    private static final long serialVersionUID = -277429315137557112L;

    private final transient Map<Path, Integer> duplicatePathMap;
    private final transient Set<Path> missingPaths;

    public IndexDocumentException(String s, Path path) {
        super(s, path);
        this.duplicatePathMap = null;
        this.missingPaths = null;
    }

    public IndexDocumentException(String message, Path indexPath, Map<Path, Integer> duplicateFileMap, Set<Path> missingPaths) {
        super(message, indexPath);
        this.duplicatePathMap = duplicateFileMap;
        this.missingPaths = missingPaths;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getMessage());
        stringBuilder.append(":");
        if (!duplicatePathMap.isEmpty()) {
            stringBuilder.append(" duplicate paths = ");
            stringBuilder.append(duplicatePathMap);
        }
        if (!missingPaths.isEmpty()) {
            stringBuilder.append(" missing paths = ");
            stringBuilder.append(missingPaths);
        }
        return stringBuilder.toString();
    }
}
