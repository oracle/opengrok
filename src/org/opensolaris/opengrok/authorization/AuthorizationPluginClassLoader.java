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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.authorization;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Class loader for authorization plugins.
 *
 * @author Krystof Tulinger
 */
public class AuthorizationPluginClassLoader extends ClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationPluginClassLoader.class);
    private final static String[] classWhitelist = new String[]{
        "org.opensolaris.opengrok.configuration.Group",
        "org.opensolaris.opengrok.configuration.Project",
        "org.opensolaris.opengrok.authorization.IAuthorizationPlugin"
    };

    private final static String[] packageBlacklist = new String[]{
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
        try {
            File[] jars = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar");
                }
            });

            if (jars == null) {
                throw new IOException("Directory " + directory + " is not accessible");
            }

            for (File f : jars) {
                try (JarFile jar = new JarFile(f)) {
                    String filename = classname.replace('.', File.separatorChar) + ".class";
                    JarEntry entry = (JarEntry) jar.getEntry(filename);
                    if (entry != null && entry.getName().endsWith(".class")) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            byte[] bytes = loadBytes(is);
                            Class c = defineClass(classname, bytes, 0, bytes.length);
                            LOGGER.log(Level.INFO, "Class \"{0}\" found in file \"{1}\"",
                                    new Object[]{
                                        classname,
                                        f.getAbsolutePath()
                                    });
                            return c;

                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(e.toString(), e);
        } catch (Throwable e) {
            throw new ClassNotFoundException(e.toString(), e);
        }
        return null;
    }

    private Class loadClassFromFile(String classname) throws ClassNotFoundException {
        try {
            String filename = classname.replace('.', File.separatorChar) + ".class";
            File f = new File(directory, filename);
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] bytes = loadBytes(in);

                Class c = defineClass(classname, bytes, 0, bytes.length);
                LOGGER.log(Level.INFO, "Class \"{0}\" found in file \"{1}\"",
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

    private void checkClassname(String name) throws SecurityException {
        if (name.startsWith("org.opensolaris.opengrok.")
                && !Arrays.asList(classWhitelist).contains(name)) {
            throw new SecurityException("Tried to load a blacklisted class \"" + name + "\"\n"
                    + "Allowed classes from opengrok package are only: "
                    + Arrays.toString(classWhitelist));
        }
    }

    private void checkPackage(String name) throws SecurityException {
        for (int i = 0; i < packageBlacklist.length; i++) {
            if (name.startsWith(packageBlacklist[i] + ".")) {
                throw new SecurityException("Tried to load a class \"" + name
                        + "\" to a blacklisted package "
                        + "\"" + packageBlacklist[i] + "\"\n"
                        + "Disabled packages are: "
                        + Arrays.toString(packageBlacklist));
            }
        }
    }

    /**
     * Loads the class with given name.
     *
     * Package blacklist:
     *
     * @see #packageBlacklist Classes whitelist:
     * @see #classWhitelist
     *
     * Order of lookup: 1) already loaded classes 2) parent class loader 3)
     * loading from .class files 4) loading from .jar files
     *
     * @param name
     * @return loaded class or null
     * @throws ClassNotFoundException
     * @throws SecurityException
     */
    @Override
    public Class loadClass(String name) throws ClassNotFoundException, SecurityException {
        Class c = null;

        checkClassname(name);

        // find already loaded class
        if ((c = findLoadedClass(name)) != null) {
            return c;
        }

        // try if parent classloader can load this class
        if (this.getParent() != null) {
            try {
                if ((c = this.getParent().loadClass(name)) != null) {
                    return c;
                }
            } catch (ClassNotFoundException ex) {
            }
        }

        try {
            checkPackage(name);
            // load it from file
            if ((c = loadClassFromFile(name)) != null) {
                return c;
            }
        } catch (ClassNotFoundException ex) {
        }

        try {
            checkPackage(name);
            // load it from jar
            if ((c = loadClassFromJar(name)) != null) {
                return c;
            }
        } catch (ClassNotFoundException ex) {
        }

        throw new ClassNotFoundException("Class \"" + name + "\" was not found");
    }

}
