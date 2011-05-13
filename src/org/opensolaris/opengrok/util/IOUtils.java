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
 * Copyright (c) 2011 Trond Norbye
 */
package org.opensolaris.opengrok.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A small utility class to provide common functionality related to
 * IO so that we don't need to duplicate the logic all over the place.
 * 
 * @author Trond Norbye <trond.norbye@gmail.com>
 */
public final class IOUtils {

    private static final Logger log = Logger.getLogger(IOUtils.class.getName());

    private IOUtils() {
        // singleton
    }

    public static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close input stream: ", e);
            }
        }
    }

    public static void close(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close output stream: ", e);
            }
        }
    }

    public static void close(Reader in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close reader: ", e);
            }
        }
    }

    public static void close(Writer out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close writer: ", e);
            }
        }
    }
    
    public static void close(ServerSocket sock) {
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close socket: ", e);
            }
        }
    }

    public static void close(Socket sock) {
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close socket: ", e);
            }
        }
    }
}
