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
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */

package org.opensolaris.opengrok.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import static org.junit.Assert.assertNotNull;
import org.opensolaris.opengrok.analysis.CtagsReader;
import org.opensolaris.opengrok.analysis.Definitions;
import org.opensolaris.opengrok.analysis.StreamSource;

/**
 * Represents a container for stream utility methods
 */
public class StreamUtils {
    /**
     * Read a stream fully to an in-memory byte array.
     * @param iss a defined stream at the chosen starting position
     * @return a defined instance
     * @throws IOException if I/O fails
     */
    public static byte[] copyStream(InputStream iss) throws IOException {

        ByteArrayOutputStream baosExp = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        do {
            read = iss.read(buffer, 0, buffer.length);
            if (read > 0) {
                baosExp.write(buffer, 0, read);
            }
        } while (read >= 0);

        baosExp.close();
        return baosExp.toByteArray();
    }

    /**
     * Reads lines from the specified UTF-8 {@link InputStream}, stripping off
     * any content rightward if a comment token -- space-hash, {@code " #"}, or
     * tab-hash, {@code "\t#"} -- is present beyond the first column, and
     * removing any trailing whitespace.
     * @param expectedSymbols a required instance to append
     * @param stream a required instance
     * @throws IOException if any I/O error occurs
     */
    public static void readExpectedSymbols(List<String> expectedSymbols,
            InputStream stream) throws IOException {
        try (BufferedReader rwords = new BufferedReader(new InputStreamReader(
                stream, "UTF-8"))) {
            String line;
            while ((line = rwords.readLine()) != null) {
                int coff;
                if ((coff = line.indexOf(" #")) > 0 ||
                        (coff = line.indexOf("\t#")) > 0) {
                    line = line.substring(0, coff);
                }
                expectedSymbols.add(line.replaceFirst("\\s$", ""));
            }
        }
    }

    /**
     * Reads lines from the specified UTF-8 {@link InputStream}, stripping off
     * any content rightward if a comment token -- space-hash, {@code " #"}, or
     * tab-hash, {@code "\t#"} -- is present beyond the first column; then
     * optionally stripping off rightward and parsing an integer value if a
     * vertical bar -- {@code '|'} -- is present beyond the first column; and
     * then removing any trailing whitespace if either a comment or number was
     * present.
     * @param expectedSymbols a required instance to append
     * @param stream a required instance
     * @throws IOException if any I/O error occurs
     */
    public static void readExpectedSymbols2(
        List<SimpleEntry<String, Integer>> expectedSymbols,
        InputStream stream) throws IOException {

        try (BufferedReader rwords = new BufferedReader(new InputStreamReader(
                stream, "UTF-8"))) {
            int lineno = 0;
            String line;
            while ((line = rwords.readLine()) != null) {
                ++lineno;
                int coff;
                if ((coff = line.indexOf(" #")) > 0 ||
                        (coff = line.indexOf("\t#")) > 0) {
                    line = line.substring(0, coff).replaceFirst("\\s$", "");
                }

                String word = line;
                Integer v = null;

                coff = line.indexOf('|');
                if (coff > 0) {
                    word = line.substring(0, coff).replaceFirst("\\s$", "");
                    String vstr = line.substring(coff + 1);
                    try {
                        v = Integer.parseInt(vstr.trim());
                    } catch (NumberFormatException e) {
                        throw new NumberFormatException("line " + lineno +
                            ": " + e.getMessage());
                    }
                }
                expectedSymbols.add(new SimpleEntry<>(word, v));
            }
        }
    }

    public static Definitions readTagsFromResource(String tagsResourceName)
            throws IOException {
        return readTagsFromResource(tagsResourceName, null);
    }

    public static Definitions readTagsFromResource(String tagsResourceName,
            String rawResourceName) throws IOException {
        return readTagsFromResource(tagsResourceName, rawResourceName, 0);
    }

    public static Definitions readTagsFromResource(String tagsResourceName,
        String rawResourceName, int tabSize) throws IOException {

        InputStream res = StreamUtils.class.getClassLoader().
            getResourceAsStream(tagsResourceName);
        assertNotNull(tagsResourceName + " as resource", res);

        BufferedReader in = new BufferedReader(new InputStreamReader(
            res, "UTF-8"));

        CtagsReader rdr = new CtagsReader();
        rdr.setTabSize(tabSize);
        if (rawResourceName != null) {
            rdr.setSplitterSupplier(() -> {
                /**
                 * This should return truly raw content, as the CtagsReader will
                 * expand tabs according to its setting.
                 */
                SourceSplitter splitter = new SourceSplitter();
                StreamSource src = sourceFromEmbedded(rawResourceName);
                try {
                    splitter.reset(src);
                } catch (IOException ex) {
                    System.err.println(ex.toString());
                    return null;
                }
                return splitter;
            });
        }

        String line;
        while ((line = in.readLine()) != null) {
            rdr.readLine(line);
        }
        return rdr.getDefinitions();
    }

    /**
     * Creates a {@code StreamSource} instance that reads data from an
     * embedded resource.
     * @param resourceName a required resource name
     * @return a stream source that reads from {@code name}
     */
    public static StreamSource sourceFromEmbedded(String resourceName) {
        return new StreamSource() {
            @Override
            public InputStream getStream() throws IOException {
                InputStream res = StreamUtils.class.getClassLoader().
                    getResourceAsStream(resourceName);
                assertNotNull("resource " + resourceName, res);
                return new BufferedInputStream(res);
            }
        };
    }

    /** private to enforce static */
    private StreamUtils() {
    }
}
