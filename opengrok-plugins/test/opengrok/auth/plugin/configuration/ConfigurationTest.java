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
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.configuration;

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import junit.framework.AssertionFailedError;
import opengrok.auth.plugin.ldap.LdapServer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 * @author Krystof Tulinger
 */
public class ConfigurationTest {

    @Test
    public void testEncodeDecode() {
        // Create an exception listener to detect errors while encoding and
        // decoding
        final LinkedList<Exception> exceptions = new LinkedList<>();
        ExceptionListener listener = new ExceptionListener() {
            @Override
            public void exceptionThrown(Exception e) {
                exceptions.addLast(e);
            }
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLEncoder enc = new XMLEncoder(out);
        enc.setExceptionListener(listener);

        Configuration configuration1 = new Configuration();
        configuration1.setInterval(500);
        configuration1.setServers(new ArrayList<>(Arrays.asList(new LdapServer("http://server.com"))));

        enc.writeObject(configuration1);
        enc.close();

        // verify that the write didn't fail
        if (!exceptions.isEmpty()) {
            AssertionFailedError afe = new AssertionFailedError(
                    "Got " + exceptions.size() + " exception(s)");
            // Can only chain one of the exceptions. Take the first one.
            afe.initCause(exceptions.getFirst());
            throw afe;
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
            AssertionFailedError afe = new AssertionFailedError(
                    "Got " + exceptions.size() + " exception(s)");
            // Can only chain one of the exceptions. Take the first one.
            afe.initCause(exceptions.getFirst());
            throw afe;
        }
    }
}
