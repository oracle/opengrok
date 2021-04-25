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
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.configuration;

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import opengrok.auth.plugin.ldap.LdapServer;
import opengrok.auth.plugin.util.WebHook;
import opengrok.auth.plugin.util.WebHooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 * @author Krystof Tulinger
 */
class ConfigurationTest {

    @Test
    void testEncodeDecode() {
        // Create an exception listener to detect errors while encoding and
        // decoding
        final LinkedList<Exception> exceptions = new LinkedList<>();
        ExceptionListener listener = exceptions::addLast;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLEncoder enc = new XMLEncoder(out);
        enc.setExceptionListener(listener);

        Configuration configuration1 = new Configuration();
        configuration1.setInterval(500);
        configuration1.setSearchTimeout(1000);
        configuration1.setConnectTimeout(42);
        configuration1.setCountLimit(10);
        configuration1.setServers(new ArrayList<>(List.of(new LdapServer("http://server.com"))));
        WebHooks webHooks = new WebHooks();
        WebHook hook = new WebHook();
        hook.setContent("foo");
        hook.setURI("http://localhost:8080/source/api/v1/messages");
        webHooks.setFail(hook);
        configuration1.setWebHooks(webHooks);

        enc.writeObject(configuration1);
        enc.close();

        // verify that the write didn't fail
        if (!exceptions.isEmpty()) {
            throw new AssertionError( "Got " + exceptions.size() + " exception(s)", exceptions.getFirst());
        }

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        XMLDecoder dec = new XMLDecoder(in, null, listener);
        Configuration configuration2 = (Configuration) dec.readObject();
        assertNotNull(configuration2);
        assertEquals(configuration1.getXMLRepresentationAsString(),
                configuration2.getXMLRepresentationAsString());

        dec.close();
        // verify that the read didn't fail
        if (!exceptions.isEmpty()) {
            throw new AssertionError( "Got " + exceptions.size() + " exception(s)", exceptions.getFirst());
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
    void testReadCacheValid() throws IOException {
        File testFile = new File(ConfigurationTest.class.getClassLoader().
                getResource("opengrok/auth/plugin/configuration/plugin-config.xml").getFile());
        Configuration config = Configuration.read(testFile);
        assertNotNull(config);
        assertEquals(2, config.getServers().size());
    }
}
