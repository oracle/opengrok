If you wanted to make a change to OpenGrok code base (small fix, new feature, anything) here's couple of steps to get you started.

### Before we begin

Firstly, create Github account (of course !).

Then install:

1. Solaris 11+
2. JDK7 (`pkg install developer/java/jdk`)
3. Exuberant ctags (from source, no IPS package yet)
4. Git (`pkg install git`)
5. Netbeans (with bundled Tomcat)
6. other SCMs (e.g. `pkg install mercurial subversion`, will be handy for testing)

Also, it would not hurt if you created an issue (https://github.com/OpenGrok/OpenGrok/issues) and mentioned that you will be working on it.

### Setting up your code repository

You'll want to create a fork of the OpenGrok/OpenGrok repo. On Github this is as simple as clicking '**Fork**' on the main project page. Getting the source code of your fork is easy, just use the instructions on the front page of the project and select the right method for you for getting the source (https://help.github.com/articles/which-remote-url-should-i-use).

Here's an example on getting the source from command line (assuming your fork is called 'foo/OpenGrok' (where 'foo' is your username on Github)

```
git clone git@github.com:foo/OpenGrok.git opengrok-git-mine
```

You'll want to setup remotes (mainly the path to the upstream repo) using the steps on https://help.github.com/articles/fork-a-repo For OpenGrok it would be:

```
cd opengrok-git-mine
git remote add upstream git@github.com:OpenGrok/OpenGrok.git
```

Then create a branch (again, the help document on https://help.github.com/articles/fork-a-repo contains detailed steps) and switch to it, e.g.:

```
git branch myfix
git checkout myfix
```

### Building

To build the code from command line, just run `ant`. It will download all necessary pre-requisites. When using IDE such as Netbeans, there is already prepared project which can be loaded. So, start Netbeans and open the project via File -> Open Project (Ctrl+Shift+O), navigate to the `opengrok-git-mine` directory and simply press the 'Open Project' button. You do not have to resolve the missing dependencies as they will be automatically installed during first build. Then there is a sub-project which contains the web part of OpenGrok. Go to File -> Open Project, navigate to the `opengrok-git-mine` directory contents and select the `opengrok-web-nbproject`. If you are behind a proxy, make sure to setup proxy in Tools -> Options -> Proxy Settings so that builds done during the build can succeed. To build the project select the project in the first column and go to Run -> Build (F11). The build should be successful, it will be visible in the Output tab:

![fresh OpenGrok build](https://github.com/OpenGrok/OpenGrok/wiki/images/opengrok-build.png)

### Deploy the web app to the web server

Right click on the `opengrok-web-nbproject` and select 'Deploy'. It should start the Tomcat shipped with the Netbeans and deploy the web app to it. By default the OpenGrok instance will be accessible on `http://127.0.0.1:8084/source/` - use your browser to see it.

### Setup sources and index them

Now setup the sources to be indexed under e.g. `/var/opengrok/src` and create data directory for storing indexes under e.g. `/var/opengrok/data`. Make sure both directories have correct permissions so that the user running the Netbeans process can read and write to them.

Now right-click on the opengrok project and select Properties. Under the dialog window go to Run and populate the 'Arguments' text field with arguments to the opengrok.jar, e.g. for our case it would be:

```
-s /var/opengrok/src -d /var/opengrok/data -c /usr/local/bin/ctags -H -S -U localhost:2424
```

This is assuming the `ctags` binary of your Exuberant ctags installation resides in `/usr/local/bin/ctags`.

Now select the opengrok project in the left column and go to Run -> Run Project (F6). To reindex from scratch simply do `rm -rf /var/opengrok/data/*` and Run Project again. If you now refresh the web page mentioned above it will reflect the reindex and you can do searches etc.

### Debugging

Simply insert a breakpoint either in the Indexer code or the webapp and Run it or do something with the browser, respectively. Then it is possible to single step, observe the variables etc.

### Test

To run tests in single file, open the file from the left column which contains the projects (e.g. opengrok -> Test Packages -> org.opensolaris.opengrok.history -> MercurialRepositoryTest.java) and right-click on it and select Test File (Ctrl+F6). It might be necessary to run 'Debug Test File' first for the first time so that the test files are correctly produced.

### Publish changes

Once done with your changes, save them in Netbeans, `git commit` and `push` them to your repository (or you can do the Git dance directly from Netbeans using the Team -> Git menu). From there it is possible to create new pull request to the origin master branch using the standard Github process (https://help.github.com/articles/creating-a-pull-request - again Github help describes this in detail).