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
package org.opensolaris.opengrok.history;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensolaris.opengrok.util.FileUtilities;
import static org.junit.Assert.*;

/**
 * Do basic testing of the Perforce support
 *
 * @author Trond Norbye
 */
public class PerforceRepositoryTest {

    private static boolean skip;
    private static List<File> files;
    private static File root = new File("/export/opengrok_p4_test");

    public PerforceRepositoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        if (!root.exists() || !(new PerforceRepository()).isWorking()) {
            skip = true;
            return;
        }
        files = new ArrayList<File>();
        FileUtilities.getAllFiles(root, files, false);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (skip) {
            return;
        }
    }

    @Test
    public void testUpdate() throws Exception {
        if (skip) {
            return;
        }
        PerforceRepository instance = new PerforceRepository();
        instance.setDirectoryName(root.getAbsolutePath());
        instance.update();
    }

    @Test
    public void testGetHistoryParser() {
        if (skip) {
            return;
        }

        PerforceRepository instance = new PerforceRepository();
        instance.setDirectoryName(root.getAbsolutePath());
        assertEquals(PerforceHistoryParser.class, instance.getHistoryParser());
    }

    @Test
    public void testGetDirectoryHistoryParser() {
        if (skip) {
            return;
        }

        PerforceRepository instance = new PerforceRepository();
        instance.setDirectoryName(root.getAbsolutePath());
        assertEquals(PerforceHistoryParser.class, instance.getDirectoryHistoryParser());
    }

    @Test
    public void testHistoryAndAnnotations() throws Exception {
        if (skip) {
            return;
        }

        PerforceRepository instance = new PerforceRepository();
        instance.setDirectoryName(root.getAbsolutePath());

        Class<? extends HistoryParser> parserClass = instance.getHistoryParser();
        assertEquals(PerforceHistoryParser.class, parserClass);
        HistoryParser parser = parserClass.newInstance();

        for (File f : files) {
            if (instance.fileHasHistory(f)) {
                History history = parser.parse(f, instance);
                assertNotNull("Failed to get history for: " + f.getAbsolutePath(), history);
                HistoryReader reader = new HistoryReader(history);

                while (reader.next()) {
                    InputStream in = instance.getHistoryGet(f.getParent(), f.getName(), reader.getRevision());
                    assertNotNull("Failed to get revision " + reader.getRevision() + " of " + f.getAbsolutePath(), in);
                    in.close();

                    if (instance.fileHasAnnotation(f)) {
                        assertNotNull("Failed to annotate: " + f.getAbsolutePath(), instance.annotate(f, reader.getRevision()));
                    }
                }
                reader.close();
            }
        }
    }
}