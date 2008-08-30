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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

package org.opensolaris.opengrok.analysis.plain;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.history.Annotation;

public final class PlainAnalyzerFactory extends FileAnalyzerFactory {

    private static final Matcher MATCHER = new Matcher() {
            public FileAnalyzerFactory isMagic(byte[] content, InputStream in) {
                for(byte b: content) {
                    if ((b >= 32 && b < 127) || // ASCII printable characters
                            (b == 9)         || // horizontal tab
                            (b == 10)        || // line feed
                            (b == 12)        || // form feed
                            (b == 13)) {        // carriage return
                        // is plain text so far, go to next byte
                        continue;
                    } else {
                        // 8-bit values or unprintable control characters,
                        // probably not plain text
                        return null;
                    }
                }
                return DEFAULT_INSTANCE;
            }
        };

    public final static PlainAnalyzerFactory DEFAULT_INSTANCE =
            new PlainAnalyzerFactory();

    private PlainAnalyzerFactory() {
        super(null, null, null, MATCHER, "text/plain", Genre.PLAIN);
    }

    @Override
    protected FileAnalyzer newAnalyzer() {
        return new PlainAnalyzer(this);
    }

    @Override
    public void writeXref(InputStream in, Writer out, Annotation annotation, Project project)
        throws IOException
    {
        PlainAnalyzer.writeXref(in, out, annotation, project);
    }
}
