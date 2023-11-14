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
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Annotation Cache Related Operations.
 */
public interface AnnotationCache extends Cache {
    /**
     * Retrieve annotation from cache.
     * @param file file under source root to get the annotation for
     * @param rev requested revision
     * @return {@link Annotation} object or <code>null</code>
     * @throws CacheException on error
     */
    @Nullable
    Annotation get(File file, String rev) throws CacheException;

    /**
     * Store annotation for file into cache.
     * @param file file under source root to store the annotation for
     * @param annotation {@link Annotation} object
     * @throws CacheException on error
     */
    void store(File file, Annotation annotation) throws CacheException;

    /**
     * Clear annotation cache entry for given file.
     * @param path path to the file relative to source root
     */
    void clearFile(String path);
}
