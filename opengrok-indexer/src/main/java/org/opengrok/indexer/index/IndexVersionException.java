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

/**
 * Exception thrown when index version does not match Lucene version.
 */
public class IndexVersionException extends IndexCheckException {

    private static final long serialVersionUID = 5693446916108385595L;

    private final int luceneIndexVersion;
    private final int indexVersion;

    public IndexVersionException(String message, Path path, int luceneIndexVersion, int indexVersion) {
        super(message, path);
        this.indexVersion = indexVersion;
        this.luceneIndexVersion = luceneIndexVersion;
    }

    @Override
    public String toString() {
        return getMessage() + ": " + String.format("Lucene version = %d", luceneIndexVersion) + ", " +
                String.format("index version = %d", indexVersion);
    }
}
