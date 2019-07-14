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

package org.opengrok.indexer.history;

import java.io.Closeable;
import java.util.Enumeration;

/**
 * Represents an API for a sequence of {@link History} instances where the
 * sequence is {@link Closeable} to release resources.
 */
public interface HistoryEnumeration extends Enumeration<History>, Closeable {

    /**
     * Returns the exit value for the subprocess.
     *
     * @return the exit value of the subprocess represented by this instance.
     * By convention, the value {@code 0} indicates normal termination.
     * @throws IllegalThreadStateException if the subprocess represented by
     * this instance has not yet terminated
     */
    int exitValue();
}
