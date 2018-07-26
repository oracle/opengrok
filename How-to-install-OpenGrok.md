OpenGrok can be installed and used under different use cases, we will just show how to use the wrapper script. Advanced usage depends on your knowledge of running java applications and command line options of OpenGrok.
Note, that you '''need''' the create the index no matter what is your use case. Without indexes Opengrok will be simply useless.

# Requirements

You need the following:

- [JDK](http://www.oracle.com/technetwork/java/) 1.8 or higher
- {OpenGrok '''binaries''' from https://github.com/OpenGrok/OpenGrok/releases (.tar.gz file with binaries, not the source code tarball !)
- https://github.com/universal-ctags for analysis (Exuberant ctags can be used too however they are not as supported as Universal ctags)
- A servlet container like [GlassFish](https://glassfish.dev.java.net/) or [Tomcat](http://tomcat.apache.org) 8.0 or later also running with Java at least 1.8
- If history is needed, appropriate binaries (in some cases also cvs/svn repository) must be present on the system (e.g. [Subversion](http://subversion.tigris.org) or [Mercurial](http://www.selenic.com/mercurial/wiki/index.cgi) or SCCS or ... )
- 2GB of memory for indexing process using OpenGrok script (bigger deployments will need more)
- a recent browser for clients - IE, Firefox, recent Chrome or Safari
- Optional tuning (see https://github.com/oracle/opengrok/wiki/Tuning-for-large-code-bases)

After unpacking the binaries to your target directory, the index needs to be created and the web application deployed.

# Creating the index

The data to be indexed should be stored in a directory called **source root**. Each subdirectory under this directory is called **project** (projects can be disabled but let's leave this detail aside for now) and usually contains checkout of a **repository** (or it's branch, version, ...) sources. Each project can have multiple repositories.

The concept of projects was introduced to effectively replace the need for more web applications with opengrok <code>.war</code> and leave you with one indexer and one web application serving MORE source code repositories - projects.

[[/images/setup-project.png]]

The index data will be created under directory called **data root**.

## <u>Step.0</u> - Setting up the sources

Source base should be available locally for OpenGrok to work efficiently. No changes are required to your source tree. If the code is under CVS or SVN, OpenGrok requires the '''checked out source''' tree under source root.

## <u>Step.1</u> - Deploy the web application

Install web application container of your choice (e.g. [Tomcat](http://tomcat.apache.org/), [Glassfish](https://glassfish.dev.java.net/)).

Use the `deploy.py` script to copy the `.war` file to the location where the application container will detect it and deploy the web application.

## <u>Step.2</u> - Indexing

This steps basically performs these steps:
  - create index
  - let the indexer generate the configuration file
  - notify the web application that new index is available

The indexing can take a lot of time - for large code bases (meaning both amount of source code and history) it can take many hours. After this is done, indexer automatically attempts to upload newly generated configuration to the web application. Until this is done, the web application will display the old state.

The indexer can be run either using `opengrok.jar` directly or using the `indexer.py` wrapper like so:

```
indexer.py -J=-Djava.util.logging.config.file=/var/opengrok/logging.properties \
    -a /opengrok/dist/lib/opengrok.jar -- \
    -s /var/opengrok/src -d /var/opengrok/data -H -P -S -G \
    -W /var/opengrok/etc/configuration.xml` -U http://localhost:8080
```

The above will use `/var/opengrok/src` as source root, `/var/opengrok/data` as data root. The configuration will be written to `/var/opengrok/etc/configuration.xml` and sent to the web application (via the URL passed to the `-U` option) at the end of the indexing.

Run the command with `-h` to get more information about the options, i.e.:

```
indexer.py -a /opengrok/dist/lib/opengrok.jar -- -h
```

It is assumed that any SCM commands are reachable in one of the components
of the PATH environment variable (e.g. the `git` command for Git repositories).
Likewise, this should be maintained in the environment of the user which runs
the web server instance.

You should now be able to point your browser to http://YOUR_WEBAPP_SERVER:WEBAPPSRV_PORT/source to work with your fresh installation.

In some setups, it might be desirable to run the indexing (and especially mirroring) of each project in parallel in order to speed up the overall progress. See https://github.com/oracle/opengrok/wiki/Per-project-management on how this can be done.

# Optional info

## Custom ctags configuration

To make ctags recognize additional symbols/definitions/etc. it is possible to
specify configuration file with extra configuration options for ctags.

This can be done by using the `-o` option for `opengrok.jar`.

Sample configuration file for Solaris code base:

```
--regex-asm=/^[ \t]*(ENTRY_NP|ENTRY|RTENTRY)+\(([a-zA-Z0-9_]+)\)/\2/f,function/
--regex-asm=/^[ \t]*ENTRY2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\1/f,function/
--regex-asm=/^[ \t]*ENTRY2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\2/f,function/
--regex-asm=/^[ \t]*ENTRY_NP2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\1/f,function/
--regex-asm=/^[ \t]*ENTRY_NP2\(([a-zA-Z0-9_]+),[ ]*([a-zA-Z0-9_]+)\)/\2/f,function/
```

## Introduce own mapping for an extension to analyzer

OpenGrok script doesn't support this out of box, so you'd need to add it there.
Usually to `StdInvocation()` function after line `-jar ${OPENGROK_JAR}`.
It would look like this:

```
-A cs:org.opensolaris.opengrok.analysis.PlainAnalyzer
```

(this will map extension `.cs` to `PlainAnalyzer`)
You should even be able to override OpenGroks analyzers using this option.

## Optional need to change web application properties or name

You might need to modify the web application if you don't store the configuration file in the default location (<code>/var/opengrok/etc/configuration.xml</code>). This can be conveniently done using the `deploy.py` script by supplying the path to the configuration.

If you need to change name of the web application from `source` to something else, just deploy `source.war` as `new_name.war`.

### Deploy the modified .war file in glassfish/Sun Java App Server

* '''Option 1:''' Use browser and log into <code>glassfish web administration interface</code>

: Common Tasks / Applications / Web Applications , button '''Deploy''' and point it to your source.war webarchive

* '''Option 2:''' Copy the source.war file to `//GLASSFISH///domains///YOURDOMAIN///autodeploy` directory, glassfish will try to deploy it "automagically".
* '''Option 3:''' Use CLI from `//GLASSFISH//` directory:

 `./bin/asadmin deploy /path/to/source.war`

### Deploy the modified .war file in tomcat:

* just copy the source.war file to `//TOMCAT_INSTALL///webapps` directory.

## Optional setup of security manager for Tomcat

On some Linux distributions you need to setup permissions for source root and data root. Please check your Tomcat documentation on this, or simply disable (your risk!) <u>security manager</u> for Tomcat (e.g. in debian/ubuntu : in file <code>/etc/default/tomcat5.5</code> set <code>TOMCAT5_SECURITY=no</code>).
A sample approach is to <u>edit</u> <code>/etc/tomcat5.5/04webapps.policy</code> (or <code>/var/apache/tomcat/conf/catalina.policy</code>) and set this (it will give OpenGrok all permissions) for your OpenGrok webapp instance:

```
grant codeBase "file:${catalina.home}/webapps/source/-" {     
permission java.security.AllPermission;};
grant codeBase "file:${catalina.home}/webapps/source/WEB-INF/lib/-" {     
permission java.security.AllPermission;};
```

Alternatively you can be more restrictive (haven't tested below with a complex setup (e.g. some versioning system which needs local access as CVS), if it will not work, please report through [[Discussions]].

```
grant codeBase "file:${catalina.home}/webapps/source/-" {  
permission java.util.PropertyPermission "subversion.native.library", "read";  
permission java.lang.RuntimePermission "loadLibrary.svnjavahl-1?;  
permission java.lang.RuntimePermission "loadLibrary.libsvnjavahl-1?;  
permission java.lang.RuntimePermission "loadLibrary.svnjavahl";  
permission java.util.PropertyPermission "disableLuceneLocks", "read";  
permission java.util.PropertyPermission "catalina.home", "read";  
permission java.util.PropertyPermission "java.io.tmpdir", "read";  
permission java.util.PropertyPermission "org.apache.lucene.lockdir", "read";  
permission java.util.PropertyPermission "org.apache.lucene.writeLockTimeout", "read";  
permission java.util.PropertyPermission "org.apache.lucene.commitLockTimeout", "read";  
permission java.util.PropertyPermission "org.apache.lucene.mergeFactor", "read";  
permission java.util.PropertyPermission "org.apache.lucene.minMergeDocs", "read";  
permission java.util.PropertyPermission "org.apache.lucene.*", "read";  
permission java.io.FilePermission "/var/lib/tomcat5/temp", "read";  
permission java.io.FilePermission "/var/lib/tomcat5/temp/*", "write";  
permission java.io.FilePermission "/var/lib/tomcat5/temp/*", "delete";};
grant codeBase "file:${catalina.home}/webapps/source/WEB-INF/lib/-" {  
permission java.util.PropertyPermission "subversion.native.library", "read";  
permission java.lang.RuntimePermission "loadLibrary.svnjavahl-1?;  
permission java.util.PropertyPermission "disableLuceneLocks", "read";  
permission java.util.PropertyPermission "catalina.home", "read";  
permission java.util.PropertyPermission "java.io.tmpdir", "read";};
grant codeBase "file:${catalina.home}/webapps/source/WEB-INF/classes/-" {  
permission java.util.PropertyPermission "subversion.native.library", "read";  
permission java.lang.RuntimePermission "loadLibrary.svnjavahl-1?;  
permission java.util.PropertyPermission "disableLuceneLocks", "read";  
permission java.util.PropertyPermission "catalina.home", "read";  
permission java.util.PropertyPermission "java.io.tmpdir", "read";};
```