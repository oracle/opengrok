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
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

/**
 * Exception thrown when retrieval or manipulation of history information
 * fails.
 */
public class HistoryException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Construct a {@code HistoryException} with the specified message.
     *
     * @param msg the message string
     */
    public HistoryException(String msg) {
        super(msg);
    }

    /**
     * Construct a {@code HistoryException} with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public HistoryException(Throwable cause) {
        super(cause);
    }

    /**
     * Construct a {@code HistoryException} with the specified message
     * and cause.
     *
     * @param msg the message string
     * @param cause the cause of the exception
     */
    public HistoryException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
