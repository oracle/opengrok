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
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import opengrok.auth.plugin.ldap.LdapServer;
import opengrok.auth.plugin.util.WebHook;
import opengrok.auth.plugin.util.WebHooks;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 * @author Krystof Tulinger
 */
class ConfigurationTest {

    /**
     * Create sample configuration object, encode it to a byte array, decode it to a new object,
     * compare the string representations of the two objects and some of the members.
     * @throws IOException on I/O error
     */
    @Test
    void testEncodeDecode() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Configuration configuration1 = createSampleConfiguration();
        configuration1.encodeObject(out);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        Configuration configuration2 = Configuration.decodeObject(in);
        assertNotNull(configuration2);
        assertEquals(configuration1.getObjectRepresentationAsString(),
                configuration2.getObjectRepresentationAsString());

        // Check some of the properties as a smoke test.
        assertEquals(configuration1.getInterval(), configuration2.getInterval());
        assertEquals(configuration1.getServers().size(), configuration2.getServers().size());
        assertEquals(configuration1.getServers().get(0).getUrl(), configuration2.getServers().get(0).getUrl());
        assertEquals(configuration1.getWebHooks().getFail().getURI(), configuration2.getWebHooks().getFail().getURI());
    }

    private static @NotNull Configuration createSampleConfiguration() {
        Configuration configuration = new Configuration();
        configuration.setInterval(500);
        configuration.setSearchTimeout(1000);
        configuration.setConnectTimeout(42);
        configuration.setCountLimit(10);
        LdapServer ldapServer1 = new LdapServer("ldap://localhost");
        LdapServer ldapServer2 = new LdapServer("ldaps://example.com", "username", "password");
        configuration.setServers(new ArrayList<>(List.of(ldapServer1, ldapServer2)));
        WebHooks webHooks = new WebHooks();
        WebHook hook = new WebHook();
        hook.setContent("foo");
        hook.setURI("http://localhost:8080/source/api/v1/messages");
        webHooks.setFail(hook);
        configuration.setWebHooks(webHooks);
        return configuration;
    }

    @Test
    void testReadCacheValid() throws IOException {
        URL url = ConfigurationTest.class.getClassLoader().
                getResource("opengrok/auth/plugin/configuration/plugin-config.yml");
        assertNotNull(url);
        File testFile = new File(url.getFile());
        Configuration config = Configuration.read(testFile);
        assertNotNull(config);
        assertEquals(2, config.getServers().size());
    }
}
