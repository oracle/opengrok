OpenGrok can be installed and used under different use cases. Advanced usage depends on your knowledge of running java applications and command line options.
Note, that you need the create the index no matter what is your use case. Without indexes Opengrok will be simply useless.

# Requirements

You need the following:

- [JDK](http://www.oracle.com/technetwork/java/) 1.8 or higher
- OpenGrok '''binaries''' from https://github.com/OpenGrok/OpenGrok/releases (.tar.gz file with binaries, not the source code tarball !)
- https://github.com/universal-ctags for analysis (avoid Exuberant ctags, they are not maintained anymore)
- A servlet container like [GlassFish](https://glassfish.dev.java.net/) or [Tomcat](http://tomcat.apache.org) 8.0 or later also running with Java at least 1.8
- If history is needed, appropriate binaries (in some cases also cvs/svn repository) must be present on the system (e.g. [Subversion](http://subversion.tigris.org) or [Mercurial](http://www.selenic.com/mercurial/wiki/index.cgi) or SCCS or ... )
- 2GB of memory for the indexing process (bigger deployments will need more)
- a recent browser for clients - IE, Firefox, recent Chrome or Safari
- Optional tuning (see https://github.com/oracle/opengrok/wiki/Tuning-for-large-code-bases)
- GIT version 2.6 or higher for GIT repositories (see PR [#1314](https://github.com/oracle/opengrok/pull/1314) for more info)

After unpacking the binaries to your target directory, the index needs to be created and the web application deployed.

See https://github.com/OpenGrok/platform for OS specific integration.

# Creating the index

The data to be indexed should be stored in a directory called **source root**. Each subdirectory under this directory is called **project** (projects can be disabled but let's leave this detail aside for now) and usually contains checkout of a **repository** (or it's branch, version, ...) sources. Each project can have multiple repositories.

The concept of projects was introduced to effectively replace the need for more web applications with opengrok <code>.war</code> and leave you with one indexer and one web application serving more source code repositories - projects.

[[/images/setup-project.png]]

The index data will be created under directory called **data root**.

## <u>Step.0</u> - Setting up the sources

Source base should be available locally for OpenGrok to work efficiently. No changes are required to your source tree. If the code is under CVS or SVN, OpenGrok requires the '''checked out source''' tree under source root.

## <u>Step.1</u> - Install management tools (optional)

This step is optional, the python package contains wrappers for OpenGrok's indexer and other commands.
In the release tarball navigate to `tools` subdirectory and install the `opengrok-tools.tar.gz` as a python package. Then you can use [defined commands](https://github.com/oracle/opengrok/tree/master/opengrok-tools#content). You can of course run the plain java yourself, without these wrappers. The tools are mainly useful for [parallel repository synchronization and indexing](https://github.com/oracle/opengrok/wiki/Repository-synchronization) and also in case when managing multiple OpenGrok instances with diverse Java installations.

In shell, you can install the package simply by:
```bash
$ python3 -m pip install opengrok-tools.tar.gz
```
Of course, the Python package can be installed into Python virtual environment.

## <u>Step.2</u> - Deploy the web application

Install web application container of your choice (e.g. [Tomcat](http://tomcat.apache.org/), [Glassfish](https://glassfish.dev.java.net/)).

Copy the `.war` file to the location where the application container will detect it and deploy the web application. 

If you happen to be using the Python tools, you can use the `opengrok-deploy` script to perform the same. The script is also handy when the configuration file is stored in non-standard location (i.e. other than `/var/opengrok/etc/configuration.xml`)

See https://github.com/oracle/opengrok/wiki/Webapp-configuration for more configuration options.

## <u>Step.3</u> - Indexing

This step consists of these operations:
  - create index
  - let the indexer generate the configuration file
  - notify the web application that new index is available

The indexing can take a lot of time - for large code bases (meaning both amount of source code and history) it can take many hours. After this is done, indexer automatically attempts to upload newly generated configuration to the web application. Until this is done, the web application will display the old state.

The indexer can be run either using `opengrok.jar` directly:
```
java -Djava.util.logging.config.file=/var/opengrok/logging.properties \
    -jar /opengrok/dist/lib/opengrok.jar \
    -c /path/to/universal/ctags \
    -s /var/opengrok/src -d /var/opengrok/data -H -P -S -G \
    -W /var/opengrok/etc/configuration.xml -U http://localhost:8080
```
or using the `opengrok-indexer` wrapper like so:
```
opengrok-indexer -J=-Djava.util.logging.config.file=/var/opengrok/logging.properties \
    -a /opengrok/dist/lib/opengrok.jar -- \
    -c /path/to/universal/ctags \
    -s /var/opengrok/src -d /var/opengrok/data -H -P -S -G \
    -W /var/opengrok/etc/configuration.xml -U http://localhost:8080
```
Notice how the indexer arguments are the same. The `opengrok-indexer` will merely find the Java executable and run it.

The above will use `/var/opengrok/src` as source root, `/var/opengrok/data` as data root. The configuration will be written to `/var/opengrok/etc/configuration.xml` and sent to the web application (via the URL passed to the `-U` option) at the end of the indexing.

Run the command with `-h` to get more information about the options, i.e.:
```
java -jar /opengrok/dist/lib/opengrok.jar -h
```
or when using the Python scripts:
```
opengrok-indexer -a /opengrok/dist/lib/opengrok.jar -- -h
```

Optionally use `--detailed` together with `-h` to get extra detailed help, including examples.

It is assumed that any SCM commands are reachable in one of the components
of the PATH environment variable (e.g. the `git` command for Git repositories).
Likewise, this should be maintained in the environment of the user which runs
the web server instance.

You should now be able to point your browser to http://YOUR_WEBAPP_SERVER:WEBAPPSRV_PORT/source to work with your fresh installation.

In some setups, it might be desirable to run the indexing (and especially mirroring) of each project in parallel in order to speed up the overall progress. See https://github.com/oracle/opengrok/wiki/Per-project-management on how this can be done.

See https://github.com/oracle/opengrok/wiki/Indexer-configuration for more indexer configuration options.

## <u>Step.4</u> - setting up periodic reindex

The index needs to be kept consistent with the data being indexed. Also, the data needs to be kept in sync with their origin. Therefore, there has to be periodic process that syncs the data and runs reindex. On Unix this is normally done by setting up a `crontab` entry.

Ideally, the time window between the data being changed on disk and reindex done should be kept to minimum otherwise strange artifacts may appear when searching/browsing.

For syncing repository data see https://github.com/oracle/opengrok/wiki/Repository-synchronization