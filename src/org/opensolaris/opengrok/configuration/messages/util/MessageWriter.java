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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration.messages.util;

import org.opensolaris.opengrok.configuration.messages.Message;

import java.io.IOException;
import java.io.OutputStream;

public class MessageWriter implements AutoCloseable {

    private final OutputStream outputStream;

    public MessageWriter(final OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("Cannot write to null output stream");
        }
        this.outputStream = out;
    }

    public void writeMessage(final Message message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Cannot write null message");
        }
        String encoded = message.getEncoded();
        outputStream.write(encoded.getBytes());
        outputStream.write(Message.DELIMITER.getBytes());
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

}
