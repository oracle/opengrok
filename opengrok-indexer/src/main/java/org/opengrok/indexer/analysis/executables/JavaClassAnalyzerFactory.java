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
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.executables;

import java.io.InputStream;
import org.opengrok.indexer.analysis.AbstractAnalyzer;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzerFactory;

/**
 * Represents a subclass of {@link FileAnalyzerFactory} that creates
 * {@link JavaClassAnalyzer} instances for files that have: 1) a CLASS file
 * extension; or 2) {@code CAFEBABE} magic along with a known
 * {@code major_version} (per
 * https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html ),
 * currently from JDK 1.1 ({@code 0x2D}) to Java SE 9 ({@code 0x35}).
 */
public class JavaClassAnalyzerFactory extends FileAnalyzerFactory {

    private static final String name = "Java class";
    
    private static final String[] SUFFIXES = {
        "CLASS"
    };

    private static final byte[] CAFEBABE =
        new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
    private static final int MAJOR_VER_HIGHBYTE = 6;
    private static final int MAJOR_VER_LOWBYTE = 7;
    private static final int JDK1_1_MAJOR_VER = 0x2D;
    private static final int JAVA_SE_9_MAJOR_VER = 0x35;

    private static final Matcher MATCHER = new Matcher() {
        @Override
        public String description() {
            return "0xCAFEBABE magic with major_version from JDK 1.1 to Java" +
                " SE 9";
        }

        @Override
        public AnalyzerFactory isMagic(byte[] content, InputStream in) {
            if (content.length < 8) {
                return null;
            }

            // Require CAFEBABE or indicate no match.
            for (int i = 0; i < CAFEBABE.length; ++i) {
                if (content[i] != CAFEBABE[i]) {
                    return null;
                }
            }
            // Require known major_version number.
            int majorVersion = ((content[MAJOR_VER_HIGHBYTE] & 0xff) << 1) |
                    (content[MAJOR_VER_LOWBYTE] & 0xff);
            if (majorVersion >= JDK1_1_MAJOR_VER && majorVersion <=
                JAVA_SE_9_MAJOR_VER) {
                return JavaClassAnalyzerFactory.DEFAULT_INSTANCE;
            }
            return null;
        }

        @Override
        public AnalyzerFactory forFactory() {
            return JavaClassAnalyzerFactory.DEFAULT_INSTANCE;
        }
    };

    public static final JavaClassAnalyzerFactory DEFAULT_INSTANCE =
        new JavaClassAnalyzerFactory();

    private JavaClassAnalyzerFactory() {
        super(null, null, SUFFIXES, null, MATCHER, null, AbstractAnalyzer.Genre.XREFABLE, name);
    }

    /**
     * Creates a new {@link JavaClassAnalyzer} instance.
     * @return a defined instance
     */
    @Override
    protected AbstractAnalyzer newAnalyzer() {
        return new JavaClassAnalyzer(this);
    }
}
