# How to build {OpenGrok

The easiest way to build (or modify) {OpenGrok is by using any modern IDE, but you can also build {OpenGrok from the command line.

## Requirements

You need the following:

- [JDK](http://java.sun.com/javase/downloads/index.jsp) 1.8 or higher
- [Maven](https://maven.apache.org)
- The source code is located in a [git](http://git-scm.com/)
repository
- First build has to happen with mvn which downloads all dependencies

## Check out the source

The first thing you need to do is to check out the source code. You can
check out the source with the following command:

`git clone https://github.com/oracle/OpenGrok`

(or clone any of your forks)

## Open correct project

OpenGrok does have a maven pom.xml for its modules.

## Prepare the source for compilation

First build should download all needed dependencies, so don't get scared
about missing jars.

## Compile the source

If you use NetBeans you should be able to open OpenGrok as a project and build it from there. Specifically, open pom.xml from the root directory, and click Run =\> Build Project (opengrok). (Ignore "Resolve problem" for jar files other than junit, but setup a webapp server for the-web-nbproject application.)

If you want to build from the command line, execute the following
command (it will need internet connection first time, so setup any
proxies if needed):

`mvn compile`

or (to clean and start from scratch)

`mvn clean compile`