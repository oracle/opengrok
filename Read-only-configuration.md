# Table of contents

<!-- toc -->

- [Advanced configuration of OpenGrok](#advanced-configuration-of-opengrok)
  * [Configuration workflow](#configuration-workflow)
  * [Read only configuration](#read-only-configuration)
  * [OpenGrok Shell Wrapper](#opengrok-shell-wrapper)
  * [List of most common configuration options](#list-of-most-common-configuration-options)
- [Generating the read only configuration](#generating-the-read-only-configuration)
  * [Generating group structure](#generating-group-structure)
- [Putting read-only configuration into effect](#putting-read-only-configuration-into-effect)
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
 - REST API endpoint for configuration `{webapp_uri}/api/v1/configuration`

The flow is as follows:

1. The indexer run
  
    Indexer is usually run with the `-W` parameter which tells the path where to write the configuration to. Let say it is default to `/var/opengrok/etc/configuration.xml`. The indexer runs and **overwrites** the possible existing file with the new configuration it gathered while doing its work.

    The word *overwrites* here is somewhat important because if you made a customization in that file - **it will simply disappear**. This is radically different to how configuration files are treated e.g. in Unix world where one expects that the application never changes its configuration file.

    At the end of indexing it will usually (depends on whether the `-U` option is used) send the new configuration via the REST API to the web application so that it can refresh its inner structures to reflect the changes.

2. Web application start
  
    When the application starts (the server starts, deploy) it reads the configuration from the the `/var/opengrok/etc/configuration.xml` directly. If the file does not exist, the application warns the user in the browser and the initial index run is needed (as described above) as this usually means that the indexer has not run yet.

    This file is particularly important in case the server container is restarted or the server rebooted - the webapp can then read the configuration and start serving the requests (without having to run the indexer to actually generate the configuration).

    Thus, the `/var/opengrok/etc/configuration.xml` file serves as a persistent storage for both the indexer and the weapp.

This presents a problem of how to store any customizations since all of them get wiped out always when the reindex is finished and the configuration is overwritten.

## Read only configuration

Therefore we substitute the persistent storage with another configuration file `/var/opengrok/etc/read-only.xml` which has the same syntax as the main `/var/opengrok/etc/configuration.xml` and contain the customized values. This file is passed to the indexer as a `-R` parameter and is decoded before the indexer runs. The indexer then fills the rest of the values which are usually configurable from the command line interface.

**This is the ONLY way how to make a persistent configuration changes in your OpenGrok instance for options that are not customizable as an indexer parameter!**

## List of most common configuration options

see
  - https://github.com/oracle/opengrok/wiki/Indexer-configuration
  - https://github.com/OpenGrok/OpenGrok/wiki/Webapp-configuration

# Generating the read only configuration

At this point it might be quite difficult to guess the syntax of the XML file for the configuration. That is where the `Groups` tool is quite handy.

You can generate an empty configuration object with the the `opengrok-groups` Python script:

```bash
$ opengrok-groups -- -e
<?xml version="1.0" encoding="UTF-8"?>
<java version="1.8.0_121" class="java.beans.XMLDecoder">
 <object class="org.opengrok.indexer.configuration.Configuration"/>
</java>
```

Now, some knowledge about Java beans is necessary to add some tunables. This is very much hand process. You can get some inspiration from looking at the contents of `/var/opengrok/etc/configuration.xml` once the reindex is done. Here is an example of how to set a basic string and a boolean property:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<java version="1.8.0_121" class="java.beans.XMLDecoder">
 <object class="org.opengrok.indexer.configuration.Configuration">

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

# Putting read-only configuration into effect

The following is assuming that OpenGrok base directory is `/opengrok`.

- backup current config
- make any necessary changes to `/opengrok/etc/readonly_configuration.xml`
- perform sanity check, e.g. when modifying project groups (https://github.com/OpenGrok/OpenGrok/wiki/Project-groups):
```
  opengrok-groups -a opengrok.jar -- -i /opengrok/etc/readonly_configuration.xml -l
```
- if you are adding project and changing regular expression of project group, try matching it: 
```
  opengrok-groups -a opengrok.jar -- -i /opengrok/etc/readonly_configuration.xml \
      -m PROJECT_TO_BE_ADDED
```
- get current config from the webapp, merge it with read-only configuration and upload the new config to the webapp
```
   opengrok-projadm -b /opengrok \
       -c /opengrok/dist/bin/venv/bin/opengrok-config-merge --jar opengrok.jar \
       -R /opengrok/etc/readonly_configuration.xml -r -u
```

This is particularly handy when using [per-project management ](https://github.com/OpenGrok/OpenGrok/wiki/Per-project-management)

Note that given that OpenGrok treats the majority of metadata as UTF-8 encoding, handling the `configuration.xml` like above pretty much requires that the locale settings are set accordingly.

# Real time web application change

## Single property change

Mostly for testing purposes it is available also to test some of the settings in the web application without the need to run the indexer. To do this there is are [Web Services](https://github.com/oracle/opengrok/wiki/Web-services).

```bash
$ curl -d "/var/opengrok/src" "${webapp_uri}/api/v1/configuration/pluginDirectory"
$ curl -d "true" "${webapp_uri}/api/v1/configuration/authorizationWatchdogEnabled"
$ curl -d "25" "${webapp_uri}/api/v1/configuration/hitsPerPage" # instead of 10
```

This call only works for primitive java types and has only meaning for the options which actually changes some behaviour in the web application.

If you need the resulting configuration to become persistent, you will need to get it via [Web Services](https://github.com/oracle/opengrok/wiki/Web-services) and store to `/var/opengrok/etc/configuration.xml`.

## Complete configuration change
  
Via the [Web Services](https://github.com/oracle/opengrok/wiki/Web-services) interface you can send a brand new configuration to the web application.

```bash
$ curl -d "@/var/opengrok/etc/configuration.xml" \
    -H "Content-Type: application/xml" \
    -X PUT "${webapp_uri}/api/v1/configuration"
```