OpenGrok - a wicked fast source browser
---------------------------------------

OpenGrok is a fast and usable source code search and cross reference
engine, written in Java. It helps you search, cross-reference and navigate
your source tree. It can understand various program file formats and
version control histories like SCCS, RCS, CVS, Subversion and Mercurial.

OpenGrok is the tool used for the OpenSolaris Source Browser.

Requirements
------------
    * Latest Java http://java.sun.com/ (At least 1.6)
    * A servlet container like Tomcat (6.x or later)
      http://tomcat.apache.org/
      supporting Servlet 2.4 and JSP 2.0
    * Exuberant Ctags http://ctags.sourceforge.net/
    * Subversion 1.3.0 or later if SVN support is needed
      http://subversion.tigris.org/
    * Mercurial 0.9.3 or later if Mercurial support is needed
      http://www.selenic.com/mercurial/wiki/
    * JFlex Ant task (if you want to build OpenGrok)
      http://www.jflex.org/


Usage
-----
SRC_ROOT refers to the directory containing your source tree.
OpenGrok analyzes the source tree and builds a search index along with
cross-referenced hypertext versions of the source files. These generated
data files will be stored in DATA_ROOT directory. 
Project concept - one project is one directory underneath SRC_ROOT and
usually contains a checkout of a project(or it's branch, version, ...)
sources, it can have several attributes (in its XML description), note that
interface of projects is being stabilized so it can change. Projects
effectively replace need for more web applications with opengrok .war and
leave you with one indexer and one web application serving MORE source code
repositories - projects. A nice concept is to have directories underneath
SRC_ROOT with a naming convention, thereby creating a good overview of
projects (e.g. name-version-branch). Then you have a simple update script &
simple index refresher script in place, which simplifies management of more
repositories.

OpenGrok setup Step.0 - Setting up the Sources.
----------------------------------------------
Source base must be available locally for OpenGrok to work efficiently. No
changes are required to your source tree. If the code is under source control
management (SCM) OpenGrok requires the checked out source tree under SRC_ROOT.
It is possible for some SCM systems to use a remote repository (Subversion,
CVS), but this is not recommended due to the performance penalty. Special
option is needed to enable remote repository support(-r on).
Note that OpenGrok ignores symbolic links.

---------------------------------------------------
Using Opengrok wrapper script(Solaris and Linux) to create indexes.
---------------------------------------------------

Step.1 - Deploy the web application
=====================================

We provided you with OpenGrok wrapper script, which should aid in deploying
the web application.
Please change to opengrok directory (can vary on your system)
Note that now you might need to change to user which owns the target 
directories for data, e.g. on Solaris you'd do # pfexec su - webservd

$ cd /usr/opengrok/bin

and run 

$ ./OpenGrok deploy

This command will do some sanity checks and will deploy the source.war in
its directory to one of detected web application containers.
Please follow the error message it provides.
If it fails to discover your container, please refer to optional steps on
changing web application properties, which has manual steps on how to do
this.

Note that OpenGrok script expects the directory /var/opengrok to be
available to user running opengrok with all permissions. In root user case
it will create all the directories needed, otherwise you have to manually
create the directory and grant all permissions to the user used.


Step.2 - Populate DATA_ROOT Directory, let the indexer generate the project
XML config file, update configuration.xml to your web app
=====================================

Second step is to just run the indexing (can take a lot of time). After this
is done, indexer automatically attempts to upload newly generated
configuration to the web application. Most probably you will not be able to
use {Opengrok before this is done.

Please change to opengrok directory (can vary on your system)

$ cd /usr/opengrok/bin

and run, if your SRC_ROOT is prepared under /var/opengrok/src

$ ./OpenGrok index

otherwise (if SRC_ROOT is in different directory) run:

$ ./OpenGrok index <absolute_path_to_your_SRC_ROOT>

Above command should try to upload latest index status reflected into
configuration.xml to a running source web application.
Once above command finishes without errors(e.g. SEVERE: Failed to send
configuration to localhost:2424
), you should be able to enjoy your opengrok and search your sources using
latest indexes and setup.

Congratulations, you should now be able to point your browser to
http://<YOUR_WEBAPP_SERVER>:<WEBAPPSRV_PORT>/source to work with your fresh
opengrok installation! :-)


At this time we'd like to point out some customization to OpenGrok script
for advanced users.
A common case would be, that you want the data in some other directory than
/var/opengrok.
This can be easily achieved by using environment variable
OPENGROK_INSTANCE_BASE .
E.g. if my opengrok data directory is /tank/opengrok and my source root is
in /tank/source and I'd like to get more verbosity I'd run the indexer as:

$ OPENGROK_VERBOSE=true OPENGROK_INSTANCE_BASE=/tank/opengrok \
  ./OpenGrok index /tank/source 

Since above will also change default location of config file, beforehands(or
restart your web container after creating this symlink) I suggest doing
below for our case of having opengrok instance in /tank/opengrok :

$ ln -s /tank/opengrok/etc/configuration.xml \
 /var/opengrok/etc/configuration.xml 

A lot more customizations can be found inside the script, you just need to
have a look at it, eventually create a configuration out of it and use
OPENGROK_CONFIGURATION environment variable to point to it. Obviously such
setups can be used for nightly cron job updates of index or other automated
purposes.


---------------------------------------------------
Using smf service(OpenSolaris) to maintain opengrok indexes.
---------------------------------------------------

If you installed opengrok from a package, then configure the service like this:

# svccfg -s opengrok
 listprop opengrok 
 setprop opengrok/srcdir="/absolute/path/to/your/sourcetree" 
 setprop opengrok/maxmemory="2048" 
 end

then make the service start the indexing, at this point it would be nice if 
the web application is already running.

# svcadm enable -rs opengrok 
(which will enable tomcat6 as dependency)

When the service starts indexing for first time, it's already enabled and
depending on tomcat6, so at this point the web application should be 
already running.
The indexing is not done when opengrok service is disabled.

To rebuild the index later (e.g. after source code changed)just run:
# svcadm refresh opengrok

Note: before removing opengrok package please disable the service.
If you don't do it, it will not be removed automatically.
In such case please remove it manually.

---------------------------------------------------
Using command line interface(general pointers) to create indexes.
---------------------------------------------------

Step.1 - Populate DATA_ROOT Directory
=====================================
Option 1. OpenGrok: There is a sample shell script OpenGrok that is suitable
for using in a cron job to run regularly. Modify the variables in the script
to point appropriate directories, or as the code suggests factor your local
configuration into a separate file and simplify future upgrades.

Option 2. opengrok.jar: You can also directly use the Java application. If
the sources are all located in a directory SRC_ROOT and the data and
hypertext files generated by OpenGrok are to be stored in DATA_ROOT, run

     $ java -jar opengrok.jar -s SRC_ROOT -d DATA_ROOT

See opengrok.jar manual below for more details.

Step.2 - Configure and Deploy source.war Webapp
===============================================
To configure the webapp source.war, look into the parameters defined in
web.xml of source.war file and change them (see note1) appropriately.

    * HEADER: is the fragment of HTML that will be used to display title or
    logo of your project
    * SRC_ROOT: the absolute path name of the root directory of your source tree
    * DATA_ROOT: absolute path of the directory where OpenGrok data
    files are stored


Optional Step.3 - Path Descriptions
-----------------------------------
OpenGrok uses path descriptions in various places (For eg. while showing
directory listings or search results) Example descriptions are in paths.tsv
file. You can list descriptions for directories one per line tab separated
format path tab description. Refer to example 4 below.

Note 1 - Changing webapp parameters: web.xml is the deployment descriptor
for the web application. It is in a Jar file named source.war, you can
change the :

    * Option 1: Unzip the file to TOMCAT/webapps/source/ directory and
     change the source/WEB-INF/web.xml and other static html files like
     index.html to customize to your project. 
    
    * Option 2: Extract the web.xml file from source.war file

     $ unzip source.war WEB-INF/web.xml

     edit web.xml and re-package the jar file. 

     $ zip -u source.war WEB-INF/web.xml

     Then copy the war files to <i>TOMCAT</i>/webapps directory.

    * Option 3: Edit the Context container element for the webapp

     Copy source.war to TOMCAT/webapps

     When invoking OpenGrok to build the index, use -w <webapp> to set the 
     context.

     After the index is built, there's a couple different ways to set the
     Context for the servlet container:
     - Add the Context inside a Host element in TOMCAT/conf/server.xml

     <Context path="/<webapp>" docBase="source.war">
        <Parameter name="DATA_ROOT" value="/path/to/data/root" override="false" />
        <Parameter name="SRC_ROOT" value="/path/to/src/root" override="false" />
        <Parameter name="HEADER" value='...' override="false" />
     </Context>

     - Create a Context file for the webapp

     This file will be named `<webapp>.xml'.

     For Tomcat, the file will be located at:
     `TOMCAT/conf/<engine_name>/<hostname>', where <engine_name>
     is the Engine that is processing requests and <hostname> is a Host
     associated with that Engine.  By default, this path is
     'TOMCAT/conf/Catalina/localhost' or 'TOMCAT/conf/Standalone/localhost'.

     This file will contain something like the Context described above.

---------------------------------------------------
Using Java DB for history cache
(instead of gzipped xml files)
---------------------------------------------------

You need Java DB 10.5.3 or later
(OpenSolaris: # pkg install SUNWjavadb or SUNWj6dev ,
Debian/Ubuntu: # apt-get install sun-java6-javadb).
There are two modes, having Java DB embedded, or running a Java DB server.
Java DB server is default option, we will not describe how to set up embedded
option.

1) Start the server:
$ mkdir -p $DATA_ROOT/derby

OpenSolaris:
# svcadm enable javadb
OR
$ java -Dderby.system.home=$DATA_ROOT/derby -jar /opt/SUNWjavadb/lib/derbynet.jar start
OR
$ java -Dderby.system.home=$DATA_ROOT/derby -jar /usr/jdk/instances/jdk1.6.0/db/lib/derbynet.jar start

Debian:
$ java -Dderby.system.home=$DATA_ROOT/derby -jar /usr/lib/jvm/java-6-sun/db/lib/derbynet.jar start


2) You need to have the derbyclient.jar in lib directory of opengrok.jar and in source.war WEB-INF/lib
Copy it over from
OpenSolaris: /opt/SUNWjavadb/lib/derbyclient.jar OR /usr/jdk/instances/jdk1.6.0/db/lib/derbyclient.jar
Debian: /usr/lib/jvm/java-6-sun/db/lib/derbyclient.jar

