# Table of contents

<!-- toc -->

- [Using OpenGrok messages](#using-opengrok-messages)
- [Messages](#messages)
  * [Types](#types)
  * [Tags](#tags)
    + [Normal and Abort messages](#normal-and-abort-messages)
- [Tools](#tools)
  * [Usage:](#usage)
  * [Examples](#examples)
- [XSS Vulnerability](#xss-vulnerability)

<!-- tocstop -->

# Using OpenGrok messages

This is a howto to introduce a new feature in OpenGrok which allows you to use custom messages in OpenGrok.

# Messages

Deployed OpenGrok can receive couple of messages through the active socket which
usually listens for the main configuration file. These are used in the web
application and displayed to the users. One can easily notify users about some
important events, for example that the reindex is being in progress and that
the searched information can be inconsistent.

While there are visible messages in the user interface, you can send a couple of other messages
regarding configuration and web application statistics. All types are listed below.

The OpenGrok comes with a tool which allows you to send these messages without
any problem. It is called `Messages` and it is located under the tools directory.
See the file for usage and more information.

## Types

Currently supported message types:

1. `NormalMessage` (normal)

    This message is designed to display some information in the web application.
    Use tags to target a specific project.

2. `AbortMessage` (abort)

    This message can delete some already published information in
    the web application.
    Use tags to restrict the deletion only to specific projects.

3. `StatsMessage` (stats)

    This message is designed to retrieve some information from the web application.
    The purpose of the message is specified in the text field as one of:
     - "reload"  the application reloads the statistics file and returns the loaded statistics
     - "clean"   the application cleans its current statistics and returns the empty statistics
     - "get"     the application returns current statistics

4. `ConfigMessage` (config)

     This message retrieves or sends a configuration to the webapp,
     depending on tag. 

    * **setconf** – tag sends config to webapp and requires a file as an argument.
    * **getconf** – tag retrieves the configuration from the webapp.
    * **set** – tag sets particular configuration option in the webapp.
    * **auth** – tag requires "reload" text and
                 reloads all authorization plugins.

5. `RefreshMesssage` (refresh)

    Sent at the end of partial reindex to trigger refresh of SearcherManagers.

6. `ProjectMessage`

    Get project listings and information.

  * **add** – adds project(s) and its repositories to the configuration.
    If the project already exists, refresh list of its repositories.
  * **delete** – removes project(s) and its repositores from the configuration.
    Also deletes its data under data root (but not the source code).
  * **indexed** – mark the project(s) as indexed so it becomes visible in the UI
  * **get-repos** – get list of repositories in the form of relative paths to source root for given project(s)
  * **get-repos-type** – get repository type(s) for given project(s)

7. `RepositoryMessage`

    Get repository information.

  * **get-repo-type** – get repository type


## Tags

Every message can have set of tags which give the closer specification to the message. Their meaning is specific for the particular message type.

### Normal and Abort messages

The tag can target specific project in the application. The exact string match is neccessary.

The tag "main" is reserved for the global information.

# Tools

There is a tool in tools directory ([Messages](https://github.com/OpenGrok/OpenGrok/blob/master/tools/Messages)) which is suitable to send messages to OpenGrok web application.

The script has documented usage.

## Examples

``` bash
$ tools/Messages --help # see usage and defaults
$ tools/Messages "Hello" # send a message with text "Hello" with tag "main"
$ tools/Messages -e "+1 min" "Hello" # set expiration to 1 minutes from now
$ tools/Messages -e "+30 min" "Hello" # set expiration to 30 minutes from now
$ tools/Messages -c "error" -e "+30 min" "Hello" # display the message as error (red color)
$ tools/Messages -c "warning" -e "+30 min" "Hello"  # display the message as warning (yellow color)
$ tools/Messages -c "warning" -e "+30 min" --type abort "Hello" # send abort message (delete messages in the system)
$ tools/Messages -n stats get # get actual statistics as JSON
$ tools/Messages -n stats reload # reload the statistics file
$ tools/Messages -n stats clean # cleans all the statistics
$ tools/Messages -n config setconf /var/opengrok/etc/groups.xml # replaces the web application configuration
```

The script is also packaged into the target archive and the usage is similar

```bash
$ /usr/opengrok/bin/Messages --help
```

You can also specify different instance base with environment variables, like it is when using the OpenGrok shell wrapper.

# XSS Vulnerability

The messages can contain custom HTML markup and there is no XSS filter or any other kind of restriction for the displayed result. You should restrict the configuration listener (localhost:2424) only to users who you trust. Further information [here](https://github.com/OpenGrok/OpenGrok/wiki/How-to-install-OpenGrok#cli---command-line-interface-usage), under the configuration.