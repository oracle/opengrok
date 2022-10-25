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
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.util.logging.Level;

/**
 * Exception thrown when retrieval or manipulation of cache data fails.
 * This is used for implementations of {@link HistoryCache} and {@link AnnotationCache}.
 */
public class CacheException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Level level;

    /**
     * Construct a {@code CacheException} with the specified message.
     *
     * @param msg the message string
     */
    public CacheException(String msg) {
        this(msg, Level.WARNING);
    }

    /**
     * Construct a {@code CacheException} with the specified message and log level.
     * @param msg message
     * @param level suggested log level
     */
    public CacheException(String msg, Level level) {
        super(msg);
        this.level = level;
    }

    /**
     * Construct a {@code CacheException} with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public CacheException(Throwable cause) {
        super(cause);
        this.level = Level.WARNING;
    }

    /**
     * Construct a {@code CacheException} with the specified message and cause.
     *
     * @param msg the message string
     * @param cause the cause of the exception
     */
    public CacheException(String msg, Throwable cause) {
        super(msg, cause);
        this.level = Level.WARNING;
    }

    /**
     * @return log {@link Level} to be used when logging the exception
     */
    public Level getLevel() {
        return level;
    }
}