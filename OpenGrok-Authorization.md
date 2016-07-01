# Using OpenGrok authorization

This howto provides information about opengrok authorization with authorization framework.
You can use this module along with project groupings which allows
you to create various project structures.

## Compatibility

If plugin directory does not exist or if it contains no plugins the framework just allows
every request.

## Configuration

You can configure:

1. Plugin directory

   In web.xml for the webapp, there is a context parameter dedicated to this.

        <context-param>
            <param-name>authorizationPluginDirectory</param-name>
            <param-value>/var/opengrok/plugins</param-value>
            <description>
                 Directory with authorization plugins.
                 Default value is `OPENGROK_DATA_ROOT`/plugins
            </description>
        </context-param>
  
   By default it points to `OPENGROK_DATA_ROOT`/plugins. The `OPENGROK_DATA_ROOT` is the dataRoot directory set in the main `configuration.xml`. If no plugin is available/the directory does not exist then the framework allows every request.

   Opengrok authorization expects plugin classes in that directory, it loads those classes in
   memory as plugins and then perform the authorization for each request.

2. Watchdog service

   For easier development there is another context parameter in web.xml.

        <context-param>
            <param-name>enableAuthorizationWatchDog</param-name>
            <param-value>false</param-value>
            <description>
                Enable watching the plugin directory for changes in real time.
                Suitable for development.
            </description>
        </context-param>

   This service watches for changes (even recursive) in the plugin directory
   and if such change was done (modifing, deleting); it reloads the plugins.
   All old plugins in memory are discarded and only the plugins contained in
   plugin directory are loaded.

   **NOTE: Modification of every file in plugin directory is propagated as an event. This means that modifying bunch**
   **of files in the plugin directory leads to several reload events**
   
   By default the value is `false`.

3. Provide a plugin

   You can provide your own plugin by putting the compiled plugin class (.class or in .jar) into plugin directory.

   **NOTE: The important experience came out when using .jar files and is discussed in [problems](#problems) section.**


## Plugins

### Example

Example of a [SampleAuthorizationPlugin.java](https://github.com/OpenGrok/OpenGrok/tree/master/plugins) is included in plugins directory in the root of the git repository.


### Implementation

Each plugin must implement the IAuthorizationPlugin interface which contains four methods:

1. `void load ( void );`

   This method is called whenever the framework loads the plugin into memory
   - watchdog service enabled and change was made (forced reload)
   - deploy of the webapp

2. `void unload ( void );`

    This method is called whenever the framework destroyes the plugin
    - watchdog service enabled and change was made (forced reload)
      (the order is first unload the old instance then load the new one)
    - undeploy of the webapp

3. `boolean isAllowed(HttpServletRequest request, Project project);`

   This method gives a decision if the project should be allowed
   for the current request.

4. `boolean isAllowed(HttpServletRequest request, Group group);`

   This analogically gives the same decision for the group.

All those methods are also described in the [code](https://github.com/OpenGrok/OpenGrok/blob/master/src/org/opensolaris/opengrok/authorization/IAuthorizationPlugin.java).

Each is expected to implement some sort of a cache for the decisions when the underlying read operations for the user rights is expensive.

`HttpServletRequest` object is the current request with all of its features
like: session, attributes, `getUser()`, `getPrincipal()`

### Restrictions

Custom classloader restricts the plugin to load only this classes from org.opensolaris.opengrok package:

```java
private final static String[] classWhitelist = new String[]{
    "org.opensolaris.opengrok.configuration.Group",
    "org.opensolaris.opengrok.configuration.Project",
    "org.opensolaris.opengrok.authorization.IAuthorizationPlugin",
    "org.opensolaris.opengrok.util.*",
    "org.opensolaris.opengrok.logger.*",
};
```

And explicitly forbids these packages to be extended.

```java
private final static String[] packageBlacklist = new String[]{
    "java",
    "javax",
    "org.w3c",
    "org.xml",
    "org.omg",
    "sun"
};
```

Also JVM can forbid you to extend some packages which are not meant to be extended.

### Set up

The plugin class must be compiled to the .class file (and the it can be packaged into .jar file). The frameworks supports both .class and .jar files. For compiling you have to provide opengrok.jar and the servlet api (`HttpServletRequest`) in the classpath

Example (for the SampleAuthorizaionPlugin which is included):
`$ javac -classpath dist/opengrok.jar -d . plugins/SampleAuthorizationPlugin.java`

Then you can just drop the compiled .class file into plugin directory and deploy the webapp.
If the plugin is a part of a package. Then you have to copy the full directory path which is made
by the compiler relatively to the source file.

If anything goes wrong, you should find information in the logs.

### Running

The framework consists of three parts

1. PluginFramework

   Plugin container
   - performs the authorization decision
   - cache the decisions so that for each request the particular
     plugin's `isAllowed` for particular project/group is called only once

2. ProjectHelper
   UI facade for filtered (authorized) projects/groups/repositories
   which should be ONLY used for displaying information on main page
   - provides methods how to get filtered projects/groups/repositories
   - cache the filtered results for each request so that it does not have
     to call the framework unnecessarily

3. AuthorizationFilter

   Standard `javax.servlet.Filter` which is used for all urls to decide if
   the current request has access to the url
   - restricts user to go to forbidden xref/history/download etc. with 403 HTTP error
   - reacts only when the url contains information about project (so it can decide per project)

Every 403 error is logged. But information about the user is missing
because the decision is made by plugins and the filter does not know
how to interpret the request to get the user from it.

### Facing problems<a name="problems"></a>

#### Using IDE
When using IDE (NetBeans) to build your plugin; you can face a problem when the framework does not load your .jar file with `ClassFormatError` resulting to `ClassNotFoundException`.

Possible solutions are:
  1. disable debugging symbols (project/properities/build/compile/uncheck generate debugging info)
  2. compile the .java files with `javac` manually and then package it into .jar manually (command above)
  3. compile the .java files with `javac` manually and use this directory structure (without packaging)

This should solve the problem. If it does not then try to change the code.
