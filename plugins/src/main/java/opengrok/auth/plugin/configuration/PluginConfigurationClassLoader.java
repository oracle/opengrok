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
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 */
package opengrok.auth.plugin.configuration;

import opengrok.auth.plugin.ldap.LdapServer;
import opengrok.auth.plugin.util.WebHook;
import opengrok.auth.plugin.util.WebHooks;

import java.beans.XMLDecoder;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Temporary hack to prevent {@link XMLDecoder} to deserialize other than allowed classes. This tries to prevent
 * calling of methods on {@link ProcessBuilder} or {@link Runtime} (or similar) which could be used for code execution.
 */
public class PluginConfigurationClassLoader extends ClassLoader {

    private static final Set<String> allowedClasses = Set.of(
            Collections.class,
            Configuration.class,
            LdapServer.class,
            String.class,
            WebHook.class,
            WebHooks.class,
            XMLDecoder.class
    ).stream().map(Class::getName).collect(Collectors.toSet());

    @Override
    public Class<?> loadClass(final String name) throws ClassNotFoundException {
        if (!allowedClasses.contains(name)) {
            throw new IllegalAccessError(name + " is not allowed to be used in configuration");
        }

        return getClass().getClassLoader().loadClass(name);
    }
}
