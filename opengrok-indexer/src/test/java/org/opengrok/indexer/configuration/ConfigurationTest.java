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
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.configuration;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opengrok.indexer.util.ClassUtil;

import org.xml.sax.Attributes;
import org.xml.sax.ext.DefaultHandler2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                            assertNotEquals(prop, f.getName(), "'" + f.getName() +
                                    "' field is transient and yet is present in XML " +
                                    "encoding of Group object");
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
                XMLDecoder dec = new XMLDecoder(in, null, exceptions::addLast)) {

            cfg = (Configuration) dec.readObject();
            assertNotNull(cfg);

            // verify that the read didn't fail
            if (!exceptions.isEmpty()) {
                // Can only chain one of the exceptions. Take the first one.
                throw new AssertionError("Got " + exceptions.size() + " exception(s)", exceptions.getFirst());
            }

            assertEquals(oldCfg.getGroups(), cfg.getGroups());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<java version=\"11.0.8\" class=\"java.beans.XMLDecoder\">\n" +
                    "  <object class=\"java.lang.Runtime\" method=\"getRuntime\">\n" +
                    "    <void method=\"exec\">\n" +
                    "      <array class=\"java.lang.String\" length=\"2\">\n" +
                    "        <void index=\"0\">\n" +
                    "          <string>/usr/bin/nc</string>\n" +
                    "        </void>\n" +
                    "        <void index=\"1\">\n" +
                    "          <string>-l</string>\n" +
                    "        </void>\n" +
                    "      </array>\n" +
                    "    </void>\n" +
                    "  </object>\n" +
                    "</java>",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<java version=\"11.0.8\" class=\"java.beans.XMLDecoder\">\n" +
                    "  <object class=\"java.lang.ProcessBuilder\">\n" +
                    "    <array class=\"java.lang.String\" length=\"1\" >\n" +
                    "      <void index=\"0\"> \n" +
                    "        <string>/usr/bin/curl https://oracle.com</string>\n" +
                    "      </void>\n" +
                    "    </array>\n" +
                    "    <void method=\"start\"/>\n" +
                    "  </object>\n" +
                    "</java>",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<java version=\"11.0.8\" class=\"java.beans.XMLDecoder\">\n" +
                    "  <object class = \"java.io.FileOutputStream\"> \n" +
                    "    <string>opengrok_test.txt</string>\n" +
                    "    <method name = \"write\">\n" +
                    "      <array class=\"byte\" length=\"3\">\n" +
                    "        <void index=\"0\"><byte>96</byte></void>\n" +
                    "        <void index=\"1\"><byte>96</byte></void>\n" +
                    "        <void index=\"2\"><byte>96</byte></void>\n" +
                    "      </array>\n" +
                    "    </method>\n" +
                    "    <method name=\"close\"/>\n" +
                    "  </object>\n" +
                    "</java>"
    })
    void testDeserializationOfNotWhiteListedClassThrowsError(final String exploit) {
        assertThrows(IllegalAccessError.class, () -> Configuration.makeXMLStringAsConfiguration(exploit));
    }

    @Test
    void testLoadingValidConfiguration() throws IOException {
        try (var br = new BufferedReader(new InputStreamReader(ConfigurationTest.class.getClassLoader()
                .getResourceAsStream("configuration/valid_configuration.xml")))) {
            String xml = br.lines().collect(Collectors.joining(System.lineSeparator()));
            var config = Configuration.makeXMLStringAsConfiguration(xml);
            assertEquals("/opt/opengrok_data", config.getDataRoot());
        }
    }

}
