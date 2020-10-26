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
 * Copyright (c) 2006, 2020, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2019, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.util;

import org.opengrok.indexer.analysis.AnalyzerGuru;
import org.opengrok.indexer.analysis.Ctags;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CtagsUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CtagsUtil.class);

    public static final String SYSTEM_CTAGS_PROPERTY = "org.opengrok.indexer.analysis.Ctags";

    public static boolean validate(String ctagsBinary) {
        Executor executor = new Executor(new String[]{ctagsBinary, "--version"});
        executor.exec(false);
        String output = executor.getOutputString();
        boolean isUnivCtags = output != null && output.contains("Universal Ctags");
        if (output == null || !isUnivCtags) {
            LOGGER.log(Level.SEVERE, "Error: No Universal Ctags found !\n"
                            + "(tried running " + "{0}" + ")\n"
                            + "Please use the -c option to specify path to a "
                            + "Universal Ctags program.\n"
                            + "Or set it in Java system property {1}",
                    new Object[]{ctagsBinary, SYSTEM_CTAGS_PROPERTY});
            return false;
        }

        LOGGER.log(Level.INFO, "Using ctags: {0}", output.trim());

        return true;
    }

    /**
     * Gets the base set of languages by executing {@code --list-languages} for
     * the specified binary.
     * @return {@code null} on failure to run, or a defined list
     */
    public static List<String> getLanguages(String ctagsBinary) {
        Executor executor = new Executor(new String[]{ctagsBinary, "--list-languages"});
        int rc = executor.exec(false);
        String output = executor.getOutputString();
        if (output == null || rc != 0) {
            LOGGER.log(Level.WARNING, "Failed to get Ctags languages");
            return null;
        }

        output = output.replaceAll("\\s+\\[disabled]", "");
        String[] split = output.split("(?m)$");
        List<String> result = new ArrayList<>();
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
     * in case of timeout, @see Ctags#doCtags.
     */
    public static void deleteTempFiles() {
        String[] dirs = {System.getProperty("java.io.tmpdir"),
                System.getenv("TMPDIR"), System.getenv("TMP")};

        for (String dir : dirs) {
            deleteTempFiles(dir);
        }
    }

    private static void deleteTempFiles(String directoryName) {
        final Pattern pattern = Pattern.compile("tags\\.\\S{6}"); // ctags uses this pattern to call mkstemp()

        if (directoryName == null) {
            return;
        }

        File dir = new File(directoryName);
        File[] files = dir.listFiles((dir1, name) -> {
            Matcher matcher = pattern.matcher(name);
            return matcher.find();
        });

        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                LOGGER.log(Level.WARNING, "cannot delete file {0}", file);
            }
        }
    }

    /**
     * Creates a new instance, and attempts to configure it from the
     * environment.
     * @return a defined instance
     */
    public static Ctags newInstance(RuntimeEnvironment env) {
        Ctags ctags = new Ctags();
        ctags.setLangMap(AnalyzerGuru.getLangMap());

        String filename = env.getCTagsExtraOptionsFile();
        if (filename != null) {
            ctags.setCTagsExtraOptionsFile(filename);
        }
        return ctags;
    }

    /** Private to enforce static. */
    private CtagsUtil() {
    }
}
