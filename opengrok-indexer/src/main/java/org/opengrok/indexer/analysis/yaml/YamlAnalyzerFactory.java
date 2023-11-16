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
 * Copyright (c) 2023, Oracle and/or its affiliates.
 * Portions Copyright (c) 2023, Gino Augustine <gino.augustine@oracle.com>.
 */
package org.opengrok.indexer.analysis.yaml;

import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

public class YamlAnalyzerFactory extends FileAnalyzerFactory {

    private static final String NAME = "Yaml";

    private static final String[] SUFFIXES = {
        "YAML",
        "YML"
    };


    public YamlAnalyzerFactory() {
        super(null, null, SUFFIXES, null, null, "text/plain",
                AbstractAnalyzer.Genre.PLAIN, NAME);
    }

    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new YamlAnalyzer(this);
    }
}
