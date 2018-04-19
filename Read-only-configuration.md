# Table of contents

<!-- toc -->

- [Advanced configuration of OpenGrok](#advanced-configuration-of-opengrok)
  * [Configuration workflow](#configuration-workflow)
  * [Read only configuration](#read-only-configuration)
  * [OpenGrok Shell Wrapper](#opengrok-shell-wrapper)
  * [List of most common configuration options](#list-of-most-common-configuration-options)
- [Generating the read only configuration](#generating-the-read-only-configuration)
  * [Generating group structure](#generating-group-structure)
- [Real time web application change](#real-time-web-application-change)

<!-- tocstop -->

# Configuration of indexer vs. configuration of webapp

There are too many options which can be passed to the OpenGrok instance. Some of them have a meaning only for the indexer while some of them only for the web application instance. There are even those which apply for both of the cases.

Most of the options are available as parameters to the OpenGrok indexer (see `java -jar opengrok.jar` for usage). However there is a number of options which **can not** be set through an indexer switch and this advanced configuration procedure must take a place.

## Configuration workflow

The thing about configuration in OpenGrok is that two different services have to share the same configuration in order to work properly.

Let us make some definitions before we start. We take the default values for the terms we use:

 - main configuration `/var/opengrok/etc/configuration.xml`
 - read only configuration `/var/opengrok/etc/read-only.xml`
 - listen address for the web application `localhost:2424`

The flow is as follows:

1. The indexer run
  
    Indexer is usually run with the `-W` parameter which tells the path where to write the configuration to. Let say it is default to `/var/opengrok/etc/configuration.xml`. The indexer runs and **overwrites** the possible existing file with the new configuration it gathered while doing its work.

    The word *overwrites* here is somewhat important because if you made a customization in that file - **it will simply disappear**. This is radically different to how configuration files are treated e.g. in Unix world.

    At the end of indexing it will usually (depends on whether the `-U` option is used) send the new configuration through the `localhost:2424` communication protocol to the web application so that it can refresh its inner structures to reflect the changes.

2. Web application start
  
    When the application starts (the server starts, deploy) it reads the configuration from the the `/var/opengrok/etc/configuration.xml` directly. If the file does not exist, the application warns the user in the browser and the initial index run is needed (as described above) as this usually means that the indexer has not run yet. This file is particularly important in case the server container is restarted or the server rebooted - the webapp can then read the configuration and start serving the requests.

    Thus, the `configuration.xml` file serves as a persistent storage for both the indexer and the weapp.

The problem now is that there is no way to persistently store any customizations. All of them get wiped out always when the reindex is finished and the configuration is overwritten.

## Read only configuration

Therefore we substitute the persistent storage with another configuration file `/var/opengrok/etc/read-only.xml` which has the same syntax as the main `/var/opengrok/etc/configuration.xml` and contain the customized values. This file is passed to the indexer as a `-R` parameter and is decoded before the indexer runs. The indexer then fills the rest of the values which are usually configurable from the command line interface.

**This is the ONLY way how to make a persistent configuration changes in your OpenGrok instance for options that are not customizable as an indexer parameter!**

## OpenGrok Shell Wrapper

On Unix systems, for those who prefer the `OpenGrok` shell wrapper instead of directly running the `java` with the `opengrok.jar` directly; there is an environment variable `OPENGROK_READ_XML_CONFIGURATION` which should point to the read only configuration file and this file is later passed to the indexer under the `-R` option.

```bash
OPENGROK_READ_XML_CONFIGURATION=/var/opengrok/etc/read-only.xml ./OpenGrok index
```

## List of most common configuration options

This is a list of the most common configuration options which are not available as an indexer switch.

 - `pluginDirectory`
 - `authorizationWatchdogEnabled`
 - `pluginConfiguration`
 - `groups`
 - ...

# Generating the read only configuration

At this point it might be quite difficult to guess the syntax of the xml file for the configuration. That is where the `Groups` tool is quite handy.

You can generate an empty configuration object with the `empty` subcommand:

```bash
$ tools/Groups empty
<?xml version="1.0" encoding="UTF-8"?>
<java version="1.8.0_121" class="java.beans.XMLDecoder">
 <object class="org.opensolaris.opengrok.configuration.Configuration"/>
</java>
```

About how to add some options please refer to the main configuration `/var/opengrok/etc/configuration.xml` and try to simulate the same process. Mostly the following is applicable:

```bash
$ tools/Groups empty
<?xml version="1.0" encoding="UTF-8"?>
<java version="1.8.0_121" class="java.beans.XMLDecoder">
 <object class="org.opensolaris.opengrok.configuration.Configuration">

  <void property="sourceRoot"> <!-- name of the property in configuration -->
   <string>/var/opengrok/src</string> <!-- java type and value -->
  </void>

  <void property="verbose"> <!-- name of the property in configuration -->
   <boolean>true</boolean> <!-- java type and value -->
  </void>

 </object>
</java>
```

Save this content into `/var/opengrok/etc/read-only.xml` and use the [steps above](#read-only-configuration) to add the read only configuration to the indexer run.

## Generating group structure

There is a shortcut for generating group structure embedded to the `Groups` tools. More information is in the [project groupings](https://github.com/OpenGrok/OpenGrok/wiki/Project-groups).

# Real time web application change

## Single property change

Mostly for testing purposes it is available also to test some of the settings in the web application without the need to run the indexer. To do this there is a tool [Messages](https://github.com/OpenGrok/OpenGrok/wiki/OpenGrok-Messages) which can send a configuration message to the web application.

```bash
$ tools/Messages -n config -t set "pluginDirectory = /var/opengrok/src"
$ tools/Messages -n config -t set "authorizationWatchdogEnabled = true"
$ tools/Messages -n config -t set "hitsPerPage = 10" # instead of 25
```

This tool only works for primitive java types and has only meaning for the options which actually changes some behaviour in the web application.

## Complete configuration change
  
    With the [Messages](https://github.com/OpenGrok/OpenGrok/wiki/Messages) tool you can send a brand new configuration to the web application.

    ```bash
    $ tools/Messages -n config setconf /var/opengrok/etc/configuration.xml
    ```

    The above will send the configuration in the `/var/opengrok/etc/configuration.xml` to the web application and replace its previous configuration.

However, as mentioned in the configuration flow, these changes **are not** persistent as they vanish when the web application receives the new configuration.