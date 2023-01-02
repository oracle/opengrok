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
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.analysis.Definitions;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CtagsUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CtagsUtil.class);

    public static final String SYSTEM_CTAGS_PROPERTY = "org.opengrok.indexer.analysis.Ctags";

    /**
     * Check that {@code ctags} program exists and is working.
     * @param ctagsBinary name of the ctags program or path
     * @return true if the program works, false otherwise
     */
    public static boolean validate(String ctagsBinary) {
        if (!isUniversalCtags(ctagsBinary)) {
            return false;
        }

        return canProcessFiles(RuntimeEnvironment.getInstance().getSourceRootPath());
    }

    /**
     * Run ctags program on a known temporary file to be created under given path and see if it was possible
     * to get some symbols.
     * @param basePath path to use for storing the temporary file
     * @return true if at least one symbol was found, false otherwise
     */
    private static boolean canProcessFiles(String basePath) {
        Path inputPath = Path.of(basePath, "ctagsValidationTemporaryFile.c");
        final String resourceFileName = "sample.c";
        Path resourcePath = getResourceFile(resourceFileName);
        if (resourcePath == null) {
            return false;
        }

        try {
            if (inputPath.toFile().exists()) {
                Files.delete(inputPath);
            }
            Files.copy(resourcePath, inputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "cannot copy ''{0}'' to ''{1}'' for ctags check: {2}",
                    new Object[]{resourcePath, inputPath, e});
            return false;
        }

        Ctags ctags = new Ctags();
        try {
            Definitions definitions = ctags.doCtags(inputPath.toString());
            if (definitions.numberOfSymbols() > 1) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "cannot determine whether ctags can produce definitions", e);
        } finally {
            ctags.close();
            try {
                Files.delete(inputPath);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "cannot delete ''{0}''", inputPath);
            }
        }

        return false;
    }

    @Nullable
    private static Path getResourceFile(String resourceFileName) {
        ClassLoader classLoader = CtagsUtil.class.getClassLoader();
        URI resourceURI;
        try {
            URL resourceURL = classLoader.getResource(resourceFileName);
            if (resourceURL == null) {
                LOGGER.log(Level.SEVERE, "cannot get resource URL of ''{0}'' for ctags check",
                        resourceFileName);
                return null;
            }
            resourceURI = resourceURL.toURI();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "cannot perform ctags check due to missing resource file ''{0}''",
                    resourceFileName);
            return null;
        }

        return Path.of(resourceURI);
    }

    @Nullable
    public static String getCtagsVersion(String ctagsBinary) {
        Executor executor = new Executor(new String[]{ctagsBinary, "--version"});
        executor.exec(false);
        String output = executor.getOutputString();
        if (output != null) {
            output = output.trim();
        }

        return output;
    }

    private static boolean isUniversalCtags(String ctagsBinary) {
        String version = getCtagsVersion(ctagsBinary);
        boolean isUniversalCtags = version != null && version.contains("Universal Ctags");
        if (version == null || !isUniversalCtags) {
            LOGGER.log(Level.SEVERE, "Error: No Universal Ctags found !\n"
                            + "(tried running " + "{0}" + ")\n"
                            + "Please use the -c option to specify path to a "
                            + "Universal Ctags program.\n"
                            + "Or set it in Java system property {1}",
                    new Object[]{ctagsBinary, SYSTEM_CTAGS_PROPERTY});
            return false;
        }

        return true;
    }

    /**
     * Gets the base set of languages by executing {@code --list-languages} for the specified binary.
     * @return empty list on failure to run, or a defined list
     */
    public static Set<String> getLanguages(String ctagsBinary) {
        Executor executor = new Executor(new String[]{ctagsBinary, "--list-languages"});
        int rc = executor.exec(false);
        String output = executor.getOutputString();
        if (output == null || rc != 0) {
            LOGGER.log(Level.WARNING, "Failed to get Ctags languages");
            return Collections.emptySet();
        }

        output = output.replaceAll("\\s+\\[disabled]", "");
        String[] split = output.split("(?m)$");
        Set<String> result = new HashSet<>();
        for (String lang : split) {
            lang = lang.trim();
            if (lang.length() > 0) {
                result.add(lang);
            }
        }
        return result;
    }

    /**
     * Deletes Ctags temporary files left over after terminating Ctags processes
     * in case of timeout or Ctags crash, @see Ctags#doCtags.
     */
    public static void deleteTempFiles() {
        Set<String> dirs = new HashSet<>(Arrays.asList(System.getProperty("java.io.tmpdir"),
                System.getenv("TMPDIR"), System.getenv("TMP")));

        if (SystemUtils.IS_OS_UNIX) {
            // hard-coded TMPDIR in Universal Ctags on Unix.
            dirs.add("/tmp");
        }

        for (String directoryName : dirs) {
            if (directoryName == null) {
                continue;
            }

            File directory = new File(directoryName);
            if (!directory.isDirectory()) {
                continue;
            }

            LOGGER.log(Level.FINER, "deleting Ctags temporary files in directory {0}", directoryName);
            deleteTempFiles(directory);
        }
    }

    private static void deleteTempFiles(File directory) {
        final Pattern pattern = Pattern.compile("tags\\.\\S{6}"); // ctags uses this pattern to call mkstemp()

        File[] files = directory.listFiles((dir, name) -> {
            Matcher matcher = pattern.matcher(name);
            return matcher.find();
        });

        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                LOGGER.log(Level.WARNING, "cannot delete file {0}", file);
            }
        }
    }

    /** Private to enforce static. */
    private CtagsUtil() {
    }
}
