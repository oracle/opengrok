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
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.history;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Executor;

/**
 * Get mapping of revision to author.
 */
public class SCCSRepositoryAuthorParser implements Executor.StreamHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SCCSRepositoryAuthorParser.class);

    private final Map<String, String> authors = new HashMap<>();

    /**
     * Pattern used to extract revision from the {@code sccs get} command.
     */
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("^([\\d.]+)\\s+(\\S+)");

    @Override
    public void processStream(InputStream input) throws IOException {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(input))) {
            String line;
            int lineno = 0;
            while ((line = in.readLine()) != null) {
                ++lineno;
                Matcher matcher = AUTHOR_PATTERN.matcher(line);
                if (matcher.find()) {
                    String rev = matcher.group(1);
                    String auth = matcher.group(2);
                    authors.put(rev, auth);
                } else {
                    LOGGER.log(Level.WARNING,
                            "Error: did not find authors in line {0}: [{1}]",
                            new Object[]{lineno, line});
                }
            }
        }
    }

    /**
     * @return map of revision to author
     */
    public Map<String, String> getAuthors() {
        return authors;
    }
}
