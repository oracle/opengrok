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
 */
package org.opensolaris.opengrok.configuration;

import java.io.File;
import java.io.IOException;
import org.junit.Test;
import static org.opensolaris.opengrok.configuration.ConfigMerge.merge;

/**
 *
 * @author vkotal
 */
public class ConfigMergeTest {
    @Test
    public void basicTest() throws Exception {
        String baseFile = getClass().getResource("configuration.xml").getPath(),
               newFile = getClass().getResource("readonly_config.xml").getPath();

        Configuration cfgBase = null;
        try {
            cfgBase = Configuration.read(new File(baseFile));
        } catch (IOException ex) {
            System.err.println("cannot read base file " + baseFile + ":" + ex);
            System.exit(1);
        }

        Configuration cfgNew = null;
        try {
            cfgNew = Configuration.read(new File(newFile));
        } catch (IOException ex) {
            System.err.println("cannot read file " + newFile);
            System.exit(1);
        }

        merge(cfgBase, cfgNew);
    }
}
