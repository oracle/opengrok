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
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;

/**
 * Placeholder for performing authorization checks.
 *
 * @author Krystof Tulinger
 */
public final class AuthorizationFramework {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFramework.class);
    private final File directory;
    private AuthorizationPluginClassLoader loader;

    private volatile static AuthorizationFramework instance = new AuthorizationFramework();
    List<IAuthorizationPlugin> plugins;

    /**
     * Plugin directory is set through RuntimeEnvironment.
     *
     * @return an instance of AuthorizationFramework
     * @see RuntimeEnvironment#getConfiguration
     * @see Configuration#setPluginDirectory
     */
    public static AuthorizationFramework getInstance() {
        return instance;
    }

    /**
     * Checks if the request should have an access to project.
     *
     * @param request request object
     * @param project project object
     * @return true if yes
     */
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return checkAll(
                request,
                "plugin_framework_project_cache",
                project.getDescription(),
                new Predicate<IAuthorizationPlugin>() {
            @Override
            public boolean test(IAuthorizationPlugin plugin) {
                return plugin.isAllowed(request, project);
            }
        });
    }

    /**
     * Checks if the request should have an access to group.
     *
     * @param request request object
     * @param group group object
     * @return true if yes
     */
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return checkAll(
                request,
                "plugin_framework_group_cache",
                group.getName(),
                new Predicate<IAuthorizationPlugin>() {
            @Override
            public boolean test(IAuthorizationPlugin plugin) {
                return plugin.isAllowed(request, group);
            }
        });
    }

    private AuthorizationFramework() {
        String path = RuntimeEnvironment.getInstance()
                .getPluginDirectory();
        File directory = path == null ? null : new File(path);
        if (path == null || directory == null || !directory.isDirectory() || !directory.canRead()) {
            LOGGER.log(Level.INFO, "plugin directory not found or not readable: {0}. "
                    + "The AuthorizationFramework will just pass requests through.", path);
        }
        plugins = new ArrayList<>();
        this.directory = directory;
        reload();
    }

    /**
     * Get available plugins.
     *
     * This and couple of following methods are declared as synchronized because
     * 1) plugins can be reloaded at anytime 
     * 2) requests are pretty asynchronous
     *
     * So this tries to ensure that there will be no
     * ConcurrentModificationException or other similar exceptions.
     *
     * @return list of available plugins
     */
    private List<IAuthorizationPlugin> getPlugins() {
        List<IAuthorizationPlugin> p;
        synchronized (this) {
            p = new ArrayList<>(plugins);
        }
        return p;
    }

    private void removePlugin(IAuthorizationPlugin plugin) {
        synchronized (this) {
            try {
                plugin.unload();
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Plugin \"" + plugin.getClass().getName() + "\" has failed while unloading with exception:", ex);
            }
            plugins.remove(plugin);
        }

    }

    private void addPlugin(IAuthorizationPlugin plugin) {
        synchronized (this) {
            plugins.add(plugin);
        }
    }

    private void removeAll() {
        synchronized (this) {
            for (IAuthorizationPlugin plugin : getPlugins()) {
                try {
                    plugin.unload();
                } catch (Throwable ex) {
                    LOGGER.log(Level.SEVERE, "Plugin \"" + plugin.getClass().getName() + "\" has failed while unloading with exception:", ex);
                }
            }
            plugins.clear();
        }
    }

    /**
     * @param suffix suffix for the files
     * @return list of file with suffix
     */
    private List<File> listFiles(String suffix) {
        File[] files = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(suffix);
            }
        });
        if (files == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(files);
    }

    /**
     * @param suffix suffix for the files
     * @return recursively traversed list of files with given suffix
     */
    private List<File> listFilesRec(String suffix) {
        return listFilesClassesRec(directory, suffix);
    }

    private List<File> listFilesClassesRec(File start, String suffix) {
        List<File> results = new ArrayList<>();
        File[] fs = start.listFiles();
        if (fs == null) {
            return results;
        }
        List<File> files = Arrays.asList(fs);
        for (File f : files) {
            if (f.isDirectory() && f.canRead() && !f.getName().equals(".") && !f.getName().equals("..")) {
                results.addAll(listFilesClassesRec(f, suffix));
            } else if (f.getName().endsWith(suffix)) {
                results.add(f);
            }
        }
        return results;
    }

    private void handleLoadClass(String classname) {
        try {
            loadClass(classname);
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.INFO, "Class was not found: ", ex);
        } catch (SecurityException ex) {
            LOGGER.log(Level.INFO, "Class was found but it is placed in prohibited package: ", ex);
        } catch (InstantiationException ex) {
            LOGGER.log(Level.INFO, "Class couldn not be instantiated: ", ex);
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.INFO, "Class loader threw an exception: ", ex);
        } catch (Throwable ex) {
            LOGGER.log(Level.INFO, "Class loader threw an uknown error: ", ex);
        }
    }

    private void loadClass(String classname) throws ClassNotFoundException,
            SecurityException,
            InstantiationException,
            IllegalAccessException {
        
        Class c = loader.loadClass(classname);
        // check for implemented interfaces
        Class[] intf = c.getInterfaces();
        boolean loaded = false;
        for (Class intf1 : intf) {
            if (intf1.getName().equals(IAuthorizationPlugin.class.getName())) {
                // call to non-parametric constructor
                IAuthorizationPlugin pf = (IAuthorizationPlugin) c.newInstance();
                addPlugin(pf);
                LOGGER.log(Level.INFO, "Plugin \"{0}\" loaded.", pf.getClass().getName());
                loaded = true;
            }
        }
        if (!loaded) {
            LOGGER.log(Level.INFO, "Plugin class \"{0}\" does not implement IAuthorizationPlugin interface.", classname);
        }
    }

    private String getClassName(File f) {
        String classname = f.getAbsolutePath().substring(directory.getAbsolutePath().length() + 1, f.getAbsolutePath().length());
        classname = classname.replace(File.separatorChar, '.'); // convert to package name
        classname = classname.substring(0, classname.lastIndexOf('.')); // strip .class
        return classname;
    }

    private String getClassName(JarEntry f) {
        String classname = f.getName().replace(File.separatorChar, '.'); // convert to package name
        return classname.substring(0, classname.lastIndexOf('.'));  // strip .class
    }

    /**
     * Calling this function forces the framework to reload its plugins.
     *
     * Plugins are taken from the pluginDirectory (set in web.xml).
     *
     * Old instances of plugins are removed and new list of plugins is
     * constructed. Unload and load event is fired on each plugin.
     *
     * @see IAuthorizationPlugin#load() 
     * @see IAuthorizationPlugin#unload() 
     */
    @SuppressWarnings("unchecked")
    public synchronized void reload() {
        if (directory == null || !directory.isDirectory() || !directory.canRead()) {
            return;
        }
        LOGGER.log(Level.INFO, "Plugins are being reloaded from " + directory.getAbsolutePath());
        removeAll();
        // trashing out the old instance of the loaded enables us
        // to reaload the plugins at runtime
        loader = (AuthorizationPluginClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return new AuthorizationPluginClassLoader(directory);
            }
        });
        
        removeAll();
        plugins = new ArrayList<>();

        List<File> classfiles = listFilesRec(".class");
        List<File> jarfiles = listFiles(".jar");

        for (File file : classfiles) {
            String classname = getClassName(file);
            if (classname.isEmpty()) {
                continue;
            }
            handleLoadClass(classname);
        }

        for (File file : jarfiles) {
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String classname = getClassName(entry);
                    if (!entry.getName().endsWith(".class") || classname.isEmpty()) {
                        continue;
                    }
                    handleLoadClass(classname);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.INFO, "Could not manipulate with file because of: ", ex);
            }
        }
        
        for (IAuthorizationPlugin plugin : getPlugins()) {
            try {
                plugin.load();
            } catch (Throwable ex) {
                // remove faulty plugin
                LOGGER.log(Level.SEVERE, "Plugin \"" + plugin.getClass().getName() + "\" has failed while loading with exception:", ex);
                removePlugin(plugin);
            }
        }
    }

    /**
     * Checks if the request should have an access to a resource.
     *
     * Internally performed with a predicate. Using cache in request attributes.
     *
     * @param request request object
     * @param cache cache
     * @param name name
     * @param predicate predicate
     * @return true if yes
     */
    @SuppressWarnings("unchecked")
    private boolean checkAll(HttpServletRequest request, String cache, String name,
            Predicate<IAuthorizationPlugin> predicate) {
        Map<String, Boolean> m = (Map<String, Boolean>) request.getAttribute(cache);

        if (m == null) {
            m = new TreeMap<>();
        }

        Boolean val = m.get(name);

        if (val != null) {
            return val;
        }

        for (IAuthorizationPlugin plugin : getPlugins()) {
            try {
                if (!predicate.test(plugin)) {
                    m.put(name, false);
                    request.setAttribute(cache, m);
                    return false;
                }
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Plugin \"" + plugin.getClass().getName() + "\" has failed with exception:", ex);
                removePlugin(plugin);
            }
        }

        m.put(name, true);
        request.setAttribute(cache, m);
        return true;
    }

}
