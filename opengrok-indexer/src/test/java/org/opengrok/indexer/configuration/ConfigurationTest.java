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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import junit.framework.AssertionFailedError;
import org.junit.Assert;
import org.junit.Test;
import org.opengrok.indexer.util.ClassUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.xml.sax.Attributes;
import org.xml.sax.ext.DefaultHandler2;

/**
 *
 * @author Krystof Tulinger
 */
public class ConfigurationTest {

    private static class Handler extends DefaultHandler2 {

        Handler() {
        }

        @Override
        public void startElement(String uri, String localName, String qname, Attributes attr) {
            if ("void".equals(qname)) {
                String prop = null;
                if ((prop = attr.getValue("property")) != null) {
                    for (Field f : Group.class.getDeclaredFields()) {
                        if (Modifier.isTransient(f.getModifiers())) {
                            Assert.assertFalse("'" + f.getName() +
                                "' field is transient and yet is present in XML " +
                                "encoding of Group object",
                                prop.equals(f.getName()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Verify that encoding of Group class does  not contain transient members.
     * @throws Exception exception
     */
    @Test
    public void testTransientKeywordGroups() throws Exception {
        Group foo = new Group("foo", "foo.*");
        Group bar = new Group("bar", "bar.*");

        Configuration cfg = new Configuration();
        cfg.addGroup(foo);
        foo.addGroup(bar);
        cfg.addGroup(bar);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XMLEncoder enc = new XMLEncoder(out)) {
            enc.writeObject(cfg);
        }

        // In this test we are no so much interested in exceptions during the
        // XML decoding as that is covered by the {@code serializationOrderTest}
        // test.
        try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); // Compliant
            saxParser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); // compliant
            Handler handler = new Handler();
            saxParser.parse(new BufferedInputStream(in), handler);
        }
    }

    /**
     * Test for a serialization bug in configuration. The problem is that with
     * this scenario the output is written in a way that when deserializing the
     * input later on, we get {@link NullPointerException} trying to use
     * {@link Group#compareTo(Group)}. This exception is caused by wrong order
     * of serialization of
     * {@link Group#getDescendants()}, {@link Group#getParent()} and
     * {@link Project#getGroups()} where the backpointers in a {@link Project}
     * to several {@link Group}s shall be stored in a set while this
     * {@link Group} does not have a name yet (= {@code null}).
     *
     * @throws IOException I/O exception
     * @see ClassUtil#remarkTransientFields(java.lang.Class)
     * ClassUtil#remarkTransientFields() for suggested solution
     */
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
