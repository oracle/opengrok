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
 * Portions Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.framework;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Class loader for plugins from .class and .jar files.
 *
 * @author Krystof Tulinger
 */
public class PluginClassLoader extends ClassLoader {

    private final Map<String, Class<?>> cache = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginClassLoader.class);
    private static final String[] CLASS_WHITELIST = new String[]{
            "org.opengrok.indexer.configuration.Group",
            "org.opengrok.indexer.configuration.Project",
            "org.opengrok.indexer.configuration.RuntimeEnvironment",
            "org.opengrok.indexer.authorization.IAuthorizationPlugin",
            "org.opengrok.indexer.authorization.plugins.*",
            "org.opengrok.indexer.authorization.AuthorizationException",
            "org.opengrok.indexer.util.*",
            "org.opengrok.indexer.logger.*",
            "org.opengrok.indexer.Metrics"
    };

    private static final String[] PACKAGE_BLACKLIST = new String[]{
            "java",
            "javax",
            "org.w3c",
            "org.xml",
            "org.omg",
            "sun"
    };

    private static final String CLASS_SUFFIX = ".class";

    private final File directory;

    public PluginClassLoader(File directory) {
        super(PluginClassLoader.class.getClassLoader());
        this.directory = directory;
    }

    private Class<?> loadClassFromJar(String classname) throws ClassNotFoundException {
        File[] jars = directory.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jars == null) {
            throw new ClassNotFoundException(
                    "Cannot load class " + classname,
                    new IOException("Directory " + directory + " is not accessible"));
        }

        for (File f : jars) {
            try (JarFile jar = new JarFile(f)) {
                // jar files always use / separator
                String filename = classname.replace('.', '/') + CLASS_SUFFIX;
                JarEntry entry = (JarEntry) jar.getEntry(filename);
                if (entry != null && entry.getName().endsWith(CLASS_SUFFIX)) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        byte[] bytes = loadBytes(is);
                        Class<?> c = defineClass(classname, bytes, 0, bytes.length);
                        LOGGER.log(Level.FINE, "Class \"{0}\" found in file \"{1}\"",
                                new Object[]{
                                        classname,
                                        f.getAbsolutePath()
                                });
                        return c;
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Loading class threw an exception:", ex);
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Loading class threw an unknown exception", ex);
            }
        }
        throw new ClassNotFoundException("Class \"" + classname + "\" could not be found");
    }

    private Class<?> loadClassFromFile(String classname) throws ClassNotFoundException {
        try {
            String filename = classname.replace('.', File.separatorChar) + CLASS_SUFFIX;
            File f = new File(directory, filename);
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] bytes = loadBytes(in);

                Class<?> c = defineClass(classname, bytes, 0, bytes.length);
                LOGGER.log(Level.FINEST, "Class \"{0}\" found in file \"{1}\"",
                        new Object[]{
                                classname,
                                f.getAbsolutePath()
                        });
                return c;
            }
        } catch (Throwable e) {
            throw new ClassNotFoundException(e.toString(), e);
        }
    }

    private byte[] loadBytes(InputStream in) throws IOException {
        byte[] bytes = new byte[in.available()];
        if (in.read(bytes) != bytes.length) {
            throw new IOException("unexpected truncated read");
        }
        return bytes;
    }

    private boolean checkWhiteList(String name) {
        for (String pattern : CLASS_WHITELIST) {
            pattern = pattern.replaceAll("\\.", "\\\\.");
            pattern = pattern.replaceAll("\\*", ".*");
            if (name.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void checkClassname(String name) throws SecurityException {
        if (name.startsWith("org.opengrok.")
                && !checkWhiteList(name)) {
            throw new SecurityException("Tried to load a blacklisted class \"" + name + "\"\n"
                    + "Allowed classes from opengrok package are only: "
                    + Arrays.toString(CLASS_WHITELIST));
        }
    }

    private void checkPackage(String name) throws SecurityException {
        for (String s : PACKAGE_BLACKLIST) {
            if (name.startsWith(s + ".")) {
                throw new SecurityException("Tried to load a class \"" + name
                        + "\" to a blacklisted package "
                        + "\"" + s + "\"\n"
                        + "Disabled packages are: "
                        + Arrays.toString(PACKAGE_BLACKLIST));
            }
        }
    }

    /**
     * Loads the class with given name.
     * <p>
     * Order of lookup:
     * <ol>
     * <li>already loaded classes </li>
     * <li>parent class loader</li>
     * <li>loading from .class files</li>
     * <li>loading from .jar files</li>
     * </ol>
     * <p>
     * Package blacklist: {@link #PACKAGE_BLACKLIST}.<br>
     * Classes whitelist: {@link #CLASS_WHITELIST}.
     *
     * @param name class name
     * @return loaded class or null
     * @throws ClassNotFoundException if class is not found
     * @throws SecurityException      if the loader cannot access the class
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException, SecurityException {
        return loadClass(name, true);
    }

    /**
     * Loads the class with given name.
     * <p>
     * Order of lookup:
     * <ol>
     * <li>already loaded classes </li>
     * <li>parent class loader</li>
     * <li>loading from .class files</li>
     * <li>loading from .jar files</li>
     * </ol>
     * <p>
     * Package blacklist: {@link #PACKAGE_BLACKLIST}.<br>
     * Classes whitelist: {@link #CLASS_WHITELIST}.
     *
     * @param name      class name
     * @param resolveIt if the class should be resolved
     * @return loaded class or null
     * @throws ClassNotFoundException if class is not found
     * @throws SecurityException      if the loader cannot access the class
     */
    @Override
    public Class<?> loadClass(String name, boolean resolveIt) throws ClassNotFoundException, SecurityException {
        Class<?> c = cache.get(name);

        if (c != null) {
            if (resolveIt) {
                resolveClass(c);
            }
            return c;
        }

        checkClassname(name);

        // find already loaded class
        if ((c = findLoadedClass(name)) != null) {
            cache.put(name, c);
            if (resolveIt) {
                resolveClass(c);
            }
            return c;
        }

        // try if parent classloader can load this class
        if (this.getParent() != null) {
            try {
                if ((c = this.getParent().loadClass(name)) != null) {
                    cache.put(name, c);
                    if (resolveIt) {
                        resolveClass(c);
                    }
                    return c;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        try {
            checkPackage(name);
            // load it from file
            if ((c = loadClassFromFile(name)) != null) {
                cache.put(name, c);
                if (resolveIt) {
                    resolveClass(c);
                }
                return c;
            }
        } catch (ClassNotFoundException ignored) {
        }

        try {
            checkPackage(name);
            // load it from jar
            if ((c = loadClassFromJar(name)) != null) {
                cache.put(name, c);
                if (resolveIt) {
                    resolveClass(c);
                }
                return c;
            }
        } catch (ClassNotFoundException ignored) {
        }

        throw new ClassNotFoundException("Class \"" + name + "\" was not found");
    }
}
