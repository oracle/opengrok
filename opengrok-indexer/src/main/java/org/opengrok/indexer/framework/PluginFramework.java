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
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.framework;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opengrok.indexer.authorization.IAuthorizationPlugin;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.IOUtils;

/**
 * Plugin framework for plugins of type {@code PluginType}.
 *
 * @author Krystof Tulinger
 */
public abstract class PluginFramework<PluginType> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginFramework.class);

    /**
     * Class of the plugin type, necessary for instantiating and searching.
     */
    private final Class<PluginType> classType;

    /**
     * Plugin directory.
     */
    private File pluginDirectory;

    /**
     * Customized class loader for plugin classes.
     */
    private PluginClassLoader loader;

    /**
     * Whether to load plugins from class files and jar files.
     */
    private boolean loadClasses = true;
    private boolean loadJars = true;

    /**
     * Create a new instance of plugin framework for a plugin directory.
     *
     * @param classType the class of the plugin type
     * @param path      the plugin directory path
     */
    public PluginFramework(Class<PluginType> classType, String path) {
        this.classType = classType;
        setPluginDirectory(path);
    }

    /**
     * Get the plugin directory.
     *
     * @return plugin directory file
     */
    public synchronized File getPluginDirectory() {
        return pluginDirectory;
    }

    /**
     * Set the plugin directory.
     *
     * @param pluginDirectory the directory
     */
    public synchronized void setPluginDirectory(File pluginDirectory) {
        this.pluginDirectory = pluginDirectory;
    }

    /**
     * Set the plugin directory.
     *
     * @param directory the directory path
     */
    public void setPluginDirectory(String directory) {
        setPluginDirectory(directory != null ? new File(directory) : null);
    }

    /**
     * Make {@code reload()} search for plugins in class files.
     *
     * @param flag true or false
     */
    public void setLoadClasses(boolean flag) {
        loadClasses = flag;
    }

    /**
     * Whether to search for plugins in class files.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isLoadClassesEnabled() {
        return loadClasses;
    }

    /**
     * Make {@code reload()} search for plugins in jar files.
     *
     * @param flag true or false
     */
    public void setLoadJars(boolean flag) {
        loadJars = flag;
    }

    /**
     * Whether to search for plugins in class files.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isLoadJarsEnabled() {
        return loadJars;
    }

    /**
     * Return the java canonical name for the plugin class. If the canonical
     * name does not exist it returns the usual java name.
     *
     * @param plugin the plugin
     * @return the class name
     */
    protected String getClassName(IAuthorizationPlugin plugin) {
        if (plugin.getClass().getCanonicalName() != null) {
            return plugin.getClass().getCanonicalName();
        }
        return plugin.getClass().getName();
    }

    /**
     * Wrapper around the class loading. Report all exceptions into the log.
     *
     * @param classname full name of the class
     * @return the class implementing the {@link IAuthorizationPlugin} interface
     * or null if there is no such class
     * @see #loadClass(String)
     */
    public PluginType handleLoadClass(String classname) {
        try {
            return loadClass(classname);
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.WARNING, String.format("Class \"%s\" was not found", classname), ex);
        } catch (SecurityException ex) {
            LOGGER.log(Level.WARNING, String.format("Class \"%s\" was found but it is placed in prohibited package: ", classname), ex);
        } catch (InstantiationException ex) {
            LOGGER.log(Level.WARNING, String.format("Class \"%s\" could not be instantiated: ", classname), ex);
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.WARNING, String.format("Class \"%s\" loader threw an exception: ", classname), ex);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, String.format("Class \"%s\" loader threw an unknown error: ", classname), ex);
        }
        return null;
    }

    /**
     * Load a class into JVM with custom class loader. Call a non-parametric
     * constructor to create a new instance of that class.
     * <p>
     * <p>The classes implementing/extending the {@code PluginType} type are
     * returned and initialized with a call to a non-parametric constructor.
     *
     * @param classname the full name of the class to load
     * @return the class implementing/extending the {@code PluginType} class
     * or null if there is no such class
     * @throws ClassNotFoundException    when the class can not be found
     * @throws SecurityException         when it is prohibited to load such class
     * @throws InstantiationException    when it is impossible to create a new
     *                                   instance of that class
     * @throws IllegalAccessException    when the constructor of the class is not
     *                                   accessible
     * @throws NoSuchMethodException     when the class does not have no-argument constructor
     * @throws InvocationTargetException if the underlying constructor of the class throws an exception
     */
    @SuppressWarnings({"unchecked"})
    private PluginType loadClass(String classname) throws ClassNotFoundException,
            SecurityException,
            InstantiationException,
            IllegalAccessException,
            NoSuchMethodException,
            InvocationTargetException {

        Class<?> c = loader.loadClass(classname);

        // check for implemented interfaces or extended superclasses
        for (Class<?> intf1 : getSuperclassesAndInterfaces(c)) {
            if (intf1.getCanonicalName().equals(classType.getCanonicalName())
                    && !Modifier.isAbstract(c.getModifiers())) {
                // call to non-parametric constructor
                return (PluginType) c.getDeclaredConstructor().newInstance();
            }
        }
        LOGGER.log(Level.FINEST, "Plugin class \"{0}\" does not implement IAuthorizationPlugin interface.", classname);
        return null;
    }

    /**
     * Get all available interfaces or superclasses of a class clazz.
     *
     * @param clazz class
     * @return list of interfaces or superclasses of the class clazz
     */
    protected List<Class<?>> getSuperclassesAndInterfaces(Class<?> clazz) {
        List<Class<?>> types = new LinkedList<>();
        Class<?> self = clazz;
        while (self != null && self != classType && !types.contains(classType)) {
            types.add(self);
            types.addAll(Arrays.asList(self.getInterfaces()));
            self = self.getSuperclass();
        }
        return types;
    }

    /**
     * Traverse list of files which possibly contain a java class
     * to load all classes.
     * Each class is loaded with {@link #handleLoadClass(String)} which
     * delegates the loading to the custom class loader
     * {@link #loadClass(String)}.
     *
     * @param classfiles list of files which possibly contain a java class
     * @see #handleLoadClass(String)
     * @see #loadClass(String)
     */
    private void loadClassFiles(List<File> classfiles) {
        PluginType plugin;

        for (File file : classfiles) {
            String classname = getClassName(file);
            if (classname.isEmpty()) {
                continue;
            }
            // Load the class in memory and try to find a configured space for this class.
            if ((plugin = handleLoadClass(classname)) != null) {
                classLoaded(plugin);
            }
        }
    }

    /**
     * Traverse list of jar files to load all classes.
     * <p>
     * Each class is loaded with {@link #handleLoadClass(String)} which
     * delegates the loading to the custom class loader
     * {@link #loadClass(String)}.
     *
     * @param jarfiles list of jar files containing java classes
     * @see #handleLoadClass(String)
     * @see #loadClass(String)
     */
    private void loadJarFiles(List<File> jarfiles) {
        PluginType pf;

        for (File file : jarfiles) {
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }
                    String classname = getClassName(entry);
                    if (!entry.getName().endsWith(".class") || classname.isEmpty()) {
                        continue;
                    }
                    // Load the class in memory and try to find a configured space for this class.
                    if ((pf = handleLoadClass(classname)) != null) {
                        classLoaded(pf);
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Could not manipulate with file because of: ", ex);
            }
        }
    }

    private String getClassName(File f) {
        String classname = f.getAbsolutePath().substring(pluginDirectory.getAbsolutePath().length() + 1, f.getAbsolutePath().length());
        classname = classname.replace(File.separatorChar, '.'); // convert to package name
        // no need to check for the index from lastIndexOf because we're in a branch
        // where we expect the .class suffix
        classname = classname.substring(0, classname.lastIndexOf('.')); // strip .class
        return classname;
    }

    private String getClassName(JarEntry f) {
        // java jar always uses / as separator
        String classname = f.getName().replace('/', '.'); // convert to package name
        return classname.substring(0, classname.lastIndexOf('.'));  // strip .class
    }

    /**
     * Allow the implementing class to interact with the loaded class when the class
     * was loaded with the custom class loader.
     *
     * @param plugin the loaded plugin
     */
    protected abstract void classLoaded(PluginType plugin);


    /**
     * Perform custom operations before the plugins are loaded.
     */
    protected abstract void beforeReload();

    /**
     * Perform custom operations when the framework has reloaded all available plugins.
     * <p>
     * When this is invoked, all plugins has been loaded into the memory and for each available plugin
     * the {@link #classLoaded(Object)} was invoked.
     */
    protected abstract void afterReload();

    /**
     * Calling this function forces the framework to reload the plugins.
     * <p>
     * <p>Plugins are taken from the pluginDirectory.
     */
    public final void reload() {
        if (pluginDirectory == null || !pluginDirectory.isDirectory() || !pluginDirectory.canRead()) {
            LOGGER.log(Level.WARNING, "Plugin directory not found or not readable: {0}. "
                    + "All requests allowed.", pluginDirectory);
            return;
        }

        LOGGER.log(Level.INFO, "Plugins are being reloaded from {0}", pluginDirectory.getAbsolutePath());

        // trashing out the old instance of the loaded enables us
        // to reload the stack at runtime
        loader = AccessController.doPrivileged((PrivilegedAction<PluginClassLoader>) () -> new PluginClassLoader(pluginDirectory));

        // notify the implementing class that the reload is about to begin
        beforeReload();

        // load all other possible plugin classes.
        if (isLoadClassesEnabled()) {
            loadClassFiles(IOUtils.listFilesRec(pluginDirectory, ".class"));
        }
        if (isLoadJarsEnabled()) {
            loadJarFiles(IOUtils.listFiles(pluginDirectory, ".jar"));
        }

        // notify the implementing class that the reload has ended
        afterReload();
    }
}
