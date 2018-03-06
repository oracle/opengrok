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
 * Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Represents an API to allow associating a {@link MessageDigest} with an input
 * stream where OpenGrok can re-wrap the stream while carrying forward the
 * digest of the underlying data.
 */
public interface DigestedInputStream extends AutoCloseable {
    InputStream getStream();
    MessageDigest getMessageDigest();
    byte[] digestAll() throws IOException;
}
