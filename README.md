
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

#### 5.4.2 Configure and Deploy source.war Webapp

To configure the webapp `source.war`, look into the parameters defined in
`web.xml` of `source.war` file and change them (see note1) appropriately.

* **HEADER** – the fragment of HTML that will be used to display title or
              logo of your project
* **SRC_ROOT** – absolute path name of the root directory of your source tree
* **DATA_ROOT** – absolute path of the directory where OpenGrok data
                 files are stored

  * File `header_include` can be created under `DATA_ROOT`.
    The contents of this file will be appended to the header of each
    web page after the OpenGrok logo element.
  * File `footer_include` can be created under `DATA_ROOT`.
    The contents of this file will be appended to the footer of each
    web page after the information about last index update.
  * The file `body_include` can be created under `DATA_ROOT`.
    The contents of this file will be inserted above the footer of the web
    application's "Home" page.
  * The file `error_forbidden_include` can be created under `DATA_ROOT`.
    The contents of this file will be displayed as the error page when
    the user is forbidden to see a particular project with `HTTP 403` code.


#### 5.4.3 Path Descriptions (optional)

OpenGrok can use path descriptions in various places (e.g. while showing
directory listings or search results). Example descriptions are in `paths.tsv`
file (delivered as `/usr/opengrok/doc/paths.tsv` by OpenGrok package on Solaris).

The `paths.tsv` file is read by OpenGrok indexing script from the configuration
directory (the same where `configuration.xml` is located) which will create file
`dtags.eftar` in the index subdirectory under `DATA_ROOT` directory which will
then be used by the webapp to display the descriptions.

The file contains descriptions for directories one per line. Path to the
directory and its description are separated by tab. The path to the directory
is absolute path under the `SRC_ROOT` directory.

For example, if the `SRC_ROOT` directory contains the following directories:

* foo
* bar
* bar/blah
* random
* random/code

then the `paths.tsv` file contents can look like this:

```
/foo  source code for foo
/bar  source code for bar
/bar/blah source code for blah
```

Note that only some paths can have a description.

#### 5.4.4 Changing webapp parameters (optional)

`web.xml` is the deployment descriptor for the web application. It is in a Jar
file named `source.war`, you can change it as follows:

* **Option 1**:  
  Unzip the file to `TOMCAT/webapps/source/` directory and
     change the `source/WEB-INF/web.xml` and other static html files like
     `index.html` to customize to your project.

* **Option 2**:  
  Extract the `web.xml` file from `source.war` file

  ```bash
  unzip source.war WEB-INF/web.xml
  ```

  edit `web.xml` and re-package the jar file.

  ```bash
  zip -u source.war WEB-INF/web.xml
  ```

  Then copy the war files to `TOMCAT/webapps` directory.

* **Option 3**:  
  Edit the Context container element for the webapp

  Copy `source.war` to `TOMCAT/webapps`

  When invoking OpenGrok to build the index, use `-w <webapp>` to set the
     context. If you change this(or set using `OPENGROK_WEBAPP_CONTEXT`) later,
     FULL clean reindex is needed.

  After the index is built, there's a couple different ways to set the
  Context for the servlet container:
  
  * Add the Context inside a Host element in `TOMCAT/conf/server.xml`

      ```xml
      <Context path="/<webapp>" docBase="source.war">
          <Parameter name="DATA_ROOT" value="/path/to/data/root" override="false" />
          <Parameter name="SRC_ROOT" value="/path/to/src/root" override="false" />
          <Parameter name="HEADER" value='...' override="false" />
      </Context>
      ```
   * Create a Context file for the webapp

     This file will be named `<webapp>.xml`.

     For Tomcat, the file will be located at:
     `TOMCAT/conf/<engine_name>/<hostname>`, where `<engine_name>`
     is the Engine that is processing requests and `<hostname>` is a Host
     associated with that Engine.  By default, this path is
     `TOMCAT/conf/Catalina/localhost` or `TOMCAT/conf/Standalone/localhost`.

     This file will contain something like the Context described above.

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

You might need to modify the web application if you don't store the
configuration file in the default location
(`/var/opengrok/etc/configuration.xml`).

