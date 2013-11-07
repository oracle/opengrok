If you wanted to make a change to OpenGrok code base (small fix, new feature, anything) here's couple of steps to get you started.

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

To build the code from command line, just run `ant`. It will download all necessary pre-requisites. When using IDE such as Netbeans, there is already prepared project which can be loaded. So, start Netbeans and open the project via File -> Open Project (Ctrl+Shift+O), navigate to the `opengrok-git-mine` directory and simply press the 'Open Project' button. You do not have to resolve the missing dependencies as they will be automatically installed during first build. Then there is a sub-project which contains the web part of OpenGrok. Go to File -> Open Project, navigate to the `opengrok-git-mine` directory contents and select the `opengrok-web-nbproject`. To build the project select the project in the first column and go to Run -> Build (F11).