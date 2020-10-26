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
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Before;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;

/**
 *
 * @author Vladimir Kotal
 */
public class RepositoryInfoTest {
    @Before
    public void setUp() {
        RuntimeEnvironment.getInstance().setSourceRoot("/src");
    }
    
    @Test
    public void testEquals() {
        String repoDirectory = "/src/foo";
        
        RepositoryInfo ri1 = new RepositoryInfo();
        ri1.setDirectoryName(new File(repoDirectory));
        ri1.setBranch("branch1");
        
        RepositoryInfo ri2 = new RepositoryInfo();
        assertNotEquals(ri1, ri2);
        
        ri2.setDirectoryName(new File(repoDirectory));
        assertEquals(ri1, ri2);
    }
}
