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

package org.opengrok.indexer.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represents an API for parsing objects from a stream.
 */
public interface ObjectStreamHandler {

    /**
     * Initializes the handler to read from the specified input.
     */
    void initializeObjectStream(InputStream in);

    /**
     * Reads an object from the initialized input unless the stream has been
     * exhausted.
     * @return a defined instance or {@code null} if the stream has been
     * exhausted
     */
    Object readObject() throws IOException;
}