3) Use these options with indexer when indexing/generating the configuration:
    -D -H

Also the Java DB server has to be running during indexing and for the web application.

Note: To use a bigger database buffer, which may improve performance of both
indexing and fetching of history, create a file named derby.properties in
$DATA_ROOT/derby and add this line to it:
derby.storage.pageCacheSize=25000

---------------------------------------------------
Optional CLI - Command Line Interface Usage
---------------------------------------------------

 You need to pass location of project file + the query to Search class, e.g.
for fulltext search for project with above generated configuration.xml you'd
do:

$ java -cp ./opengrok.jar org.opensolaris.opengrok.search.Search -R \
/var/opengrok/etc/configuration.xml -f fulltext_search_string

 For quick help run:

$ java -cp ./opengrok.jar org.opensolaris.opengrok.search.Search

---------------------------------------------------
Optional need to change web application properties or name
---------------------------------------------------

 You might need to modify the web application if you don't store the
configuration file in the default location
(/var/opengrok/etc/configuration.xml).

To configure the webapp source.war, look into the parameters defined in
WEB-INF/web.xml of source.war (use jar or zip/unzip or your preferred zip
tool to get into it - e.g. extract the web.xml file from source.war ($ unzip
source.war WEB-INF/web.xml) file, edit web.xml and re-package the jar file
(zip -u source.war WEB-INF/web.xml) ) file and change those web.xml
parameters appropriately. These sample parameters need modifying(there are
more options, refer to manual or read param comments).

    * CONFIGURATION - the absolute path to XML file containing project
    * configuration (e.g. /var/opengrok/etc/configuration.xml )
    * ConfigAddress - port for remote updates to configuration, optional,
    * but advised(since there is no authentication) to be set to
    * localhost:<some_port> (e.g. localhost:2424), if you choose some_port
    * below 1024 you have to have root privileges

