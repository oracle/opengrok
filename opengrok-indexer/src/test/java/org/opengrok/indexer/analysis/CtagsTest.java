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
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opengrok.indexer.util.TestRepository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Lubos Kosco
 */
public class CtagsTest {

    private static Ctags ctags;
    private static TestRepository repository;

    @BeforeAll
    public static void setUpClass() throws Exception {
        ctags = new Ctags();

        repository = new TestRepository();
        repository.create(CtagsTest.class.getClassLoader().getResource("sources"));

        /*
         * This setting is only needed for bug19195 but it does not seem
         * that it is possible to specify it just for single test case.
         * The config file contains assembly specific settings so it should
         * not be harmful to other test cases.
         */
        String extraOptionsFile =
                repository.getSourceRoot() + "/bug19195/ctags.config";
        ctags.setCTagsExtraOptionsFile(extraOptionsFile);
    }

    @AfterAll
    public static void tearDownClass() {
        ctags.close();
        ctags = null;
        repository.destroy();
        repository = null;
    }

    /**
     * Helper method that gets the definitions for a file in the repository.
     * @param fileName file name relative to source root
     * @return the definitions found in the file
     */
    private static Definitions getDefs(String fileName) throws Exception {
        String path = repository.getSourceRoot() + File.separator
                + fileName.replace('/', File.separatorChar);
        return ctags.doCtags(new File(path).getAbsolutePath());
    }

    /**
     * Test of doCtags method, of class Ctags.
     */
    @Test
    void testDoCtags() throws Exception {
        Definitions result = getDefs("bug16070/arguments.c");
        assertEquals(13, result.numberOfSymbols());
    }

    /**
     * Test that we don't get many false positives in the list of method
     * definitions for Java files. Bug #14924.
     */
    @Test
    void bug14924() throws Exception {
        // Expected method names found in the file
        String[] names = {"ts", "classNameOnly", "format"};
        // Expected line numbers for the methods
        int[] lines = {44, 48, 53};

        Definitions result = getDefs("bug14924/FileLogFormatter.java");
        int count = 0;
        for (Definitions.Tag tag : result.getTags()) {
            if (tag.type.startsWith("method")) {
                assertTrue(count < names.length, "too many methods");
                assertEquals(names[count], tag.symbol, "method name");
                assertEquals(lines[count], tag.line, "method line");
                count++;
            }
        }
        assertEquals(names.length, count, "method count");
    }

    /**
     * Test that multiple extra command line options are processed correctly
     * for assembler source code. Bug #19195.
     */
    @Test
    void bug19195() throws Exception {
        // Expected method names found in the file
        String[] names = {"foo", "bar", "_fce", "__fce"};
        // Expected line numbers for the methods
        int[] lines = {26, 49, 69, 69};

        /* Perform the actual test. */
        Definitions result = getDefs("bug19195/test.s");
        int count = 0;
        for (Definitions.Tag tag : result.getTags()) {
            if (tag.type.startsWith("function")) {
                assertTrue(count < names.length, "too many functions");
                assertEquals(names[count], tag.symbol, "function name");
                assertEquals(lines[count], tag.line, "function line");
                count++;
            }
        }
        assertEquals(names.length, count, "function count");
    }

    @ParameterizedTest
    @MethodSource("terraformTestParams")
    void testTfTags(final SingleTagTestData data) throws Exception {
        var defs = getDefs("terraform/" + data.file + ".tf");
        assertAll(
                () -> assertEquals(1, defs.getTags().size()),
                () -> assertEquals(data.symbol, defs.getTags().get(0).symbol),
                () -> assertEquals(data.type, defs.getTags().get(0).type)
        );
    }

    private static List<SingleTagTestData> terraformTestParams() {
        return List.of(
                new SingleTagTestData("data_source", "aws_ami.example", "dataSource"),
                new SingleTagTestData("module", "servers", "module"),
                new SingleTagTestData("output_value", "instance_ip_addr", "outputValue"),
                new SingleTagTestData("provider", "oci", "provider"),
                new SingleTagTestData("resource", "oci_core_vcn.test_vcn", "resource"),
                new SingleTagTestData("variable", "availability_zone_names", "variable")
        );
    }

    private static class SingleTagTestData {
        private final String file;
        private final String symbol;
        private final String type;

        SingleTagTestData(final String file, final String symbol, final String type) {
            this.file = file;
            this.symbol = symbol;
            this.type = type;
        }
    }

}
