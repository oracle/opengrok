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
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.util.BufferSink;

/**
 *
 * @author Krystof Tulinger
 */
public class RepositoryTest {

    @Test
    public void testMultipleDateFormats() throws ParseException {
        String[][] tests = new String[][]{
            {"2016-01-01 10:00:00", "'abcd'", "yyyy", "yyyy-MM-dd HH:mm:ss"},
            {"2016 Sat, 5 Apr 2008 15:12:51 +0000", "yyyy d MMM yyyy HH:mm:ss Z", "yyyy EE, d MMM yyyy HH:mm:ss Z"},
            {"Sat, 5 Apr 2008 15:12:51 +0000", "d MMM yyyy Z", "EE, d MMM yyyy HH:mm:ss Z"},
            {"2016_01_01T10:00:00Z", "yyyy_MM_dd'T'HH:mm:ss'Z'"},
            {"2016-01-01 10:00", "yyyy-MM-dd", "yyyy-MM-dd HH", "yyyy-MM-dd HH:mm"},
            {"2016_01_01 10:00 +0000", "yyyy_MM_dd HH:mm Z"},
            {"2016-01-01 10:00:00 +0000", "yyyy-MM-dd HH:mm:ss Z", "'bad format'"},
            {"2016-01-01 10:00 +0000", "y-MM-d HH:mm", "yyyy-MM-dd HH:mm Z"},
            {"2016-01-02 10:00 +0000", "yyyy-MM HH:mm Z", "yyyy-MM-dd HH:mm Z"}
        };

        for (String[] test : tests) {
            RepositoryImplementation repository = new RepositoryImplementation();
            repository.setDatePatterns(Arrays.copyOfRange(test, 1, test.length));
            repository.parse(test[0]);
        }
    }

    @Test
    public void testDateFormats() throws ParseException {
        String[][] tests = new String[][]{
            {"2016-01-01 10:00:00", "yyyy-MM-dd HH:mm:ss"},
            {"2016 Sat, 5 Apr 2008 15:12:51 +0000", "yyyy EE, d MMM yyyy HH:mm:ss Z"},
            {"Sat, 5 Apr 2008 15:12:51 +0000", "EE, d MMM yyyy HH:mm:ss Z"},
            {"2016_01_01T10:00:00Z", "yyyy_MM_dd'T'HH:mm:ss'Z'"},
            {"2016-01-01 10:00", "yyyy-MM-dd HH:mm"},
            {"2016_01_01 10:00 +0000", "yyyy_MM_dd HH:mm Z"},
            {"2016-01-01 10:00:00 +0000", "yyyy-MM-dd HH:mm:ss Z"},
            {"2016-01-01 10:00 +0000", "yyyy-MM-dd HH:mm Z"},
            {"2016-01-02 10:00 +0000", "yyyy-MM-dd HH:mm Z"},
            {"2017-01-01 10:00 +0700", "yyyy-MM-dd HH:mm Z"},
            {"2016-12-31 10:00 +0000", "yyyy-MM-dd HH:mm Z"}
        };

        for (String[] test : tests) {
            RepositoryImplementation repository = new RepositoryImplementation();
            repository.setDatePatterns(new String[]{test[1]});
            repository.parse(test[0]);
        }
    }

    /**
     * Test if the repository will correctly throw an exception when no date
     * format is specified.
     */
    @Test
    public void testDateFormatsNoFormat() {
        String[] tests = new String[]{
            "abcd",
            "2016-01-01 10:00:00",
            "2016 Sat, 1 Apr 2008 15:12:51 +0000",
            "Sat, 1 Dub 2008 15:12:51 +0000",
            "2016_01_01T10:00:00Z",
            "2016-01-01 10:00",
            "2016_01_01 10:00 +0000",
            "2016-01-01 10:00:00 +0000",
            "2016-01-01 10:00 +0000",
            "2016-01-02 10:00 +0000",
            "2017-01-01 10:00 +0700",
            "2016-12-31 10:00 +0000"
        };

        RepositoryImplementation repository = new RepositoryImplementation();
        repository.setDatePatterns(new String[0]);

        for (String test : tests) {
            try {
                repository.parse(test);
                Assert.fail("Shouldn't be able to parse the date: " + test);
            } catch (ParseException ex) {
            }
        }
    }

    @Test
    public void testMultipleInvalidDateFormats() {
        String[][] tests = new String[][]{
            {"2016-01-01 10:00:00", "'abcd'", "MMM yy:ss ", "EE, d MMM yyyy Hss Z"},
            {"2016 Sat, 5 Apr 2008 15:12:51 +0000", "yyyy d MMM yy:ss Z", "yyyy EE, d MMM ss Z"},
            {"Sat, 5 Apr 2008 15:12:51 +0000", "d MMM yyyy Z", "EE, d MMM yyyy Hss Z"},
            {"2016_01_01T10:00:00Z", "yyyy_MM_dd'T'ss'Z'"},
            {"2016-01-01 10:00", "MM-dd Z", "EE-MM-dd HH", "Z dd-dd HH:mm"},
            {"2016_01_01 10:00 +0000", "yyyy-MM_dd HH:mm Z"},
            {"2016-01-01 10:00:00 +0000", "yyyy_MM_dd HH:s Z", "'bad format'"},
            {"2016-01-01 10:00 +0000", "MM-d HH:mm", "yyyy-MM-dmm Z"},
            {"2016-01-02 10:00 +0000", "yyyy-Mmm Z", "yyyy-M HH:mm Z"}
        };

        for (String[] test : tests) {
            RepositoryImplementation repository = new RepositoryImplementation();
            repository.setDatePatterns(Arrays.copyOfRange(test, 1, test.length));
            try {
                repository.parse(test[0]);
                Assert.fail("Shouldn't be able to parse the date: " + test[0]);
            } catch (ParseException ex) {
            }
        }
    }

    private class RepositoryImplementation extends Repository {
        private static final long serialVersionUID = 1686223058901603237L;

        @Override
        public boolean fileHasHistory(File file) {
            return false;
        }

        @Override
        public boolean hasHistoryForDirectories() {
            return false;
        }

        @Override
        public History getHistory(File file) throws HistoryException {
            return null;
        }

        @Override
        boolean getHistoryGet(
                BufferSink sink, String parent, String basename, String rev) {
            return false;
        }

        @Override
        public boolean fileHasAnnotation(File file) {
            return false;
        }

        @Override
        public Annotation annotate(File file, String revision) throws IOException {
            return null;
        }

        @Override
        public boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
            return false;
        }

        @Override
        public String determineParent(CommandTimeoutType cmdType) throws IOException {
            return "";
        }

        @Override
        public String determineBranch(CommandTimeoutType cmdType) throws IOException {
            return "";
        }

        @Override
        String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
            return null;
        }
    }
}