If you need to change name of the web application from source to something
else you need to use special option -w <new_name> for indexer to create
proper xrefs, besides changing the .war file name. Examples below show just
deploying source.war, but you can use it to deploy your new_name.war too.

Deploy the modified .war file in glassfish/Sun Java App Server:
-----------------------------------

    * Option 1: Use browser and log into glassfish web administration
    * interface

    Common Tasks / Applications / Web Applications , button Deploy and point
it to your source.war webarchive

    * Option 2: Copy the source.war file to
    * GLASSFISH/domains/YOURDOMAIN/autodeploy directory, glassfish will try
    * to deploy it "auto magically".
    * Option 3: Use cli from GLASSFISH directory:

# ./bin/asadmin deploy /path/to/source.war

Deploy the modified .war file in tomcat:
-----------------------------------

    * just copy the source.war file to TOMCAT_INSTALL/webapps directory.

---------------------------------------------------
Optional opengrok indexer setup with agent and systray GUI control application
---------------------------------------------------

we provide an example opengrok-agent.properties file, which can be used when
starting special OpenGrok Agent, where you can connect with a systray GUI
application.

To start the indexer with configuration run:
$ java -cp ./opengrok.jar org.opensolaris.opengrok.management.OGAgent \
--config opengrok-agent.properties

