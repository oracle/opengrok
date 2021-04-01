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
 * Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.executables;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.AnalyzerGuru;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Represents a container for tests of {@link JavaClassAnalyzerFactory}.
 */
public class JavaClassAnalyzerFactoryTest {

    /**
     * Tests a Java .class file.
     * @throws IOException I/O exception
     */
    @Test
    public void testJavaClassWrtAnalyzerGuru() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/executables/javaclass.bin");
        assertNotNull(res, "despite inclusion locally,");

        // assert that it is matched
        AnalyzerFactory fac = AnalyzerGuru.find(res);
        assertNotNull(fac, "javaclass.bin should have factory");
        assertSame(fac.getClass(), JavaClassAnalyzerFactory.class, "should be JavaClassAnalyzerFactory");
    }

    /**
     * Tests a dylib with spurious CAFEBABE.
     * @throws IOException I/O exception
     */
    @Test
    public void testDylibCafebabeWrtAnalyzerGuru() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream(
            "analysis/executables/fat.dylib");
        assertNotNull(res, "despite inclusion locally,");

        AnalyzerFactory fac = AnalyzerGuru.find(res);
        if (fac != null) {
            assertNotSame(fac.getClass(), JavaClassAnalyzerFactory.class, "should not be JavaClassAnalyzerFactory");
        }
    }
}
