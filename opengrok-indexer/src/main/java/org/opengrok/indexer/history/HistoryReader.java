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
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.util.IOUtils;

/**
 * Class for reading history entries in a way suitable for indexing by Lucene.
 */
public class HistoryReader extends Reader {

    private final List<HistoryEntry> entries;
    private Reader input;

    public HistoryReader(History history) {
        entries = history.getHistoryEntries();
    }

    @Override
    public int read(char @NotNull [] cbuf, int off, int len) throws IOException {
        if (input == null) {
            input = createInternalReader();
        }
        return input.read(cbuf, off, len);
    }

    @Override
    public void close() throws IOException {
        IOUtils.close(input);
    }

    private Reader createInternalReader() {
        StringBuilder str = new StringBuilder();
        for (HistoryEntry entry : entries) {
            str.append(entry.getLine());
        }
        return new StringReader(str.toString());
    }
}
