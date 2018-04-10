
Copyright (c) 2006, 2017 Oracle and/or its affiliates. All rights reserved.


# OpenGrok - a wicked fast source browser [![Build Status](https://travis-ci.org/oracle/opengrok.svg?branch=master)](https://travis-ci.org/oracle/opengrok)

1.  [Introduction](#1-introduction)
2.  [Requirements](#2-requirements)
3.  [Usage](#3-usage)
4.  [OpenGrok install](#4-opengrok-install)
5.  [OpenGrok setup](#5-opengrok-setup)
6.  [Optional Command Line Interface Usage](#6-optional-command-line-interface-usage)
7.  [Change web application properties or name](#7-change-web-application-properties-or-name)
8.  [Information for developers](#8-information-for-developers)
9.  [Tuning OpenGrok for large code bases](#9-tuning-opengrok-for-large-code-bases)
10. [Authors](#10-authors)
11. [Contact us](#11-contact-us)

## 1. Introduction

OpenGrok is a fast and usable source code search and cross reference
engine, written in Java. It helps you search, cross-reference and navigate
your source tree. It can understand various program file formats and
version control histories of many source code management systems.

Official page of the project is on:
<http://opengrok.github.com/OpenGrok/>

## 2. Requirements
* Latest Java (at least 1.8)
  <http://www.oracle.com/technetwork/java/>
* A servlet container like Tomcat (8.x or later) supporting Servlet 2.5 and JSP 2.1
  <http://tomcat.apache.org/>
* Exuberant Ctags or Universal Ctags
  <http://ctags.sourceforge.net/>
  <https://ctags.io/>
* Source Code Management installation depending on type of repositories indexed
* If you want to build OpenGrok:
  * Ant (1.9.4 and later)
    <http://ant.apache.org/>
  * JFlex
    <http://www.jflex.de/>
  * Netbeans (optional, at least 8.2, will need Ant 1.9.4)
    <http://netbeans.org/>

## 3. Usage

OpenGrok usually runs in servlet container (e.g. Tomcat).

`SRC_ROOT` environment variable refers to the directory containing your source
tree. OpenGrok analyzes the source tree and builds a search index along with
cross-referenced hypertext versions of the source files. These generated
data files will be stored in directory referred to with environment variable
called `DATA_ROOT`.

### 3.1 Projects

OpenGrok has a concept of **Projects** - one project is one directory underneath
`SRC_ROOT` directory which usually contains a checkout of a project sources.
 (this can be branch, version, ...)

Projects effectively replace the need to have more web applications, each with
opengrok `.war` file. Instead it leaves you with one indexer and one web
application serving multiple source code repositories - projects.
Then you have a simple update script and simple index refresher script in
place, which simplifies management of more repositories.

A nice concept is to have a naming convention for directories underneath
`SRC_ROOT`, thereby creating a good overview of projects (e.g.
name-version-branch).

For example, the `SRC_ROOT` directory can contain the following directories:

* openssl-head
* openssl-0.9.8-stable
* openssl-1.0.0-stable

Each of these directories was created with `cvs checkout` command (with
appropriate arguments to get given branch) and will be treated by OpenGrok
as a project.

### 3.2 Messages

Deployed OpenGrok can receive couple of messages through the active socket which
usually listens for the main configuration file. These are used in the web
application and displayed to the users. One can easily notify users about some
important events, for example that the reindex is being in progress and that
the searched information can be inconsistent.

The OpenGrok comes with a tool which allows you to send these messages without
any problem. It is called **Messages** and it is located under the tools directory.
See the file for usage and more information.

#### 3.2.1 Tags

Any message can use tags which makes it more specific for the application.
Messages which tag match some OpenGrok project are considered project specific
and the information contained in them are displayed only for the specific projects.

There is a key tag `main` which is exclusive for displaying
messages on the OpenGrok landing page - like a common information.

#### 3.2.2 Types

Currently supported message types:

1. **NormalMessage (normal)** – this message is designed to display some information in the web application.
    Use tags to target a specific project.

2. **AbortMessage (abort)** – this message can delete some already published information in
    the web application.
    Use tags to restrict the deletion only to specific projects.

3. **StatsMessage (stats)** – this message is designed to retrieve some information from the web application.

    The purpose of the message is specified in the text field as one of:

    * **reload** – the application reloads the statistics file
                and returns the loaded statistics
    * **clean**  – the application cleans its current statistics
                and returns the empty statistics
    * **get** – the application returns current statistics

4. **ConfigMessage (config)** – this message performs some configuration communication with the webapp,
    depending on tag.
    * **setconf** – tag sends config to webapp and requires a file as an argument.
    * **getconf** – tag retrieves the configuration from the webapp.
    * **set** – tag sets particular configuration option in the webapp.
    * **auth** – tag requires "reload" text and
                 reloads all authorization plugins.

5. **RefreshMesssage (refresh)** – sent at the end of partial reindex to trigger refresh of `SearcherManagers`.

6. **ProjectMessage** – used for adding/deleting projects and partial (per-project) reindex.

  * **add** – adds project(s) and its repositories to the configuration.
    If the project already exists, refresh list of its repositories.
  * **delete** – removes project(s) and its repositores from the configuration.
    Also deletes its data under data root (but not the source code).
  * **indexed** – mark the project(s) as indexed so it becomes visible in the UI
  * **get-repos** – get list of repositories in the form of relative paths to source root for given project(s)
  * **get-repos-type** – get repository type(s) for given project(s)

6. **RepositoryMessage** – used for getting repository info.

  * **get-repo-type** – get repository type

## 4. OpenGrok install

### 4.1 Installing on Solaris from *.p5p file

#### 4.1.0 Install

The file `<package_name>.p5p` you can easily use as a new publisher for the `pkg` command.

```
pkg install --no-refresh -g /path/to/file <package_name>.p5p opengrok
```

#### 4.1.1 Update

You can also update OpenGrok software with the `*.p5p` file by running a command

```
pkg update --no-refresh -g /path/to/file/<package_name>.p5p 'pkg://opengrok/*'
```

## 5. OpenGrok setup

To setup OpenGrok it is needed to prepare the source code, let OpenGrok index
it and start the web application.

### 5.1 Setting up the sources

Source base should be available locally for OpenGrok to work efficiently.
No changes are required to your source tree. If the code is under source
control management (SCM) OpenGrok requires the checked out source tree under
`SRC_ROOT`.

By itself OpenGrok does not perform the setup of the source code repositories
or synchronization of the source code with its origin. This needs to be done by
the user or by using automatic scripts.

It is possible for SCM systems which are not distributed (Subversion, CVS)
to use a remote repository but this is not recommended due to the performance
penalty. Special option when running the OpenGrok indexer is needed to enable
remote repository support (`-r on`).

In order for history indexing to work for any SCM system it is necessary
to have environment for given SCM systems installed and in a path accessible
by OpenGrok.

Note that OpenGrok ignores symbolic links.

If you want to skip indexing the history of a particular directory
(and all of it's subdirectories), you can touch `.opengrok_skip_history` file
at the root of that directory.
If you want to disable history generation for all repositories globally, then
set `OPENGROK_GENERATE_HISTORY` environment variable to `off` during indexing.

### 5.2 Using Opengrok shell wrapper script to create indexes

For \*nix systems there is a shell script called `OpenGrok` which simplifies most
of the tasks. It has been tested on Solaris and Linux distributions.

#### 5.2.1 Deploy the web application

First please change to opengrok directory where the OpenGrok shell script is
stored (can vary on your system).

Note that now you might need to change to user which owns the target
directories for data, e.g. on Solaris you'd do:

```bash
pfexec su - webservd
cd /usr/opengrok/bin
```

and run

```bash
./OpenGrok deploy
```

This command will do some sanity checks and will deploy the `source.war` in
its directory to one of detected web application containers.
Please follow the error message it provides.

If it fails to discover your container, please refer to optional steps on
changing web application properties below, which explains how to do this.

Note that OpenGrok script expects the directory `/var/opengrok` to be
available to user running opengrok with all permissions. In root user case
it will create all the directories needed, otherwise you have to manually
create the directory and grant all permissions to the user used.

#### 5.2.2 Populate DATA\_ROOT Directory

During this process the indexer will generate OpenGrok XML configuration file
`configuration.xml` and sends the updated configuration to your web app.

The indexing can take a lot of time. After this is done, indexer automatically
attempts to upload newly generated configuration to the web application.
Most probably you will not be able to use Opengrok before this is done for the
first time.

Please change to opengrok directory (can vary on your system)

```bash
cd /usr/opengrok/bin
```

and run, if your `SRC_ROOT` is prepared under `/var/opengrok/src`

```bash
./OpenGrok index
```

otherwise (if `SRC_ROOT` is in different directory) run:

```bash
./OpenGrok index <absolute_path_to_your_SRC_ROOT>
```

The above command attempts to upload the latest index status reflected into
`configuration.xml` to a running source web application.
Once above command finishes without errors
(e.g. `SEVERE: Failed to send configuration to localhost:2424`),
you should be able to enjoy your OpenGrok and search your sources using
latest indexes and setup.

It is assumed that any SCM commands are reachable in one of the components
of the PATH environment variable (e.g. `git` command for Git repositories).
Likewise, this should be maintained in the environment of the user which runs
the web server instance.

Congratulations, you should now be able to point your browser to
`http://<YOUR_WEBAPP_SERVER>:<WEBAPPSRV_PORT>/source` to work with your fresh
OpenGrok installation! :-)

At this time we'd like to point out some customization to OpenGrok script
for advanced users.
A common case would be, that you want the data in some other directory than
`/var/opengrok`. This can be easily achieved by using environment variable
`OPENGROK_INSTANCE_BASE`.

E.g. if OpenGrok data directory is `/tank/opengrok` and source root is
in `/tank/source` then to get more verbosity run the indexer as:

```bash
OPENGROK_VERBOSE=true OPENGROK_INSTANCE_BASE=/tank/opengrok
./OpenGrok index /tank/source
```

Since above will also change default location of config file, beforehand(or
restart your web container after creating this symlink) I suggest doing
below for our case of having OpenGrok instance in `/tank/opengrok`:

```bash
ln -s /tank/opengrok/etc/configuration.xml /var/opengrok/etc/configuration.xml
```

More customizations can be found inside the script, you just need to
have a look at it, eventually create a configuration out of it and use
`OPENGROK_CONFIGURATION` environment variable to point to it. Obviously such
setups can be used for nightly cron job updates of index or other automated
purposes.

#### 5.2.3 Partial reindex

There is inherent time window between after the source code is updated
(highlighted in step **5.1** above) and before indexer completes. During this
time window the index does not match the source code. To alleviate this
limitation, one can kick off update of all source repositories in
parallel and once all the mirroring completes perform complete reindex.
This does not really help in case when some of the source code
repositories are slow to sync, e.g. because the latency to their origin is
significant, because the overall mirroring process has to wait for all the
projects to finish syncing before running the indexer. To overcome this
limitation, the index of each project can be created just after the
mirroring of this project finishes.

Indexing of all projects (i.e. `OpenGrok index /var/opengrok/src`) in
order to discover new and remove deleted projects from the configuration can be
avoided. One can start with no index and no projects and then incrementally add
them and index them.

It basically works like this:

1. bootstrap initial configuration: OpenGrok bootstrap

  * this will create `/var/opengrok/etc/configuration.xml` with basic set of
    properties. If more is needed use:

     ```
     Messages -n config -t set "set propertyXY = valueFOO"
     ```

2. add a new project **foo**:

  ```
  Messages -t foo -n project add
  ```

  * the project **foo** is now visible in the configuration however is not yet
       searchable. Store the config in a file so that indexer can see the project
       and its repositories:

     ```
     Messages -n config -t getconf > /var/opengrok/etc/configuration.xml
     ```

3. index the project. It will become searchable after that.

  ```
  OPENGROK_READ_XML_CONFIGURATION=/var/opengrok/etc/configuration.xml
  OpenGrok indexpart /foo
  ```

4. make the project `indexed` status of the project persistent so that if
     webapp is redeployed the project will be still visible:

  ```
  Messages -n config -t getconf > /var/opengrok/etc/configuration.xml
  ```

If an *add* message is sent that matches existing project, the list of
repositories for that project will be refreshed. This is handy when repositories
are added/deleted.

##### 5.2.3.1 Logging when running partial reindex

When running the indexer the logs are being written to single file. Since
multiple indexers are being run in parallel in step 2, their logs have to
be separated. To do this, create `logging.properties` file for each project
using the `/var/opengrok/logging.properties` file as template. The only line
which can differ would be this:

```
java.util.logging.FileHandler.pattern = /var/opengrok/log/myproj/opengrok%g.%u.log
```

Note the path component `myproj` which separates the logs for given
project to this directory. The creation of the per-project directory and the
`logging.properties` file can be easily done in a script.

The command used in step 2 can thus look like this:

```bash
OPENGROK_LOGGER_CONFIG_PATH=/var/opengrok/myproj.logging
OPENGROK_READ_XML_CONFIGURATION=/var/opengrok/etc/configuration.xml
OpenGrok indexpart /myproj
```

The last argument is path relative to `SRC_ROOT`.

### 5.3 Using SMF service (Solaris) to maintain OpenGrok indexes

If you installed OpenGrok from the OSOLopengrok package, it will work out of
the box. Should you need to configure it (e.g. because of non-default `SRC_ROOT`
or `DATA_ROOT` paths) it is done via the `opengrok` property group of the
service like this:

```bash
svccfg -s opengrok setprop opengrok/srcdir="/absolute/path/to/your/sourcetree"
svccfg -s opengrok setprop opengrok/maxmemory="2048"
```

Then make the service start the indexing, at this point it would be nice if
the web application is already running.

Now enable the service:

```bash
svcadm enable -rs opengrok
```

Note that this will enable tomcat service as dependency.

When the service starts indexing for first time, it's already enabled and
depending on tomcat, so at this point the web application should be
already running.

Note that indexing is not done when the opengrok service is disabled.

To rebuild the index later (e.g. after source code changed) just run:

```bash
svcadm refresh opengrok
```

The service makes it possible to supply part of the configuration via the
`opengrok/readonly_config` service property which is set to
`/etc/opengrok/readonly_configuration.xml` by default.

Note: before removing the package please disable the service.
If you don't do it, it will not be removed automatically.
In such case please remove it manually.

### 5.4 Using command line interface to create indexes

There are 2 (or 3) steps needed for this task.

#### 5.4.1 Populate DATA_ROOT Directory

* **Option 1. OpenGrok**:
  There is a sample shell script `OpenGrok` that is suitable
for using in a cron job to run regularly. Modify the variables in the script
to point appropriate directories, or as the code suggests factor your local
configuration into a separate file and simplify future upgrades.

* **Option 2. opengrok.jar**:
  You can also directly use the Java application. If
  the sources are all located in a directory `SRC_ROOT` and the data and
  hypertext files generated by OpenGrok are to be stored in `DATA_ROOT`, run

  ```bash
  java -jar opengrok.jar -s $SRC_ROOT -d $DATA_ROOT
  ```

  See `opengrok.jar` manual below for more details.

#### 5.4.5 Custom ctags configuration

To make ctags recognize additional symbols/definitions/etc. it is possible to
specify configuration file with extra configuration options for ctags.

This can be done by setting `OPENGROK_CTAGS_OPTIONS_FILE` environment variable
when running the OpenGrok shell script (or directly with the `-o` option for
`opengrok.jar`). Default location for the configuration file in the OpenGrok
shell script is `etc/ctags.config` under the OpenGrok base directory (by default
the full path to the file will be `/var/opengrok/etc/ctags.config`).

Sample configuration file for Solaris code base is delivered in the `doc/`
directory.

### 5.6 Introduce own mapping for an extension to analyzer

OpenGrok script doesn't support this out of box, so you'd need to add it there.
Usually to `StdInvocation()` function after line `-jar ${OPENGROK_JAR}`.
It would look like this:

```
-A cs:org.opensolaris.opengrok.analysis.PlainAnalyzer
```

(this will map extension `.cs` to `PlainAnalyzer`)
You should even be able to override OpenGroks analyzers using this option.

### 5.7 Logging

Both indexer and web app emit extensive log messages.

OpenGrok is shipped with the `logging.properties` file that contains logging
configuration.

The `OpenGrok` shell script will automatically use this file
if found under the base directory. It can also be set using the
`OPENGROK_LOGGER_CONFIG_PATH` environment variable.

If not using the shell script, the path to the configuration file can be
set using the `-Djava.util.logging.config.file=/PATH/TO/MY/logging.properties`
java parameter.


## 6. Optional Command Line Interface Usage

You need to pass location of project file + the query to `Search` class, e.g.
for fulltext search for project with above generated `configuration.xml` you'd
do:

```bash
java -cp ./opengrok.jar org.opensolaris.opengrok.search.Search -R \
    /var/opengrok/etc/configuration.xml -f fulltext_search_string
```
 For quick help run:

```bash
java -cp ./opengrok.jar org.opensolaris.opengrok.search.Search
```

## 7. Change web application properties or name

See https://github.com/oracle/opengrok/wiki/Webapp-configuration


## 8. Information for developers

See https://github.com/oracle/opengrok/wiki/Developer-intro and https://github.com/oracle/opengrok/wiki/Developers

## 9. Tuning OpenGrok for large code bases

See https://github.com/oracle/opengrok/wiki/Tuning-for-large-code-bases

## 10. Authors

The project has been originally conceived in Sun Microsystems by Chandan B.N.

* Chandan B.N, (originally Sun Microsystems)
* Trond Norbye, norbye.org
* Knut Pape, eBriefkasten.de
* Martin Englund, (originally Sun Microsystems)
* Knut Anders Hatlen, Oracle.
* Lubos Kosco, Oracle. <http://blogs.oracle.com/taz/>
* Vladimir Kotal, Oracle. <http://blogs.oracle.com/vlad/>
* Krystof Tulinger

## 11. Contact us

Feel free to participate in discussion on the mailing lists:

  `opengrok-users@yahoogroups.com` (user topics)

  `opengrok-dev@yahoogroups.com` (developers' discussion)

To subscribe, send email to `<mailing_list_name>-subscribe@yahoogroups.com`
