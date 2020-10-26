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
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web.util;

import org.opengrok.indexer.configuration.RuntimeEnvironment;

import java.io.File;
import java.io.FileNotFoundException;

public class FileUtil {

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    // private to enforce static
    private FileUtil() {
    }

    /**
     * @param path path relative to source root
     * @return file object corresponding to the file under source root
     * @throws FileNotFoundException if the file constructed from the {@code path} parameter and source root does not exist
     * @throws NoPathParameterException if the {@code path} parameter is null
     */
    public static File toFile(String path) throws NoPathParameterException, FileNotFoundException {
        if (path == null) {
            throw new NoPathParameterException("Missing path parameter");
        }

        File file = new File(env.getSourceRootFile(), path);
        if (!file.isFile()) {
            throw new FileNotFoundException("File " + file.toString() + "not found");
        }

        return file;
    }
}
