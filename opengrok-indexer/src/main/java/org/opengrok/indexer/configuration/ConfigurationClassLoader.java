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
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.configuration;

import io.micrometer.statsd.StatsdFlavor;
import org.opengrok.indexer.authorization.AuthControlFlag;
import org.opengrok.indexer.authorization.AuthorizationEntity;
import org.opengrok.indexer.authorization.AuthorizationPlugin;
import org.opengrok.indexer.authorization.AuthorizationStack;
import org.opengrok.indexer.authorization.IAuthorizationPlugin;
import org.opengrok.indexer.configuration.Configuration.RemoteSCM;
import org.opengrok.indexer.history.RepositoryInfo;

import java.beans.XMLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Temporary hack to prevent {@link XMLDecoder} to deserialize other than allowed classes. This tries to prevent
 * calling of methods on {@link ProcessBuilder} or {@link Runtime} (or similar) which could be used for code execution.
 */
public class ConfigurationClassLoader extends ClassLoader {

    private static final Set<String> allowedClasses = Set.of(
            ArrayList.class,
            AuthControlFlag.class,
            AuthorizationEntity.class,
            AuthorizationPlugin.class,
            AuthorizationStack.class,
            Collections.class,
            Configuration.class,
            Enum.class,
            Filter.class,
            Group.class,
            HashMap.class,
            HashSet.class,
            IAuthorizationPlugin.class,
            IgnoredNames.class,
            LuceneLockName.class,
            Project.class,
            RemoteSCM.class,
            RepositoryInfo.class,
            Set.class,
            StatsdConfig.class,
            StatsdFlavor.class,
            String.class,
            SuggesterConfig.class,
            TreeMap.class,
            TreeSet.class,
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
