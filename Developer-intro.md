If you wanted to make a change to OpenGrok code base (small fix, new feature, anything) here's couple of steps to get you started.

### Before we begin

Firstly, create Github account (of course !).

OpenGrok can be built and tested on Linux, OS X, BSD, Solaris, Windows etc. You will need:

1. At least JDK8
1. [Universal ctags](https://github.com/universal-ctags) (from source)
1. Git (for cloning the repository), Subversion (needed for successful build)
1. Any modern IDE (e.g. [IDEA](https://www.jetbrains.com/idea/), [Eclipse](http://www.eclipse.org/downloads/packages/))
1. other SCMs 

Also, it would not hurt if you created an issue (https://github.com/oracle/OpenGrok/issues) and mentioned that you will be working on it.

### Setting up your code repository

You'll want to create a fork of the oracle/OpenGrok repo. On Github this is as simple as clicking '**Fork**' on the main project page. Getting the source code of your fork is easy, just use the instructions on the front page of the project and select the right method for you for getting the source (https://help.github.com/articles/which-remote-url-should-i-use).

Here's an example on getting the source from command line (assuming your fork is called 'foo/OpenGrok' (where 'foo' is your username on Github)

```
git clone git@github.com:foo/OpenGrok.git opengrok-git-mine
```

You'll want to setup remotes (mainly the path to the upstream repo) using the steps on https://help.github.com/articles/fork-a-repo For OpenGrok it would be:

```
cd opengrok-git-mine
git remote add upstream git@github.com:oracle/OpenGrok.git
```

Then create a branch (again, the help document on https://help.github.com/articles/fork-a-repo contains detailed steps) and switch to it, e.g.:

```
git branch myfix
git checkout myfix
```

### Building

To build the code from command line, just run `mvn compile`. It will download all necessary pre-requisities. When using IDE you can open or import the project.

### Deploy the web app to the web server

There are multiple possibilities.

#### Deploy `.war` directly

Run command `mvn package -DskipTests` which will create a release as in the [releases page](https://github.com/oracle/opengrok/releases). This release can be found in the `distribution/target` directory with file name `opengrok-{version}.tar.gz`. Unzip the file by `tar xvf opengrok-{version}.tar.gz`. The `.war` file is located in `opengrok-{version}/lib/source.war`. You can copy this file to the web applications directory of your application server (e.g. `webapps` for Tomcat). Or you can use different means specific to application servers (e.g. Tomcat Manager).

#### Use Tomcat Maven plugin

Modify `~/.m2/settings.xml` to:
```xml
<settings>
  <servers>
    <server>
      <id>OpenGrok</id>
      <username>admin1</username> <!-- you can use different name -->
      <password>admin1</password> <!-- you can use different password -->
    </server>
  </servers>
</settings>
```

Add to `tomcat_users.xml` located in `conf` directory of your Tomcat server:
```xml
<user password="admin1" roles="manager-script,admin" username="admin1"/>
```

**Note:** Do not add `manager-gui` role because it won't work.

Type following commands to the console:
```bash
mvn install
cd opengrok-web
mvn tomcat7:redeploy
```

#### Use IDE (will be updated)

### Setup sources and index them

Now setup the sources to be indexed under e.g. `/var/opengrok/src` and create data directory for storing indexes under e.g. `/var/opengrok/data`. Make sure both directories have correct permissions so that the user running the process can read and write to them.

Run the main method of `org.opengrok.indexer.index.Indexer` with the following arguments: (IDE and command line steps will be updated)
```
-W /var/opengrok/etc/configuration.xml -s /var/opengrok/src -d /var/opengrok/data -c /usr/local/bin/ctags -H -S -U http://localhost:8080/source
```

This is assuming the `ctags` binary of your Exuberant ctags installation resides in `/usr/local/bin/ctags`.

If you now refresh the web page mentioned above it will reflect the reindex and you can do searches etc.

### Debugging

Simply insert a breakpoint either in the Indexer code or the webapp. 

If you are running the Indexer then you can easily debug it from you IDE.

To debug the web application the most generic way would be to add debug parameters to the application server.
(e.g. `CATALINA_OPTS=-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n` for Tomcat in `setenv.sh` file). Then you can simply use remote debugging from your IDE.

See [[Debugging wiki|Debugging]] for more information on debugging.

### Test

To run tests type `mvn test` command. For specific tests you can use `-Dtest` option, e.g. `mvn test -Dtest=IndexerTest -DfailIfNoTests=false`.

Also, OpenGrok repository is setup so that pushes will trigger [Travis](https://travis-ci.org) builds so it is not necessary to run tests on your workstation - just commit and push to Github (first it is necessary to enable Travis for your fork on the Travis web).

See [Developers wiki](https://github.com/oracle/opengrok/wiki/Developers) for more info on testing.

### Publish changes

Once done with your changes, save them, `git commit` and `push` them to your repository (or you can do the Git dance directly from your IDE). From there it is possible to create new pull request to the upstream master branch using the standard Github process (https://help.github.com/articles/creating-a-pull-request - again Github help describes this in detail).

Make sure to sign the Oracle Contributor Agreement (http://www.oracle.com/technetwork/goto/oca) and ideally post the contributor number to the pull request.