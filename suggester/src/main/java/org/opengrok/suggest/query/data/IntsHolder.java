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
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.suggest.query.data;

/**
 * Simple interface for querying if some data structure contains some {@code int} value.
 */
public interface IntsHolder {

    /**
     * Determines whether the data structure contains {@code i} value.
     * @param i value which presence is checked
     * @return {@code true} if {@code i} is present. {@code false} otherwise.
     */
    boolean has(int i);

    /**
     * Returns number of elements.
     * @return number of elements.
     */
    int numberOfElements();

}