To configure the webapp `source.war`, look into the parameters defined in
`WEB-INF/web.xml` of `source.war` (use jar or zip/unzip or your preferred zip
tool to get into it - e.g. extract the `web.xml` file from `source.war` (`unzip
source.war WEB-INF/web.xml`) file, edit `web.xml` and re-package the jar file
(`zip -u source.war WEB-INF/web.xml`) ) file and change those `web.xml`
parameters appropriately. These sample parameters need modifying(there are
more options, refer to manual or read param comments).

* **CONFIGURATION** – the absolute path to XML file containing project configuration 
  (e.g. `/var/opengrok/etc/configuration.xml`)
* **ConfigAddress** – port for remote updates to configuration, optional, but advised(since there is no authentication)
  to be set to `localhost:<some_port>` (e.g. `localhost:2424`), if you choose `some_port` below 1024 you have to have 
  root privileges

If you need to change name of the web application from source to something
else you need to use special option `-w <new_name>` for indexer to create
proper xrefs, besides changing the `.war` file name. Be sure that when this
changed you reindex cleanly from scratch. Examples below show just
deploying `source.war`, but you can use it to deploy your `new_name.war` too.

Deploy the modified `.war` file in glassfish/Sun Java App Server:

* **Option 1**:  
  Use browser and log into glassfish web administration interface: Common Tasks / Applications / Web Applications 
  , button Deploy and point it to your `source.war` webarchive

* **Option 2**:  
  Copy the `source.war` file to
    `GLASSFISH/domains/YOURDOMAIN/autodeploy` directory, glassfish will try
    to deploy it "auto magically".

* **Option 3**:  
  Use cli from GLASSFISH directory:

  ```bash
  ./bin/asadmin deploy /path/to/source.war
  ```
  Deploy the modified `.war` file in tomcat:
    just copy the `source.war` file to `TOMCAT_INSTALL/webapps` directory.


## 8. Information for developers

### 8.0 Building

Just run `ant` from command line in the top-level directory or use build
process driven by graphical developer environment such as Netbeans.

Note: in case you are behind http proxy, use `ANT_OPTS` to download jflex, lucene.
E.g. 
```bash
ANT_OPTS="-Dhttp.proxyHost=?.? -Dhttp.proxyPort=80" ant
```

#### 8.0.1 Package build

Run `ant package` to create package (specific for the operating system this is
being executed on) under the `dist/` directory.

### 8.1 Unit testing

**Note**: For full coverage report, a proper junit test environment is required.
That would mean:

  * You have to use Ant 1.9 and above
  * At least `junit-4.12.jar` and its dependencies have to be in ant's
    classpath (e.g. in `./lib`). The test task will download them automatically.
  * Your `PATH` must contain directory with exuberant ctags binary
    * **Note**: make sure that the directory which contains exuberant ctags binary
      is prepended before the directory with plain ctags program.
  * Your `PATH` variable must contain directories which contain binaries of
    appropriate SCM software which means commands hg, sccs, cvs, git, bzr, svn
    (svnadmin too). They must be available for the full report.

The tests are then run as follows:

```bash
ant -lib ./lib test
```

To check if the test completed without error look for `AssertionFailedError`
occurences in the `TESTS-TestSuites.xml` file produced by the test run.

### 8.2 Using Findbugs

