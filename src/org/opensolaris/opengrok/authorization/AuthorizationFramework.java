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
package org.opensolaris.opengrok.authorization;

import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
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
import org.opensolaris.opengrok.authorization.AuthorizationCheck.AuthControlFlag;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Group;
import org.opensolaris.opengrok.configuration.Nameable;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.util.IOUtils;

/**
 * Placeholder for performing authorization checks.
 *
 * @author Krystof Tulinger
 */
public final class AuthorizationFramework {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFramework.class);
    private volatile static AuthorizationFramework instance = new AuthorizationFramework();

    /**
     * Plugin directory.
     */
    private File pluginDirectory;

    /**
     * Customized class loader for plugin classes.
     */
    private AuthorizationPluginClassLoader loader;

    /**
     * List of available plugins in the order of the execution.
     */
    List<AuthorizationPluginWrapper> plugins;

    /**
     * Keeping track of the number of reloads in this framework. This can be
     * used by the plugins to invalidate the session and force reload the
     * authorization values.
     *
     * Starting at 0 and increases with every reload.
     *
     * The plugin should call RuntimeEnvironment.getPluginVersion() to get this
     * number.
     *
     * @see RuntimeEnvironment#getPluginVersion()
     */
    private int pluginVersion = 0;

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
     * Get the plugin directory.
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
        AuthorizationFramework.this.setPluginDirectory(directory != null ? new File(directory) : null);
    }

    /**
     * Checks if the request should have an access to project. See
     * {@link #checkAll(HttpServletRequest, String, Nameable, Predicate)} for more
     * information about invocation order.
     *
     * @param request request object
     * @param project project object
     * @return true if yes
     *
     * @see #checkAll(HttpServletRequest, String, Nameable, Predicate)
     */
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return checkAll(
                request,
                "plugin_framework_project_cache",
                project,
                new Predicate<AuthorizationPluginWrapper>() {
            @Override
            public boolean test(AuthorizationPluginWrapper plugin) {
                return plugin.isAllowed(request, project);
            }
        });
    }

    /**
     * Checks if the request should have an access to group. See
     * {@link #checkAll(HttpServletRequest, String, Nameable, Predicate)} for more
     * information about invocation order.
     *
     * @param request request object
     * @param group group object
     * @return true if yes
     *
     * @see #checkAll(HttpServletRequest, String, Nameable, Predicate)
     */
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return checkAll(
                request,
                "plugin_framework_group_cache",
                group,
                new Predicate<AuthorizationPluginWrapper>() {
            @Override
            public boolean test(AuthorizationPluginWrapper plugin) {
                return plugin.isAllowed(request, group);
            }
        });
    }

    private AuthorizationFramework() {
        String path = RuntimeEnvironment.getInstance()
                .getPluginDirectory();
        plugins = new ArrayList<>();
        setPluginDirectory(path);
        reload();
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
     * Get available plugins.
     *
     * This and couple of following methods are declared as synchronized because
     * <ol>
     * <li>plugins can be reloaded at anytime</li>
     * <li>requests are pretty asynchronous</li>
     * </ol>
     *
     * So this tries to ensure that there will be no
     * ConcurrentModificationException or other similar exceptions.
     *
     * @return list of available plugins
     */
    protected List<AuthorizationPluginWrapper> getPlugins() {
        List<AuthorizationPluginWrapper> p;
        synchronized (this) {
            p = new ArrayList<>(plugins);
        }
        return p;
    }

    /**
     * Find the plugin wrapper for the class name.
     *
     * @param classname class name
     * @return the plugin wrapper or null if there is none
     */
    public AuthorizationPluginWrapper findPlugin(String classname) {
        if (classname == null) {
            return null;
        }
        for (AuthorizationPluginWrapper p : getPlugins()) {
            if (p.getClassname().equals(classname)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Add a plugin into the plugin array. This has the same effect as invoking
     * {@link #addPlugin(IAuthorizationPlugin)}.
     *
     * @param plugin the authorization plugin
     */
    protected synchronized void addPlugin(IAuthorizationPlugin plugin) {
        addPlugin(plugin, AuthControlFlag.REQUIRED);
    }

    /**
     * Add a plugin into the plugin array.
     *
     * <h3>Configured plugins</h3>
     * For plugins which have an entry in configuration, the new plugin is put
     * in the place respecting the user-defined order of exectution. If the
     * plugin has existed before, it is unloaded and the plugin is overwritten
     * with the new plugin.
     *
     * <h3>New plugins</h3>
     * If there is no entry in configuration for this class, the plugin is
     * appended to the end of the plugin container with role <code>role</code>
     *
     * <p>
     * <b>The plugin's load method is NOT invoked</b></p>
     *
     * @param plugin the authorization plugin
     * @param role the role for the new plugins
     */
    public synchronized void addPlugin(IAuthorizationPlugin plugin, AuthControlFlag role) {
        AuthorizationPluginWrapper wrapper;
        if ((wrapper = findPlugin(getClassName(plugin))) != null) {
            if (wrapper.hasPlugin()) {
                LOGGER.log(Level.FINEST, "Plugin class \"{0}\" is rewriting previously loaded plugin.",
                        getClassName(plugin));
                unloadPlugin(wrapper);
            }
            wrapper.setPlugin(plugin);
            return;
        }
        LOGGER.log(Level.INFO, "Plugin class \"{0}\" was not found in configuration."
                + " Appending the plugin at the end of the list with role \"{1}\"",
                new Object[]{getClassName(plugin), role});
        plugins.add(new AuthorizationPluginWrapper(role, getClassName(plugin), plugin));
    }

    /**
     * Remove and unload a single plugin.
     *
     * @param plugin the plugin wrapper
     * @see AuthorizationPluginWrapper#unload()
     */
    protected synchronized void removePlugin(AuthorizationPluginWrapper plugin) {
        unloadPlugin(plugin);
        plugins.remove(plugin);
    }

    /**
     * Remove and unload all plugins.
     *
     * @see AuthorizationPluginWrapper#unload()
     */
    public synchronized void removeAll() {
        unloadAllPlugins();
        plugins = new ArrayList<>();
    }

    /**
     * Call unload and load method on the plugin.
     *
     * @param plugin the plugin wrapper
     * @see AuthorizationPluginWrapper#unload()
     * @see AuthorizationPluginWrapper#load()
     */
    protected synchronized void reloadPlugin(AuthorizationPluginWrapper plugin) {
        unloadPlugin(plugin);
        loadPlugin(plugin);
    }

    /**
     * Call the load method on the plugin and set it as working if it does not
     * fail.
     *
     * @param plugin the plugin wrapper
     * @see AuthorizationPluginWrapper#load()
     */
    protected synchronized void loadPlugin(AuthorizationPluginWrapper plugin) {
        try {
            plugin.load();
            plugin.setWorking();

        } catch (Throwable ex) {
            LOGGER.log(Level.SEVERE, "Plugin \"" + plugin.getClassname() + "\" has failed while loading with exception:", ex);
            plugin.setFailed();
        }
    }

    /**
     * Load all plugins in the current array. If the plugin has not been loaded
     * yet it is marked as failed.
     */
    public void loadAllPlugins() {
        for (AuthorizationPluginWrapper plugin : getPlugins()) {
            // replace all plugins which have not been loaded (possibly they do not exist) with failing plugin
            if (!plugin.hasPlugin()) {
                LOGGER.log(Level.SEVERE, "Configured plugin \"{0}\" has not been loaded into JVM (missing file?). "
                        + "This can cause the authorization to fail always.",
                        plugin.getClassname());
                plugin.setFailed();
            }
            loadPlugin(plugin);
            LOGGER.log(Level.INFO, "[{0}] Plugin \"{1}\" {2} and is {3}.",
                    new Object[]{
                        plugin.getRole().toString().toUpperCase(),
                        plugin.getClassname(),
                        plugin.hasPlugin() ? "loaded" : "not found",
                        plugin.isWorking() ? "working" : "failed"});
        }
    }

    /**
     * Call the unload method on the plugin.
     *
     * @param plugin the plugin wrapper
     * @see AuthorizationPluginWrapper#unload()
     */
    protected synchronized void unloadPlugin(AuthorizationPluginWrapper plugin) {
        try {
            plugin.unload();
        } catch (Throwable ex) {
            LOGGER.log(Level.SEVERE, "Plugin \"" + plugin.getClassname() + "\" has failed while unloading with exception:", ex);
        }
    }

    /**
     * Unload all plugins in the current array.
     */
    public void unloadAllPlugins() {
        for (AuthorizationPluginWrapper p : getPlugins()) {
            unloadPlugin(p);
        }
    }

    /**
     * Wrapper around the class loading. Report all exceptions into the log.
     *
     * @param classname full name of the class
     *
     * @see #loadClass(String)
     */
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

    /**
     * Load a class into JVM with custom class loader. Call a non-parametric
     * constructor to create a new instance of that class.
     *
     * <p>
     * The classes implementing the {@link IAuthorizationPlugin} interface are
     * automatically added among the discovered plugins.
     * </p>
     *
     * @param classname the full name of the class to load
     *
     * @throws ClassNotFoundException when the class can not be found
     * @throws SecurityException when it is prohibited to load such class
     * @throws InstantiationException when it is impossible to create a new
     * instance of that class
     * @throws IllegalAccessException when the constructor of the class is not
     * accessible
     */
    private void loadClass(String classname) throws ClassNotFoundException,
            SecurityException,
            InstantiationException,
            IllegalAccessException {

        Class c = loader.loadClass(classname);

        // check for implemented interfaces
        Class[] intf = c.getInterfaces();
        boolean loaded = false;
        for (Class intf1 : intf) {
            if (intf1.getCanonicalName().equals(IAuthorizationPlugin.class.getCanonicalName())) {
                // call to non-parametric constructor
                IAuthorizationPlugin pf = (IAuthorizationPlugin) c.newInstance();

                addPlugin(pf);

                LOGGER.log(Level.INFO, "Plugin \"{0}\" loaded.", getClassName(pf));
                loaded = true;
            }
        }
        if (!loaded) {
            LOGGER.log(Level.FINEST, "Plugin class \"{0}\" does not implement IAuthorizationPlugin interface.", classname);
        }
    }

    /**
     * Traverse list of files which possibly contain a java class and then
     * traverse a list of jar files to load all classes which are contained
     * within them. Each class is loaded with {@link #handleLoadClass(String)}
     * which delegates the loading to the custom class loader
     * {@link #loadClass(String)}.
     *
     * @param classfiles list of files which possibly contain a java class
     * @param jarfiles list of jar files containing java classes
     *
     * @see #handleLoadClass(String)
     * @see #loadClass(String)
     */
    private void loadClasses(List<File> classfiles, List<File> jarfiles) {
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
                LOGGER.log(Level.WARNING, "Could not manipulate with file because of: ", ex);
            }
        }
    }

    private String getClassName(File f) {
        String classname = f.getAbsolutePath().substring(pluginDirectory.getAbsolutePath().length() + 1, f.getAbsolutePath().length());
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
     * This function has the same effect as calling {@code reload(new ArrayList<>())}.
     *
     * @see IAuthorizationPlugin#load()
     * @see IAuthorizationPlugin#unload()
     */
    public synchronized void reload() {
        reload(new ArrayList<>());
    }

    /**
     * Calling this function forces the framework to reload its plugins.
     *
     * Plugins are taken from the pluginDirectory (set in web.xml).
     *
     * Old instances of plugins are removed and new list of plugins is
     * constructed. Unload and load event is fired on each plugin.
     *
     * @param oldPlugins old plugins which are being discarded
     * @see IAuthorizationPlugin#load()
     * @see IAuthorizationPlugin#unload()
     */
    @SuppressWarnings("unchecked")
    public synchronized void reload(List<AuthorizationPluginWrapper> oldPlugins) {
        if (pluginDirectory == null || !pluginDirectory.isDirectory() || !pluginDirectory.canRead()) {
            LOGGER.log(Level.WARNING, "plugin directory not found or not readable: {0}. "
                    + "All requests allowed.", pluginDirectory);
            return;
        }
        LOGGER.log(Level.INFO, "Plugins are being reloaded from {0}", pluginDirectory.getAbsolutePath());

        // trashing out the old instance of the loaded enables us
        // to reaload the plugins at runtime
        loader = (AuthorizationPluginClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
                return new AuthorizationPluginClassLoader(pluginDirectory);
            }
        });

        // clean all plugins
        removeAll();

        // increase the current plugin version tracked by the framework
        increasePluginVersion();

        // add spaces with configured plugins - leaving null just to obtain the correct order of execution
        for (AuthorizationCheck check : RuntimeEnvironment.getInstance().getPluginConfiguration()) {
            plugins.add(new AuthorizationPluginWrapper(check, null));
        }

        // load all possible classes
        loadClasses(
                IOUtils.listFilesRec(pluginDirectory, ".class"),
                IOUtils.listFiles(pluginDirectory, ".jar"));

        loadAllPlugins();
    }

    /**
     * Returns the current plugins version in this framework. This can be used
     * by the plugins to invalidate the session and force reload the
     * authorization values.
     *
     * This number changes with every plugin reload.
     *
     * The plugin should call RuntimeEnvironment.getPluginVersion() to get this
     * number and act upon if it needs to renew the session.
     *
     * @return the current version number
     * @see RuntimeEnvironment#getPluginVersion()
     */
    public int getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Changes the plugin version to the next version.
     */
    public void increasePluginVersion() {
        this.pluginVersion++;
    }

    /**
     * Sets the plugin version to an arbitrary number.
     *
     * @param pluginVersion the number
     */
    public void setPluginVersion(int pluginVersion) {
        this.pluginVersion = pluginVersion;
    }

    /**
     * Checks if the request should have an access to a resource.
     *
     * <p>
     * Internally performed with a predicate. Using cache in request
     * attributes.</p>
     *
     * <h3>Order of plugin invocation</h3>
     *
     * <p>
     * The order of plugin invokation is given by the configuration
     * {@link RuntimeEnvironment#getPluginConfiguration()} and appropriate
     * actions are taken when traversing the list with set of keywords, such
     * as:</p>
     *
     * <h4>required</h4>
     * Failure of such a plugin will ultimately lead to the authorization
     * framework returning failure but only after the remaining plugins have
     * been invoked.
     *
     * <h4>requisite</h4>
     * Like required, however, in the case that such a plugin returns a failure,
     * control is directly returned to the application. The return value is that
     * associated with the first required or requisite plugin to fail.
     *
     * <h4>sufficient</h4>
     * If such a plugin succeeds and no prior required plugin has failed the
     * authorization framework returns success to the application immediately
     * without calling any further plugins in the stack. A failure of a
     * sufficient plugin is ignored and processing of the plugin list continues
     * unaffected.
     *
     * <p>
     * Loaded plugins which do not occur in the configuration are appended to
     * the list with "required" keyword. As of the nature of the class discovery
     * this means that the order of invocation of these plugins is rather
     * random.</p>
     *
     * <p>
     * Plugins in the configuration which have not been loaded are skipped.</p>
     *
     * @param request request object
     * @param cache cache
     * @param name name
     * @param predicate predicate
     * @return true if yes
     *
     * @see RuntimeEnvironment#getPluginConfiguration()
     */
    @SuppressWarnings("unchecked")
    private boolean checkAll(HttpServletRequest request, String cache, Nameable entity,
            Predicate<AuthorizationPluginWrapper> predicate) {
        Boolean val;
        Map<String, Boolean> m = (Map<String, Boolean>) request.getAttribute(cache);

        if (m == null) {
            m = new TreeMap<>();
        } else if ((val = m.get(entity.getName())) != null) {
            // cache hit
            return val;
        }

        boolean overallDecision = performCheck(entity, predicate);

        m.put(entity.getName(), overallDecision);
        request.setAttribute(cache, m);
        return overallDecision;
    }

    private boolean performCheck(Nameable entity, Predicate<AuthorizationPluginWrapper> predicate) {
        boolean overallDecision = true;
        LOGGER.log(Level.FINEST, "Authorization for \"{0}\"",
                new Object[]{entity.getName()});
        for (AuthorizationPluginWrapper plugin : getPlugins()) {
            // run the plugin's test method
            try {
                LOGGER.log(Level.FINEST, "Plugin \"{0}\" [{1}] testing a name \"{2}\"",
                        new Object[]{plugin.getClassname(), plugin.getRole(), entity.getName()});

                boolean pluginDecision = predicate.test(plugin);

                LOGGER.log(Level.FINEST, "Plugin \"{0}\" [{1}] testing a name \"{2}\" => {3}",
                        new Object[]{plugin.getClassname(), plugin.getRole(), entity.getName(),
                            pluginDecision ? "true" : "false"});

                if (!pluginDecision && plugin.isRequired()) {
                    // required sets a failure but still invokes all other plugins
                    overallDecision = false;
                    continue;
                } else if (!pluginDecision && plugin.isRequisite()) {
                    // requisite sets a failure and immediately returns the failure
                    overallDecision = false;
                    break;
                } else if (overallDecision && pluginDecision && plugin.isSufficient()) {
                    // sufficient immediately returns the success
                    overallDecision = true;
                    break;
                }
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING,
                        String.format("Plugin \"%s\" has failed the testing of \"%s\" with an exception.",
                                plugin.getClassname(),
                                entity.getName()),
                        ex);

                LOGGER.log(Level.FINEST, "Plugin \"{0}\" [{1}] testing a name \"{2}\" => {3}",
                        new Object[]{plugin.getClassname(), plugin.getRole(), entity.getName(),
                            "false (failed)"});

                // set the return value to false for this faulty plugin
                if (!plugin.isSufficient()) {
                    overallDecision = false;
                }
                // requisite plugin may immediately return the failure
                if (plugin.isRequisite()) {
                    break;
                }
            }
        }
        LOGGER.log(Level.FINEST, "Authorization for \"{0}\" => {1}",
                new Object[]{entity.getName(), overallDecision ? "true" : "false"});
        return overallDecision;
    }
}
