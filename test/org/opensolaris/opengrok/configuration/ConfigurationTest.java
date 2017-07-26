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

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import junit.framework.AssertionFailedError;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 * @author Krystof Tulinger
 */
public class ConfigurationTest {

    @Test
    public void serializationOrderTest() throws IOException {
        Project project = new Project("project");
        Group apache = new Group("Apache", "test.*");
        Group bsd = new Group("BSD", "test.*");
        Group opensource = new Group("OpenSource", "test.*");

        opensource.addGroup(apache);
        opensource.addGroup(bsd);

        bsd.addProject(project);
        opensource.addProject(project);

        project.getGroups().add(opensource);
        project.getGroups().add(bsd);

        Configuration cfg = new Configuration();
        Configuration oldCfg = cfg;
        cfg.addGroup(apache);
        cfg.addGroup(bsd);
        cfg.addGroup(opensource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XMLEncoder enc = new XMLEncoder(out)) {
            enc.writeObject(cfg);
        }

        // Create an exception listener to detect errors while encoding and
        // decoding
        final LinkedList<Exception> exceptions = new LinkedList<>();

        try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
                XMLDecoder dec = new XMLDecoder(in, null, (Exception e) -> {
                    exceptions.addLast(e);
                })) {

            cfg = (Configuration) dec.readObject();
            assertNotNull(cfg);
            dec.close();

            // verify that the read didn't fail
            if (!exceptions.isEmpty()) {
                AssertionFailedError afe = new AssertionFailedError(
                        "Got " + exceptions.size() + " exception(s)");
                // Can only chain one of the exceptions. Take the first one.
                afe.initCause(exceptions.getFirst());
                throw afe;
            }

            assertEquals(oldCfg.getGroups(), cfg.getGroups());
        }
    }
}