If you want to run Findbugs (<http://findbugs.sourceforge.net/>) on OpenGrok,
you have to download Findbugs to your machine, and install it where you have
checked out your OpenGrok source code, under the `lib/findbugs` directory,
like this:

```bash
cd ~/.ant/lib
wget http://..../findbugs-x.y.z.tar.gz
gtar -xf findbugs-x.y.z.tar.gz
mv findbugs-x.y.z findbugs
```

You can now run ant with the findbugs target:

```
$ ant findbugs
...
findbugs:
 [findbugs] Executing findbugs from ant task
 [findbugs] Running FindBugs...
 [findbugs] Warnings generated: nnn
 [findbugs] Output saved to findbugs/findbugs.html
```

Now, open `findbugs/findbugs.html` in a web-browser, and start fixing bugs!

If you want to install findbugs some other place than `~/.ant/lib`, you can
untar the `.tar.gz` file to a directory, and use the `findbugs.home` property to
tell ant where to find findbugs, like this (if you have installed fundbugs
under the lib directory):

```bash
ant findbugs -Dfindbugs.home=lib/findbug
```

There is also a `findbugs-xml` ant target that can be used to generate XML files
that can later be parsed, e.g. by Jenkins.

### 8.3 Using Jacoco

If you want to check test coverage on OpenGrok, download jacoco from
<http://www.eclemma.org/jacoco/>. Place `jacocoagent.jar` and `jacocoant.jar` in the
`opengrok/lib`, `~/.ant/lib` or into classpath (`-lib` option of ant).

Now you can instrument your classes and test them run:

```bash
ant -Djacoco=true -Djacoco.home=/<path_to>/jacoco jacoco-code-coverage
```

Now you should get output data in `jacoco.exec`

Look at `jacoco/index.html` to see how complete your tests are.

### 8.4 Using Checkstyle

To check that your code follows the standard coding conventions,
you can use checkstyle from <http://checkstyle.sourceforge.net/>

First you must download checkstyle from <http://checkstyle.sourceforge.net/>.
You need Version 6.8 (or newer). Extract the package you have
downloaded, and create a symbolic link to it from `~/.ant/lib/checkstyle`,
e.g. like this:

```bash
cd ~/.ant/lib
unzip ~/Desktop/checkstyle-7.6.zip
ln -s checkstyle-7.6 checkstyle
```

You also have to create symbolic links to the jar files:

```bash
cd checkstyle
ln -s checkstyle-7.6-all.jar checkstyle-all.jar
```

To run checkstyle on the source code, just run ant checkstyle:

```bash
ant checkstyle
```

Output from the command will be stored in the checkstyle directory.

If you want to install checkstyle some other place than `~/.ant/lib`, you can
untar the `.tar.gz` file to a directory, and use the `checkstyle.home` property
to tell ant where to find checkstyle, like this (if you have installed
checkstyle under the lib directory):

```bash
ant checkstyle -Dcheckstyle.home=lib/checkstyle
```

### 8.5 Using PMD and CPD

To check the quality of the OpenGrok code you can also use PMD
from <https://pmd.github.io/>.

How to install:

```bash
cd ~/.ant/lib
unzip ~/Desktop/pmd-bin-5.5.4.zip
ln -s pmd-5.5.4/ pmd
```

To run PMD on the source code, just run ant pmd:

```bash
ant -Dpmd.home=~/.ant/lib/pmd pmd
```

Output from the command will be stored in the pmd subdirectory:

```
$ ls pmd
pmd_report.html  pmd_report.xml
```

If you want to install PMD some other place than `~/.ant/lib`, you can
unzip the `.zip` file to a directory, and use the `pmd.home` property
to tell ant where to find PMD, like this (if you have installed
PMD under the `./ext_lib directory`):

```bash
ant pmd -Dpmd.home=ext_lib/pmd
```

To run CPD, just use the same as above, but use targets:

```bash
ant -Dpmd.home=ext_lib/pmd cpd cpd-xml
```

Which will result in:

```
$ ls pmd
cpd_report.xml cpd_report.txt
```

### 8.6 Using JDepend

To see dependencies in the source code, you can use JDepend from
<https://github.com/clarkware/jdepend>.

How to install:

```bash
cd ~/.ant/lib
unzip ~/Desktop/jdepend-2.9.1.zip
ln -s jdepend-2.9.1/ jdepend
cd jdepend/lib
ln -s jdepend-2.9.1.jar jdepend.jar
```

How to analyze:

```bash
ant jdepend
```

Output is stored in the jdepend directory:

```
$ ls jdepend/
report.txt  report.xml
```

### 8.7 Using SonarQube

Use a sonar runner with included `sonar-project.properties` properties,
e.g. using bash:

```bash
cd <checkout_dir> # it has to contain sonar-project.properties!
export SONAR_RUNNER_OPTS="-Xmx768m -XX:MaxPermSize=256m"
export SERVERIP=10.163.26.78
~//Projects/sonar-runner-2.3/bin/sonar-runner \
    -Dsonar.host.url=http://${SERVERIP}:9000 \
    -Dsonar.jdbc.url=jdbc:h2:tcp://${SERVERIP}:9092/sonar
```

### 8.8 Using Travis CI

Travis depends on updated and working maven build.
Please see `.travis.yml`, if your branch has this file,
you should be able to connect your Github to Travis CI.
OpenGroks Travis is here: <https://travis-ci.org/OpenGrok/OpenGrok>

### 8.9 Maven

The build can now be done through Maven (<https://maven.apache.org/>) which takes care of the dependency management
and setup (calls Ant for certain actions).

#### 8.9.1 Unit Testing

You can test the code at the moment by running `./mvnw test` which will execute *all* tests.
Conditionally, if you don't have every type of repository installed, you can set it to unit-test only those which are
found to be working on your system.

```bash
./mvnw test -Djunit-force-all=false
```

You can also force a specific repository test from running through the following system property

```bash
./mvnw test -Djunit-force-all=false -Djunit-force-git=true
```

## 9. Tuning OpenGrok for large code bases

### 9.1 Almost atomic index flip using ZFS

While indexing big source repos you might consider using ZFS filesystem to give
you advantage of datasets which can be flipped over or cloned when needed.
If the machine is strong enough it will also give you an option to
incrementally index in parallel to having the current sources and index in sync.
(So Tomcat sees certain zfs datasets, then you just stop it, flip datasets to
the ones that were updated by SCM/index and start tomcat again - outage is
minimal, sources+indexes are **always** in sync, users see the truth)

### 9.2 JVM tuning

OpenGrok script by default uses 2GB of heap and 16MB per thread for flush size of
lucene docs indexing(when to flush to disk).
It also uses default 32bit JRE.
This **might not** be enough. You might need to consider this:
Lucene 4.x sets indexer defaults:

```
DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB = 1945;
DEFAULT_MAX_THREAD_STATES = 8;
DEFAULT_RAM_BUFFER_SIZE_MB = 16.0;
```

* which might grow as big as 16GB (though `DEFAULT_RAM_BUFFER_SIZE_MB` shouldn't
 really allow it, but keep it around 1-2GB)

* the lucenes `RAM_BUFFER_SIZE_MB` can be tuned now using the parameter `-m`, so
running a 8GB 64 bit server JDK indexer with tuned docs flushing(on Solaris 11):

  ```
  # export JAVA=/usr/java/bin/`isainfo -k`/java
  (or use /usr/java/bin/amd64/java )
  # export JAVA_OPTS="-Xmx8192m -server"
  # OPENGROK_FLUSH_RAM_BUFFER_SIZE="-m 256" ./OpenGrok index /source
  ```

Tomcat by default also supports only small deployments. For bigger ones you
**might** need to increase its heap which might necessitate the switch to 64-bit
Java. It will most probably be the same for other containers as well.
For tomcat you can easily get this done by creating `conf/setenv.sh`:

```bash
# cat conf/setenv.sh
# 64-bit Java
JAVA_OPTS="$JAVA_OPTS -d64 -server"

# OpenGrok memory boost to cover all-project searches
# (7 MB * 247 projects + 300 MB for cache should be enough)
# 64-bit Java allows for more so let's use 8GB to be on the safe side.
# We might need to allow more for concurrent all-project searches.
JAVA_OPTS="$JAVA_OPTS -Xmx8g"

export JAVA_OPTS
```

### 9.3 Tomcat/Apache tuning

For tomcat you might also hit a limit for http header size (we use it to send
the project list when requesting search results):

* increase(add) in `conf/server.xml` 
 
  ```xml 
  maxHttpHeaderSize
  connectionTimeout="20000"
  maxHttpHeaderSize="65536"
  redirectPort="8443" />
  ```

  Refer to docs of other containers for more info on how to achieve the same.

The same tuning to Apache can be done with the `LimitRequestLine` directive:

```
LimitRequestLine 65536
LimitRequestFieldSize 65536
```

### 9.4 Open File and processes hard and soft limits

The initial index creation process is resource intensive and often the error
`java.io.IOException: error=24, Too many open files` appears in the logs. To
avoid this increase the `ulimit` value to a higher number.

It is noted that the hard and soft limit for open files of 10240 works for mid
sized repositories and so the recommendation is to start with 10240.

If you get a similar error, but for threads:
`java.lang.OutOfMemoryError: unable to create new native thread `
it might be due to strict security limits and you need to increase the limits.

### 9.5 Multi-project search speed tip

If multi-project search is performed frequently, it might be good to warm
up file system cache after each reindex. This can be done e.g. with
<https://github.com/hoytech/vmtouch>


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
