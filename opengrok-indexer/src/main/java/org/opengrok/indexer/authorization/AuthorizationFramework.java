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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.opengrok.indexer.configuration.Configuration;
import org.opengrok.indexer.configuration.Group;
import org.opengrok.indexer.configuration.Nameable;
import org.opengrok.indexer.configuration.Project;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.IOUtils;
import org.opengrok.indexer.web.Statistics;

/**
 * Placeholder for performing authorization checks.
 *
 * @author Krystof Tulinger
 */
public final class AuthorizationFramework {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationFramework.class);

    /**
     * Plugin directory.
     */
    private File pluginDirectory;

    /**
     * Customized class loader for plugin classes.
     */
    private AuthorizationPluginClassLoader loader;

    /**
     * Stack of available plugins/stacks in the order of the execution.
     */
    AuthorizationStack stack;
    /**
     * New stack. This is set by {@code setStack()} and used for delayed
     * reconfiguration in {@code reload()}.
     */
    AuthorizationStack newStack;

    /**
     * Lock for safe reloads.
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Keeping track of the number of reloads in this framework. This is
     * used to invalidate the session and force reload the authorization values
     * stored in HTTP session.
     *
     * Starting at 0 and increases with every reload.
     */
    private long pluginVersion = 0;

    /**
     * Whether to load plugins from class files and jar files.
     */
    private boolean loadClasses = true;
    private boolean loadJars = true;
    
    // HTTP session attribute that holds plugin version 
    private final static String SESSION_VERSION = "opengrok-authorization-session-version";
    
    /**
     * Create a new instance of authorization framework with no plugin
     * directory and the default plugin stack.
     */
    public AuthorizationFramework() {
        this(null);
    }
    
    /**
     * Create a new instance of authorization framework with the plugin
     * directory and the default plugin stack.
     *
     * @param path the plugin directory path
     */
    public AuthorizationFramework(String path) {
        this(path, new AuthorizationStack(AuthControlFlag.REQUIRED, "default stack"));
    }

    /**
     * Create a new instance of authorization framework with the plugin
     * directory and the plugin stack.
     *
     * @param path the plugin directory path
     * @param stack the top level stack configuration
     */
    public AuthorizationFramework(String path, AuthorizationStack stack) {
        this.stack = stack;
        setPluginDirectory(path);
    }

    /**
     * Get the plugin directory.
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
     * @param flag true or false
     */
    public void setLoadClasses(boolean flag) {
        loadClasses = flag;
    }
    
    /**
     * Whether to search for plugins in class files.
     * @return true if enabled, false otherwise
     */
    public boolean isLoadClassesEnabled() {
        return loadClasses;
    }
    
    /**
     * Make {@code reload()} search for plugins in jar files.
     * @param flag true or false
     */
    public void setLoadJars(boolean flag) {
        loadJars = flag;
    }
    
    /**
     * Whether to search for plugins in class files.
     * @return true if enabled, false otherwise
     */
    public boolean isLoadJarsEnabled() {
        return loadJars;
    }

    /**
     * Checks if the request should have an access to project. See
     * {@link #checkAll} for more information about invocation order.
     *
     * @param request request object
     * @param project project object
     * @return true if yes
     *
     * @see #checkAll
     */
    public boolean isAllowed(HttpServletRequest request, Project project) {
        return checkAll(
                request,
                "plugin_framework_project_cache",
                project,
                new AuthorizationEntity.PluginDecisionPredicate() {
                    @Override
                    public boolean decision(IAuthorizationPlugin plugin) {
                        return plugin.isAllowed(request, project);
                    }
        }, new AuthorizationEntity.PluginSkippingPredicate() {
            @Override
            public boolean shouldSkip(AuthorizationEntity authEntity) {
                // shouldn't skip if there is no setup
                if (authEntity.forProjects().isEmpty() && authEntity.forGroups().isEmpty()) {
                    return false;
                }

                // shouldn't skip if the project is contained in the setup
                if (authEntity.forProjects().contains(project.getName())) {
                    return false;
                }

                return true;
            }
        });
    }

    /**
     * Checks if the request should have an access to group. See
     * {@link #checkAll} for more information about invocation order.
     *
     * @param request request object
     * @param group group object
     * @return true if yes
     *
     * @see #checkAll
     */
    public boolean isAllowed(HttpServletRequest request, Group group) {
        return checkAll(
                request,
                "plugin_framework_group_cache",
                group,
                new AuthorizationEntity.PluginDecisionPredicate() {
                    @Override
                    public boolean decision(IAuthorizationPlugin plugin) {
                        return plugin.isAllowed(request, group);
                    }
        }, new AuthorizationEntity.PluginSkippingPredicate() {
            @Override
            public boolean shouldSkip(AuthorizationEntity authEntity) {
                // shouldn't skip if there is no setup
                if (authEntity.forProjects().isEmpty() && authEntity.forGroups().isEmpty()) {
                    return false;
                }

                // shouldn't skip if the group is contained in the setup
                return !authEntity.forGroups().contains(group.getName());
            }
        });
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
     * This method and couple of following methods use locking because
     * <ol>
     * <li>plugins can be reloaded at anytime</li>
     * <li>requests are pretty asynchronous</li>
     * </ol>
     *
     * So this tries to ensure that there will be no
     * {@code ConcurrentModificationException} or other similar exceptions.
     *
     * @return the stack containing plugins/other stacks
     */
    public AuthorizationStack getStack() {
        lock.readLock().lock();
        try {
            return stack;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Set new value of the authorization stack. This will come into effect
     * only after {@code reload()} is called.
     *
     * @param s new stack to be used
     */
    public void setStack(AuthorizationStack s) {
        this.newStack = s;
    }

    /**
     * Add an entity into the plugin stack.
     *
     * @param stack the stack
     * @param entity the authorization entity (stack or plugin)
     */
    protected void addPlugin(AuthorizationStack stack, AuthorizationEntity entity) {
        if (stack != null) {
            stack.add(entity);
        }
    }

    /**
     * Add a plug-in into the plug-in stack. This has the same effect as invoking
     * addPlugin(stack, IAuthorizationPlugin, REQUIRED).
     *
     * @param stack the stack
     * @param plugin the authorization plug-in
     */
    public void addPlugin(AuthorizationStack stack, IAuthorizationPlugin plugin) {
        addPlugin(stack, plugin, AuthControlFlag.REQUIRED);
    }

    /**
     * Add a plug-in into the plug-in array.
     *
     * <h3>Configured plugin</h3>
     * For plug-in that has an entry in configuration, the new plug-in is put
     * in the place respecting the user-defined order of execution.
     *
     * <h3>New plugin</h3>
     * If there is no entry in configuration for this class, the plugin is
     * appended to the end of the plugin stack with flag <code>flag</code>
     * 
     * <p><b>The plug-in's load method is NOT invoked at this point</b></p>
     *
     * This has the same effect as invoking
     * {@code addPlugin(new AuthorizationEntity(stack, flag,
     * getClassName(plugin), plugin)}.
     *
     * @param stack the stack
     * @param plugin the authorization plug-in
     * @param flag the flag for the new plug-in
     */
    public void addPlugin(AuthorizationStack stack, IAuthorizationPlugin plugin, AuthControlFlag flag) {
        if (stack != null) {
            LOGGER.log(Level.WARNING, "Plugin class \"{0}\" was not found in configuration."
                    + " Appending the plugin at the end of the list with flag \"{1}\"",
                    new Object[]{getClassName(plugin), flag});
            addPlugin(stack, new AuthorizationPlugin(flag, getClassName(plugin), plugin));
        }
    }

    /**
     * Remove and unload all plugins from the stack.
     *
     * @see AuthorizationEntity#unload()
     */
    public void removeAll() {
        unloadAllPlugins(stack);
        stack.getStack().clear();
    }
    
    private void removeAll(AuthorizationStack stack) {
        unloadAllPlugins(stack);
        stack.getStack().clear();
    }

    /**
     * Load all plugins in the stack. If any plugin has not been loaded yet it
     * is marked as failed.
     *
     * @param stack the stack
     */
    public void loadAllPlugins(AuthorizationStack stack) {
        if (stack != null) {
            stack.load(new TreeMap<>());
        }
    }

    /**
     * Unload all plugins in the stack
     *
     * @param stack the stack
     */
    public void unloadAllPlugins(AuthorizationStack stack) {
        if (stack != null) {
            stack.unload();
        }
    }

    /**
     * Wrapper around the class loading. Report all exceptions into the log.
     *
     * @param classname full name of the class
     * @return the class implementing the {@link IAuthorizationPlugin} interface
     * or null if there is no such class
     *
     * @see #loadClass(String)
     */
    public IAuthorizationPlugin handleLoadClass(String classname) {
        try {
            return loadClass(classname);
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.WARNING, String.format("Class \"%s\" was not found: ", classname), ex);
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
     *
     * <p>
     * The classes implementing the {@link IAuthorizationPlugin} interface are
     * returned and initialized with a call to a non-parametric constructor.
     * </p>
     *
     * @param classname the full name of the class to load
     * @return the class implementing the {@link IAuthorizationPlugin} interface
     * or null if there is no such class
     *
     * @throws ClassNotFoundException when the class can not be found
     * @throws SecurityException when it is prohibited to load such class
     * @throws InstantiationException when it is impossible to create a new
     * instance of that class
     * @throws IllegalAccessException when the constructor of the class is not
     * accessible
     * @throws NoSuchMethodException when the class does not have no-argument constructor
     * @throws InvocationTargetException if the underlying constructor of the class throws an exception
     */
    private IAuthorizationPlugin loadClass(String classname) throws ClassNotFoundException,
            SecurityException,
            InstantiationException,
            IllegalAccessException,
            NoSuchMethodException,
            InvocationTargetException {

        Class<?> c = loader.loadClass(classname);

        // check for implemented interfaces
        for (Class intf1 : getInterfaces(c)) {
            if (intf1.getCanonicalName().equals(IAuthorizationPlugin.class.getCanonicalName())
                    && !Modifier.isAbstract(c.getModifiers())) {
                // call to non-parametric constructor
                return (IAuthorizationPlugin) c.getDeclaredConstructor().newInstance();
            }
        }
        LOGGER.log(Level.FINEST, "Plugin class \"{0}\" does not implement IAuthorizationPlugin interface.", classname);
        return null;
    }

    /**
     * Get all available interfaces of a class c.
     *
     * @param c class
     * @return array of interfaces of the class c
     */
    protected List<Class> getInterfaces(Class c) {
        List<Class> interfaces = new LinkedList<>();
        Class self = c;
        while (self != null && !interfaces.contains(IAuthorizationPlugin.class)) {
            interfaces.addAll(Arrays.asList(self.getInterfaces()));
            self = self.getSuperclass();
        }
        return interfaces;
    }

    /**
     * Traverse list of files which possibly contain a java class 
     * to load all classes which are contained within them into the given stack.
     * Each class is loaded with {@link #handleLoadClass(String)} which
     * delegates the loading to the custom class loader
     * {@link #loadClass(String)}.
     *
     * @param stack the stack where to add the loaded classes
     * @param classfiles list of files which possibly contain a java class
     *
     * @see #handleLoadClass(String)
     * @see #loadClass(String)
     */
    private void loadClassFiles(AuthorizationStack stack, List<File> classfiles) {
        IAuthorizationPlugin pf;
        
        for (File file : classfiles) {
            String classname = getClassName(file);
            if (classname.isEmpty()) {
                continue;
            }
            // Load the class in memory and try to find a configured space for this class.
            if ((pf = handleLoadClass(classname)) != null) {
                if (!stack.setPlugin(pf)) {
                    LOGGER.log(Level.INFO, "plugin {0} is not configured in the stack",
                            classname);
                }
            }
        }
    }

    /**
     * Traverse list of jar files to load all classes which are contained within
     * them into the given stack.
     * Each class is loaded with {@link #handleLoadClass(String)} which
     * delegates the loading to the custom class loader
     * {@link #loadClass(String)}.
     * 
     * @param stack the stack where to add the loaded classes
     * @param jarfiles list of jar files containing java classes
     * 
     * @see #handleLoadClass(String)
     * @see #loadClass(String)
     */
    private void loadJarFiles(AuthorizationStack stack, List<File> jarfiles) {
        IAuthorizationPlugin pf;

        for (File file : jarfiles) {
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String classname = getClassName(entry);
                    if (!entry.getName().endsWith(".class") || classname.isEmpty()) {
                        continue;
                    }
                    // Load the class in memory and try to find a configured space for this class.
                    if ((pf = handleLoadClass(classname)) != null) {
                        if (!stack.setPlugin(pf)) {
                            LOGGER.log(Level.INFO, "plugin {0} is not configured in the stack",
                                    classname);
                        }
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
        classname = classname.substring(0, classname.lastIndexOf('.')); // strip .class
        return classname;
    }

    private String getClassName(JarEntry f) {
        String classname = f.getName().replace(File.separatorChar, '.'); // convert to package name
        return classname.substring(0, classname.lastIndexOf('.'));  // strip .class
    }

    /**
     * Calling this function forces the framework to reload its stack.
     *
     * <p>
     * Plugins are taken from the pluginDirectory.</p>
     *
     * <p>
     * Old instances of stack are removed and new list of stack is constructed.
     * Unload and load event is fired on each plugin.</p>
     *
     * <p>
     * This method is thread safe with respect to the currently running
     * authorization checks.</p>
     *
     * @see IAuthorizationPlugin#load(java.util.Map)
     * @see IAuthorizationPlugin#unload()
     * @see Configuration#getPluginDirectory()
     */
    @SuppressWarnings("unchecked")
    public void reload() {
        if (pluginDirectory == null || !pluginDirectory.isDirectory() || !pluginDirectory.canRead()) {
            LOGGER.log(Level.WARNING, "Plugin directory not found or not readable: {0}. "
                    + "All requests allowed.", pluginDirectory);
            return;
        }
        if (stack == null) {
            LOGGER.log(Level.WARNING, "Plugin stack not found in configuration: null. All requests allowed.");
            return;
        }

        LOGGER.log(Level.INFO, "Plugins are being reloaded from {0}", pluginDirectory.getAbsolutePath());

        // trashing out the old instance of the loaded enables us
        // to reload the stack at runtime
        loader = (AuthorizationPluginClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
                return new AuthorizationPluginClassLoader(pluginDirectory);
            }
        });

        AuthorizationStack newLocalStack;
        if (this.newStack == null) {
            // Clone a new stack not interfering with the current stack.
            newLocalStack = getStack().clone();
        } else {
            newLocalStack = this.newStack.clone();
        }
        
        // Load all other possible plugin classes.
        if (isLoadClassesEnabled()) {
            loadClassFiles(newLocalStack, IOUtils.listFilesRec(pluginDirectory, ".class"));
        }
        if (isLoadJarsEnabled()) {
            loadJarFiles(newLocalStack, IOUtils.listFiles(pluginDirectory, ".jar"));
        }

        // fire load events
        loadAllPlugins(newLocalStack);

        AuthorizationStack oldStack;
        /**
         * Replace the stack in a write lock to avoid inconsistent state between
         * the stack change and currently executing requests performing some
         * authorization on the same stack.
         *
         * @see #performCheck is controlled with a read lock
         */
        lock.writeLock().lock();
        try {
            oldStack = stack;
            stack = newLocalStack;
            
            // increase the current plugin version tracked by the framework
            increasePluginVersion();
        } finally {
            lock.writeLock().unlock();
        }

        Statistics stats = RuntimeEnvironment.getInstance().getStatistics();
        stats.addRequest("authorization_stack_reload");
        
        // clean the old stack
        removeAll(oldStack);
        oldStack = null;
    }

    /**
     * Returns the current plugin version in this framework.
     * 
     * This number changes with every {@code reload()}.
     * 
     * Assumes the {@code lock} is held for reading.
     *
     * @return the current version number
     */
    private long getPluginVersion() {
        return pluginVersion;
    }

    /**
     * Changes the plugin version to the next version.
     * 
     * Assumes that {@code lock} is held for writing.
     */
    private void increasePluginVersion() {
        this.pluginVersion++;
    }

    /**
     * Is this session marked as invalid?
     *
     * Assumes the {@code lock} is held for reading.
     *
     * @param req the request
     * @return true if it is; false otherwise
     */
    private boolean isSessionInvalid(HttpSession session) {
        if (session.getAttribute(SESSION_VERSION) == null) {
            return true;
        }
        
        long version = (long) session.getAttribute(SESSION_VERSION);

        return version != getPluginVersion();
    }

    /**
     * Checks if the request should have an access to a resource. This method is
     * thread safe with respect to the concurrent reload of plugins.
     *
     * <p>
     * Internally performed with a predicate. Using cache in request
     * attributes.</p>
     *
     * <h3>Order of plugin invocation</h3>
     *
     * <p>
     * The order of plugin invocation is given by the stack and appropriate
     * actions are taken when traversing the stack with set of keywords,
     * such as:</p>
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
     * @see RuntimeEnvironment#getPluginStack()
     */
    @SuppressWarnings("unchecked")
    private boolean checkAll(HttpServletRequest request, String cache, Nameable entity,
            AuthorizationEntity.PluginDecisionPredicate pluginPredicate,
            AuthorizationEntity.PluginSkippingPredicate skippingPredicate) {
        
        if (stack == null) {
            return true;
        }

        Statistics stats = RuntimeEnvironment.getInstance().getStatistics();

        Boolean val;
        Map<String, Boolean> m = (Map<String, Boolean>) request.getAttribute(cache);

        if (m == null) {
            m = new TreeMap<>();
        } else if ((val = m.get(entity.getName())) != null) {
            // cache hit
            stats.addRequest("authorization_cache_hits");
            return val;
        }

        stats.addRequest("authorization_cache_misses");

        long time = 0;
        boolean overallDecision = false;
        
        lock.readLock().lock();
        try {
            // Make sure there is a HTTP session that corresponds to current plugin version.
            HttpSession session;
            if (((session = request.getSession(false)) != null) && isSessionInvalid(session)) {
                session.invalidate();
                stats.addRequest("authorization_sessions_invalidated");
            }
            request.getSession().setAttribute(SESSION_VERSION, getPluginVersion());

            time = System.currentTimeMillis();

            overallDecision = performCheck(entity, pluginPredicate, skippingPredicate);
        } finally {
            lock.readLock().unlock();
        }

        if (time > 0) {
            time = System.currentTimeMillis() - time;

            stats.addRequestTime("authorization", time);
            stats.addRequestTime(
                    String.format("authorization_%s", overallDecision ? "positive" : "negative"),
                    time);
            stats.addRequestTime(
                    String.format("authorization_%s_of_%s", overallDecision ? "positive" : "negative", entity.getName()),
                    time);
            stats.addRequestTime(
                    String.format("authorization_of_%s", entity.getName()),
                    time);
        }
        
        m.put(entity.getName(), overallDecision);
        request.setAttribute(cache, m);
        
        return overallDecision;
    }

    /**
     * Perform the actual check for the entity.
     *
     * Assumes that {@code lock} is held in read mode.
     * 
     * @param entity either a project or a group
     * @param pluginPredicate a predicate that decides if the authorization is
     * successful for the given plugin
     * @param skippingPredicate predicate that decides if given authorization
     * entity should be omitted from the authorization process
     * @return true if entity is allowed; false otherwise
     */
    private boolean performCheck(Nameable entity,
            AuthorizationEntity.PluginDecisionPredicate pluginPredicate,
            AuthorizationEntity.PluginSkippingPredicate skippingPredicate) {
        
            return stack.isAllowed(entity, pluginPredicate, skippingPredicate);
    }
}
