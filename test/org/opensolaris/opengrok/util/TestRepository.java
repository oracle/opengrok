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
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import static org.junit.Assert.*;

/**
 * A source repository to be used during a test
 *
 * @author Trond Norbye
 */
public class TestRepository {

    private File sourceRoot;
    private File dataRoot;

    public void create(InputStream inputBundle) throws IOException {
        File sourceBundle = null;
        try {
            sourceRoot = File.createTempFile("source", "opengrok");
            dataRoot = File.createTempFile("data", "opengrok");
            sourceBundle = File.createTempFile("srcbundle", ".zip");

            if (sourceRoot.exists()) {
                assertTrue(sourceRoot.delete());
            }

            if (dataRoot.exists()) {
                assertTrue(dataRoot.delete());
            }

            if (sourceBundle.exists()) {
                assertTrue(sourceBundle.delete());
            }

            assertTrue(sourceRoot.mkdirs());
            assertTrue(dataRoot.mkdirs());

            assertNotNull(inputBundle);
            FileOutputStream out = new FileOutputStream(sourceBundle);
            FileUtilities.copyFile(inputBundle, out);
            out.close();
            FileUtilities.extractArchive(sourceBundle, sourceRoot);
            RuntimeEnvironment.getInstance().setSourceRootFile(sourceRoot);
            RuntimeEnvironment.getInstance().setDataRoot(dataRoot);
        } finally {
            if (sourceBundle != null) {
                sourceBundle.delete();
            }
        }
    }

    public void destroy() {
        if (sourceRoot != null) {
            FileUtilities.removeDirs(sourceRoot);
        }
        if (dataRoot != null) {
            FileUtilities.removeDirs(dataRoot);
        }
    }

    public File getSourceRoot() {
        return sourceRoot;
    }

    public File getDataRoot() {
        return dataRoot;
    }
}