then from the remote machine one can run:
$ java -cp ./opengrok.jar \
org.opensolaris.opengrok.management.client.OpenGrokTrayApp

assuming configuration permits remote connections(so not listening on
localhost, but rather on a physical network interface)

This agent is work in progress, so it might not fully work.

---------------------------------------------------
Using Findbugs
---------------------------------------------------
If you want to run Findbugs (http://findbugs.sourceforge.net/) on OpenGrok,
you have to download Findbugs to your machine, and install it where you have 
checked out your OpenGrok source code, under the lib/findbugs directory,
like this:

   cd ~/.ant/lib
   wget http://..../findbugs-x.y.z.tar.gz
   gtar -xf findbugs-x.y.z.tar.gz
   mv findbugs-x.y.z findbugs

You can now run ant with the findbugs target:

  ant findbugs
  ...
  findbugs:
   [findbugs] Executing findbugs from ant task
   [findbugs] Running FindBugs...
   [findbugs] Warnings generated: nnn
   [findbugs] Output saved to findbugs/findbugs.html

Now, open findbugs/findbugs.html in a web-browser, and start fixing bugs!

If you want to install findbugs some other place than ~/.ant/lib, you can untar the
.tar.gz file to a directory, and use the findbugs.home property to tell ant where to find
findbugs, like this (if you have installed fundbugs under the lib directory):

  ant findbugs -Dfindbugs.home=lib/findbug

There is also a findbugs-xml ant target that can be used to generate XML files that can
later be parsed, e.g. by Hudson.

---------------------------------------------------
Using Emma
---------------------------------------------------
If you want to check test coverage on OpenGrok, download Emma from
http://emma.sourceforge.net/. Place emma.jar and emma-ant.jar in the
opengrok/trunk/lib directory, or ~/.ant/lib.

Now you can instrument your classes, and create a jar file:

   ant emma-instrument

If you are using NetBeans, select File - "opengrok" Properties 
- libraries - Compile tab. Press the "Add JAR/Folder" and select
lib/emma.jar and lib/emma_ant.jar

If you are not using netbeans, you have to edit the file 
nbproject/project.properties, and add "lib/emma.jar" and 
"lib/emma_ant.jar" to the javac.classpath inside it.

Now you can put the classes into jars and generate distributable:

   ant dist

The classes inside opengrok.jar should now be instrumented.
If you use opengrok.jar for your own set of tests, you need 
emma.jar in the classpath.If you want to specify where to store 
the run time analysis, use these properties:

   emma.coverage.out.file=path/coverage.ec
   emma.coverage.out.merge=true

The coverage.ec file should be placed in the opengrok/trunk/coverage
directory for easy analyze.

If you want to test the coverage of the unit tests, you can
run the tests:

   ant test   (Or Alt+F6 in NetBeans)

Now you should get some output saying that Emma is placing runtime 
coverage data into coverage.ec.

To generate reports, run ant again:

   ant emma-report

Look at coverage/coverage.txt, coverage/coverage.xml and 
coverage/coverage.html to see how complete your tests are.

Note: For full coverage report your system has to provide proper junit test 
environment, that would mean:
- you have to use ant 1.7 and above
- at least junit-4.?.jar has to be in ants classpath (e.g. in ./lib)
- your PATH must contain exuberant ctags binary
- your PATH variable must contain binaries of appropriate SCM SW, so commands
hg, sccs, cvs, git, bzr, svn (svnadmin too) must be available for full report

---------------------------------------------------
Using Checkstyle
---------------------------------------------------

To check that your code follows the standard coding conventions,
you can use checkstyle from http://checkstyle.sourceforge.net/

First you must download checkstyle from http://checkstyle.sourceforge.net/ ,
You need Version 5.1 (or newer). Extract the package you have
downloaded, and create a symbolic link to it from ~/.ant/lib/checkstyle,
e.g. like this:

   cd ~/.ant/lib
   unzip ~/Desktop/checkstyle-5.1.zip
   ln -s checkstyle-5.1 checkstyle

You also have to create symbolic links to the jar files:

   cd checkstyle
   ln -s checkstyle-5.1.jar checkstyle.jar
   ln -s checkstyle-all-5.1.jar checkstyle-all.jar

To run checkstyle on the source code, just run ant checkstyle:

   ant checkstyle

Output from the command will be stored in the checkstyle directory.

If you want to install checkstyle some other place than ~/.ant/lib, you can
untar the .tar.gz file to a directory, and use the checkstyle.home property
to tell ant where to find checkstyle, like this (if you have installed 
checkstyle under the lib directory):

  ant checkstyle -Dcheckstyle.home=lib/checkstyle

---------------------------------------------------
Using PMD and CPD
---------------------------------------------------

To check the quality of the OpenGrok code you can also use PMD
from http://pmd.sourceforge.net/.

How to install:

  cd ~/.ant/lib
  unzip ~/Desktop/pmd-bin-4.2.5.zip
  ln -s pmd-4.2.5/ pmd

You also have to make links to the jar files:

  cd ~/.ant/lib/pmd/lib
  ln -s pmd-4.2.5.jar pmd.jar
  ln -s jaxen-1.1.1.jar jaxen.jar

To run PMD on the rource code, just run ant pmd:

  ant pmd

Outout from the command will be stored in the pmd subdirectory.

  % ls pmd
  pmd_report.html  pmd_report.xml

If you want to install PMD some other place than ~/.ant/lib, you can
unzip the .zip file to a directory, and use the pmd.home property
to tell ant where to find PMD, like this (if you have installed 
PMD under the lib directory):

  ant pmd -Dpmd.home=lib/pmd-4.2.5

To run CPD, just use the same as above, but use targets:

  ant cpd cpd-xml

Which will result in

  % ls pmd
  cpd_report.xml cpd_report.txt

---------------------------------------------------
Using JDepend
---------------------------------------------------
To see dependencies in the source code, you can use JDepend from
http://clarkware.com/software/JDepend.html.

How to install:

  cd ~/.ant/lib
  unzip ~/Desktop/jdepend-2.9.zip
  ln -s jdepend-2.9/ jdepend
  cd jdepend/lib
  ln -s jdepend-2.9.jar jdepend.jar

How to analyze:

  ant jdepend

Output is stored in the jdepend directory:

  $ ls jdepend/
  report.txt  report.xml

AUTHORS 
-------
Chandan B.N, Sun Microsystems. https://blogs.sun.com/chandan
Trond Norbye, norbye.org
Knut Pape, eBriefkasten.de
Martin Englund, Sun Microsystems
Knut Anders Hatlen, Sun Microsystems
Lubos Kosco, Sun Microsystems
