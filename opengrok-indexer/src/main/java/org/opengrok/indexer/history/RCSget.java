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
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.suigeneris.jrcs.diff.PatchFailedException;
import org.suigeneris.jrcs.rcs.Archive;
import org.suigeneris.jrcs.rcs.InvalidFileFormatException;
import org.suigeneris.jrcs.rcs.impl.NodeNotFoundException;
import org.suigeneris.jrcs.rcs.parse.ParseException;
import org.opengrok.indexer.util.IOUtils;

/**
 * Virtualize RCS log as an input stream.
 */
public class RCSget extends InputStream {
    private InputStream stream;

    /**
     * Pass null in version to get current revision.
     * @param file file contents to get
     * @param version specified revision or @{code null}
     * @throws java.io.IOException if I/O exception occurred
     * @throws java.io.FileNotFoundException if the file cannot be found
     */
    public RCSget(String file, String version) throws IOException, FileNotFoundException {
        try {
            Archive archive = new Archive(file);
            Object[] lines;

            if (version == null) {
                lines = archive.getRevision(false);
            } else {
                lines = archive.getRevision(version, false);
            }

            StringBuilder sb = new StringBuilder();
            for (Object line : lines) {
                sb.append((String) line);
                sb.append("\n");
            }
            stream = new ByteArrayInputStream(sb.toString().getBytes());
        } catch (ParseException e) {
            throw RCSRepository.wrapInIOException("Parse error", e);
        } catch (InvalidFileFormatException e) {
            throw RCSRepository.wrapInIOException("Invalid RCS file format", e);
        } catch (PatchFailedException e) {
            throw RCSRepository.wrapInIOException("Patch failed", e);
        } catch (NodeNotFoundException e) {
            throw RCSRepository.wrapInIOException(
                    "Revision " + version + " not found", e);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        stream.reset();
    }

    @Override
    public void close() throws IOException {
        IOUtils.close(stream);
    }

    @Override
    public synchronized void mark(int readlimit) {
        stream.mark(readlimit);
    }

    @Override
    public int read(byte[] buffer, int pos, int len) throws IOException {
        return stream.read(buffer, pos, len);
    }

    @Override
    public int read() throws IOException {
        throw new IOException("use a BufferedInputStream. just read() is not supported!");
    }
}
