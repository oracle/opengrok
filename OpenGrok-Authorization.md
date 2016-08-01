# Table of contents

<!-- toc -->

- [Using OpenGrok authorization](#using-opengrok-authorization)
  * [Compatibility](#compatibility)
  * [Configuration](#configuration)
  * [Plugins](#plugins)
    + [Example](#example)
    + [Implementation](#implementation)
    + [Restrictions](#restrictions)
    + [Set up](#set-up)
    + [Running](#running)
- [HTTP Basic Tutorial](#http-basic-tutorial)
  * [Setting up Tomcat](#setting-up-tomcat)
    + [Tomcat users](#tomcat-users)
    + [Application deployment descriptor](#application-deployment-descriptor)
  * [Setting up the repositories](#setting-up-the-repositories)
  * [Setting up the groupings](#setting-up-the-groupings)
    + [`READ_XML_CONFIGURATION`](#read_xml_configuration)
  * [Index](#index)
  * [The plugin](#the-plugin)
    + [Permission policy](#permission-policy)
    + [Group discovery](#group-discovery)
    + [Authorization check](#authorization-check)
  * [Running](#running-1)
  * [Complete code](#complete-code)
- [Troubleshooting](#troubleshooting)
  * [Using IDE](#using-ide)

<!-- tocstop -->

# Using OpenGrok authorization

This howto provides information about opengrok authorization with authorization framework.
You can use this module along with [project groupings](https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Groupings) which allows
you to create various project structures.

## Compatibility

If plugin directory does not exist or if it contains no plugins the framework just allows
every request.

## Configuration

You can configure:

1. Plugin directory

   In web.xml for the webapp, there is a context parameter dedicated to this.
	```xml
	<context-param>
	    <param-name>authorizationPluginDirectory</param-name>
	    <param-value>/var/opengrok/plugins</param-value>
	    <description>
		 Directory with authorization plugins.
		 Default value is `OPENGROK_DATA_ROOT`/plugins
	    </description>
	</context-param>
	```
   By default it points to `OPENGROK_DATA_ROOT`/plugins. The `OPENGROK_DATA_ROOT` is the dataRoot directory set in the main `configuration.xml`. If no plugin is available/the directory does not exist then the framework allows every request.

   Opengrok authorization expects plugin classes in that directory, it loads those classes in
   memory as plugins and then perform the authorization for each request.

2. Watchdog service

   For easier development there is another context parameter in `web.xml`.
	```xml
	<context-param>
	    <param-name>enableAuthorizationWatchDog</param-name>
	    <param-value>false</param-value>
	    <description>
		Enable watching the plugin directory for changes in real time.
		Suitable for development.
	    </description>
	</context-param>
	```
   This service watches for changes (even recursive) in the plugin directory
   and if such change was done (modifing, deleting); it reloads the plugins.
   All old plugins in memory are discarded and only the plugins contained in
   plugin directory are loaded.

   **NOTE: Modification of every file in plugin directory is propagated as an event. This means that modifying bunch**
   **of files in the plugin directory leads to several reload events**
   
   By default the value is `false`.

3. Provide a plugin

   You can provide your own plugin by putting the compiled plugin class (.class or in .jar) into plugin directory.

   **NOTE: The important experience came out when using .jar files and is discussed in [troubleshooting](#troubleshooting) section.**


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

   This is the plugin container
   - performs the authorization decision
   - cache the decisions so that for each request the particular
     plugin's `isAllowed` for particular project/group is called only once

2. ProjectHelper

   UI facade for filtered (authorized) projects/groups/repositories
   which should be **ONLY** used for displaying filtered information anywhere
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

# HTTP Basic Tutorial
This is an example using HTTP Basic authorization. 

This example has several presumptions:

1. you have a clean working OpenGrok instance on your machine (no projects, no groups)
2. you are able to use `Groups` subcommand (available when you clone the github repository under tools)
3. you have the permission to modify Tomcat files
4. you have set the plugin directory or you know where it is ([described above](#configuration))

## Setting up Tomcat
HTTP Basic is supported by Tomcat and we can configure a simple example including users and roles.
How we can set up the Tomcat is described in [this tutorial](http://www.avajava.com/tutorials/lessons/how-do-i-use-basic-authentication-with-tomcat.html) using users and roles defined by us.

### Tomcat users
We have to modify a Tomcat file to provide information about users and roles in the system. This file is placed in `$CATALINA_BASE/conf/tomcat-users.xml`.
For purpose of this example add these lines to the file inside the element `<tomcat-users>`.
```xml
<role rolename="users"/>
<role rolename="admins"/>
<role rolename="plugins"/>
<role rolename="ghost"/>

<user username="007" password="123456" roles="users"/>
<user username="008" password="123456" roles="plugins"/>
<user username="009" password="123456" roles="users,admins"/>
<user username="00A" password="123456" roles="admins"/>
<user username="00B" password="123456" roles="admins,plugins"/>
<user username="00F" password="123456" roles="ghost"/>
```
With these lines we tell Tomcat to set up 4 roles and 6 users with their roles assigned. You can modify the usernames and passwords to fit your needs but
we will use those in this example.

### Application deployment descriptor
Now we have to tell the application that it should use HTTP Basic authentication to protect its sources. We can do this by modifying a `web.xml` file which is usually placed in the `WEB-INF` directory in your application. Following lines are necessary to get it work.

```xml
<security-constraint>
    <web-resource-collection>
        <url-pattern>/*</url-pattern> <!-- protect the whole application -->
        <http-method>GET</http-method>
        <http-method>POST</http-method>
    </web-resource-collection>
    <auth-constraint>
        <role-name>plugins</role-name> <!-- these are the roles from tomcat-users.xml -->
        <role-name>users</role-name> <!-- these are the roles from tomcat-users.xml -->
        <role-name>admins</role-name> <!-- these are the roles from tomcat-users.xml -->
    </auth-constraint>

    <user-data-constraint>
        <!-- transport-guarantee can be CONFIDENTIAL, INTEGRAL, or NONE -->
        <transport-guarantee>NONE</transport-guarantee>
    </user-data-constraint>
</security-constraint>

<security-role>
    <role-name>plugins</role-name>
    <role-name>users</role-name>
    <role-name>admins</role-name>
</security-role>

<login-config>
    <auth-method>BASIC</auth-method>
</login-config>
```

Being in the `web.xml` we would recommend you to turn on the [watchdog service](#configuration) which is suitable for developing plugins, setting this parameter to `true`.
```xml
<context-param>
  <param-name>enableAuthorizationWatchDog</param-name>
  <param-value>false</param-value>
  <description>
      Enable watching the plugin directory for changes in real time.
      Suitable for development.
  </description>
</context-param>
```

This forces the application to reload all plugins in the plugin directory when a modification of any file inside it occurs.

## Setting up the repositories
We need to create a couple of test repositories to show the authorization features.
```
$ cd "$OPENGROK_SRC_ROOT" # navigate to the source folder
$ for name in `seq 1 11`; do mkdir "test-project-$name"; cd "test-project-$name"; echo "Give it a try! Hello from $i" > README.md; git init; git config user.email "x"; git config user.name "y"; git add .; git commit -m "init commit"; cd ..; done;
```

This should create 10 repositories named "test-project-$number" in the source directory.

## Setting up the groupings
In order to use the authorization feature really comfortably it is recommended to create groups of projects. In the authorization plugin you can then use the whole groups instead of single projects.

We are going to use a [tool](https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Groupings#tools) called `Groups` to create these.
```
$ export READ_XML_CONFIGURATION=/var/opengrok/opt/myconf.xml # [described here]()
$ ./tools/Groups add admins  "test-project-1|test-project-2|test-project-3|test-project-4" -u
$ ./tools/Groups add users   "test-project-5|test-project-6|test-project-7|test-project-8" -u
$ ./tools/Groups add plugins "test-project-9|test-project-10" -p users -u
```

The group names correspond to the roles defined in `tomcat-users.xml` earlier.
The final group structure should look like this now:
```
$ ./tools/Groups list
admins ~ "test-project-1|test-project-2|test-project-3|test-project-4"
users ~ "test-project-5|test-project-6|test-project-7|test-project-8"
    plugins ~ "test-project-9|test-project-10"
```

### `READ_XML_CONFIGURATION`

This variable contains a path to an xml file which eventually gets merged with the main configuration (usually `/var/opengrok/configuration.xml`) before index/reindex. If you have not used this before you can generate a new one with this (for example):

```
$ export READ_XML_CONFIGURATION=/var/opengrok/opt/myconf.xml
$ ./tools/Groups add admins "test-project-1|test-project-2|test-project-3|test-project-4" > "$READ_XML_CONFIGURATION"
```

This command generates a configuration with one group "admins" and saves that into "`$READ_XML_CONFIGURATION`" file.

## Index

Now run the index as usual. Do not forget to use the `READ_XML_CONFIGURATION` variable used in previous step as that will pass the groups into the main configuration file.
**This is the only option how to preserve groups on reindex - use a `READ_XML_CONFIGURATION` variable!**

## The plugin
Now comes the main part - the plugin itself.

It consists of three parts:

1. Permission policy
2. Group discovery
3. Authorization check

### Permission policy
You can use whatever policy you want using even external tools in java or you OS. In this example we will use static map defining which user or group has access to which projects.

```java
private static final Map<String, Set<String>> userProjects = new TreeMap<>();
private static final Map<String, Set<String>> userGroups = new TreeMap<>();

static {
    // all have access to "test-project-11" and some to other "test-project-5" or "test-project-8"
    userProjects.put("007", new TreeSet<>(Arrays.asList(new String[]{"test-project-11", "test-project-5"})));
    userProjects.put("008", new TreeSet<>(Arrays.asList(new String[]{"test-project-11", "test-project-8"})));
    userProjects.put("009", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
    userProjects.put("00A", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
    userProjects.put("00B", new TreeSet<>(Arrays.asList(new String[]{"test-project-11"})));
}

static {
    userGroups.put("007", new TreeSet<>(Arrays.asList(new String[]{})));
    userGroups.put("008", new TreeSet<>(Arrays.asList(new String[]{})));
    userGroups.put("009", new TreeSet<>(Arrays.asList(new String[]{})));
    userGroups.put("00A", new TreeSet<>(Arrays.asList(new String[]{})));
    userGroups.put("00B", new TreeSet<>(Arrays.asList(new String[]{})));
}
```

### Group discovery

The plugin framework which works above our plugin has no idea how specific our plugins wants to be when it does the decisions so there is no way of automate discovery of groups/subgroups/projects in groups. All has to be done in our plugin, preferably only once the plugin is loaded/first used for the particular user (not included in this example).

**The very important implication is that allowing a group does not allow its subgroups neither projects.** Consider this as a feature since it gives you more freedom for allowing or disallowing particular groups and projects.

This is the most important part - if you have found a group for the user then add all of its projects, repositories, subgroups and their underlying objects.
```java
private void init(HttpServletRequest request) {

        // ...

        if ((g = Group.getByName(group)) != null) {
            // group discovery
            for (Project p : g.getRepositories()) {
                userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
            }
            for (Project p : g.getProjects()) {
                userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
            }
            for (Group grp : g.getDescendants()) {
                for (Project p : grp.getRepositories()) {
                    userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
                }
                for (Project p : grp.getProjects()) {
                    userProjects.get(request.getUserPrincipal().getName()).add(p.getDescription());
                }
                descendants.add(grp.getName());
            }
            while (g != null) {
                descendants.add(g.getName());
                g = g.getParent();
            }

        }
    }
    userGroups.get(request.getUserPrincipal().getName()).addAll(descendants);
}
```
Eventually, the `userProjects` and `userGroups` are maps of type User-Projects/Groups where we can quickly decide whether the user has the permission for the given entity
by just looking into the map and the corresponding set.

### Authorization check

The final authorization check is just simple - check if the map contains such Project/Group for the certain user.
```java
// for projects
return userProjects.get(request.getUserPrincipal().getName()).contains(project.getDescription());
// or for groups
return userGroups.get(request.getUserPrincipal().getName()).contains(group.getName());
```

## Running

Now you can compile the plugin as described above in this wiki page and place it into the plugin directory (default `$OPENGROK_DATA_ROOT/plugins`).
If you enabled watchdog service then you are ready to see the results, otherwise you need to restart the application.

When you enter the application, the page immediately fires an login form where you can enter the credentials (as written in [tomcat users](#tomcat-users)).
Depending on what you have entered you should see filtered results on the main page and even when you search for anything you should not be able to access other projects.

You can try other accounts by logging out by forgetting the session (`ctrl+shift+delete` + forget current session/this can vary in different browsers).

## Complete code
[HttpBasicAuthorizationPlugin.java](https://github.com/OpenGrok/OpenGrok/blob/master/plugins/src/main/java/HttpBasicAuthorizationPlugin.java)


# Troubleshooting

## Using IDE
When using IDE (NetBeans) to build your plugin; you can face a problem when the framework does not load your .jar file with `ClassFormatError` resulting to `ClassNotFoundException`.

Possible solutions are:
  1. disable debugging symbols (project/properities/build/compile/uncheck generate debugging info)
  2. compile the .java files with `javac` manually and then package it into .jar manually (command above)
  3. compile the .java files with `javac` manually and use this directory structure (without packaging)

This should solve the problem. If it does not then try to change the code.