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
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.authorization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
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
 * Class loader for authorization plugins.
 *
 * @author Krystof Tulinger
 */
public class AuthorizationPluginClassLoader extends ClassLoader {

    private final Map<String, Class> cache = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationPluginClassLoader.class);
    private final static String[] CLASS_WHITELIST = new String[]{
        "org.opengrok.indexer.configuration.Group",
        "org.opengrok.indexer.configuration.Project",
        "org.opengrok.indexer.configuration.RuntimeEnvironment",
        "org.opengrok.indexer.authorization.IAuthorizationPlugin",
        "org.opengrok.indexer.authorization.plugins.*",
        "org.opengrok.indexer.util.*",
        "org.opengrok.indexer.logger.*"
    };

    private final static String[] PACKAGE_BLACKLIST = new String[]{
        "java",
        "javax",
        "org.w3c",
        "org.xml",
        "org.omg",
        "sun"
    };

    private final File directory;

    public AuthorizationPluginClassLoader(File directory) {
        super(AuthorizationPluginClassLoader.class.getClassLoader());
        this.directory = directory;
    }

    private Class loadClassFromJar(String classname) throws ClassNotFoundException {
        File[] jars = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });

        if (jars == null) {
            throw new ClassNotFoundException(
                    "Cannot load class " + classname,
                    new IOException("Directory " + directory + " is not accessible"));
        }

        for (File f : jars) {
            try (JarFile jar = new JarFile(f)) {
                String filename = classname.replace('.', File.separatorChar) + ".class";
                JarEntry entry = (JarEntry) jar.getEntry(filename);
                if (entry != null && entry.getName().endsWith(".class")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        byte[] bytes = loadBytes(is);
                        Class c = defineClass(classname, bytes, 0, bytes.length);
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
                LOGGER.log(Level.SEVERE, "Loading class threw an unknown exception:", ex);
            }
        }
        throw new ClassNotFoundException("Class \"" + classname + "\" could not be found");
    }

    private Class loadClassFromFile(String classname) throws ClassNotFoundException {
        try {
            String filename = classname.replace('.', File.separatorChar) + ".class";
            File f = new File(directory, filename);
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] bytes = loadBytes(in);

                Class c = defineClass(classname, bytes, 0, bytes.length);
                LOGGER.log(Level.FINEST, "Class \"{0}\" found in file \"{1}\"",
                        new Object[]{
                            classname,
                            f.getAbsolutePath()
                        });
                return c;
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(e.toString(), e);
        } catch (Throwable e) {
            throw new ClassNotFoundException(e.toString(), e);
        }
    }

    private byte[] loadBytes(InputStream in) throws IOException {
        byte[] bytes = new byte[in.available()];
        in.read(bytes);
        return bytes;
    }

    private boolean checkWhiteList(String name) {
        for (int i = 0; i < CLASS_WHITELIST.length; i++) {
            String pattern = CLASS_WHITELIST[i];
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
        for (int i = 0; i < PACKAGE_BLACKLIST.length; i++) {
            if (name.startsWith(PACKAGE_BLACKLIST[i] + ".")) {
                throw new SecurityException("Tried to load a class \"" + name
                        + "\" to a blacklisted package "
                        + "\"" + PACKAGE_BLACKLIST[i] + "\"\n"
                        + "Disabled packages are: "
                        + Arrays.toString(PACKAGE_BLACKLIST));
            }
        }
    }

    /**
     * Loads the class with given name.
     *
     * Order of lookup:
     * <ol>
     * <li>already loaded classes </li>
     * <li>parent class loader</li>
     * <li>loading from .class files</li>
     * <li>loading from .jar files</li>
     * </ol>
     *
     * Package blacklist: {@link #PACKAGE_BLACKLIST}.<br>
     * Classes whitelist: {@link #CLASS_WHITELIST}.
     *
     * @param name class name
     * @return loaded class or null
     * @throws ClassNotFoundException if class is not found
     * @throws SecurityException if the loader cannot access the class
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException, SecurityException {
        return loadClass(name, true);
    }

    /**
     * Loads the class with given name.
     *
     * Order of lookup:
     * <ol>
     * <li>already loaded classes </li>
     * <li>parent class loader</li>
     * <li>loading from .class files</li>
     * <li>loading from .jar files</li>
     * </ol>
     *
     * Package blacklist: {@link #PACKAGE_BLACKLIST}.<br>
     * Classes whitelist: {@link #CLASS_WHITELIST}.
     *
     * @param name class name
     * @param resolveIt if the class should be resolved
     * @return loaded class or null
     * @throws ClassNotFoundException if class is not found
     * @throws SecurityException if the loader cannot access the class
     */
    @Override
    public Class loadClass(String name, boolean resolveIt) throws ClassNotFoundException, SecurityException {
        Class c;

        if ((c = cache.get(name)) != null) {
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
            } catch (ClassNotFoundException ex) {
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
        } catch (ClassNotFoundException ex) {
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
        } catch (ClassNotFoundException ex) {
        }

        throw new ClassNotFoundException("Class \"" + name + "\" was not found");
    }
}
