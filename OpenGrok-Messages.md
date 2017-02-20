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

This is a howto to introduce a new feature in OpenGrok which allows you to display custom messages in the OpenGrok web interface.

# Messages

Deployed OpenGrok can receive couple of messages through the active socket which
usually listens for the main configuration file. These are used in the web
application and displayed to the users. One can easily notify users about some
important events, for example that the reindex is being in progress and that
the searched information can be inconsistent.

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

    This message sends a configuration to the webapp. Requires file as argument.

5. `RefreshMesssage` (refresh)

    Sent at the end of partial reindex to trigger refresh of SearcherManagers.

## Tags

Every message can have set of tags which give the closer specification to the message. Their meaning is specific for the particular message type.

### Normal and Abort messages

The tag can target specific project in the application. The exact string match is neccessary.

The tag "main" is reserved for the global information.

# Tools

There is a tool in tools directory ([Messages](https://github.com/OpenGrok/OpenGrok/blob/master/tools/Messages)) which is suitable to send messages to OpenGrok web application.

The script has documented usage.

## Usage:
```
$ tools/Messages --help

Usage: Messages [options] <text>

[OPTIONS]:
  -c|--class                    css class to apply for the message (default info)
  -e|--expire                   human readable date of expiration (default +5 min) (*)
  -h|--help                     display this message and exit
  -n|--type                     type of the message (default normal)
  -p|--port                     remote port number of the application (default 2424)
  -s|--server                   remote server of the application (default localhost)
  -t|--tag                      tag the message/multiple options (default main)
  -u|--expire-timestamp         explicit UTC timestamp for expiration in sec
  -v|--verbose                  verbose

  (*) see man date: option --date (requires GNU date - use DATE env variable)
  css classes: success, info, warning, error
  types: normal, abort, stats
  tags: main, <project name>
  text: supports html markup

  Message types:
    config:
     - send configuration to the webapp. Requires file as argument.
    normal:
     - assign a <text> to the main page or a project
     - can be more precise with <tags> (for specific project)
    abort:
     - discard existing messages in the system with the same <tags>
    stats:
     - ask the application for its statistics
     - query is formed in the message <text>:
       - "reload"  the application reloads the statistics file
                   and returns the loaded statistics
       - "clean"   the application cleans its current statistics
                   and returns the empty statistics
       - "get"     the application returns current statistics

  Optional environment variables:
    OPENGROK_CONFIGURATION - location of your configuration
      e.g. $ OPENGROK_CONFIGURATION=/var/opengrok/myog.conf tools/Messages ... 

    See the code for more information on configuration options / variables

```

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
$ tools/Messages -n config /var/opengrok/etc/groups.xml
```

The script is also packaged into the target archive and the usage is similar

```bash
$ /usr/opengrok/bin/Messages --help
```

You can also specify different instance base with environment variables, like it is when using the OpenGrok shell wrapper.

# XSS Vulnerability

The messages can contain custom HTML markup and there is no XSS filter or any other kind of restriction for the displayed result. You should restrict the configuration listener (localhost:2424) only to users who you trust. Further information [here](https://github.com/OpenGrok/OpenGrok/wiki/How-to-install-OpenGrok#cli---command-line-interface-usage), under the configuration.