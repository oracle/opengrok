OpenGrok can be run with or without projects. Project is simply a directory under OpenGrok source root directory that can have one or more Source Code Management repositories underneath. In a setup without projects, all of the data have to be indexed at once. With projects however, each project has its own index so it is possible to index projects in parallel, thus speeding the overall process. Better yet, this can include project synchronization which usually involves running commands like `git pull` in all the repositories for given project.

This is handy in case the synchronization, indexing for some of the projects is taking a long time or simply you have lots of projects. Or all of it together.

Previously, it was necessary to index all of source root in order to discover new projects.
Starting with the changes for issue #1390, using the `Messages` tool it is possible to manage the projects.
As a result, the indexing of complete source root is only necessary when upgrading across OpenGrok version
with incompatible Lucene indexes.

The following is assuming that the commands `Messages`, `Groups` and `ConfigMerge` tools are in `PATH`.

Combine these procedures with the parallel processing tools under the [tools/sync](https://github.com/OpenGrok/OpenGrok/tree/master/tools/sync) directory and you have per-project management with parallel processing.

## Adding project

- backup current config (this could be done by copying the `configuration.xml` file aside, taking file-system snapshot etc.)
- clone the project repositories under source root directory
- add the project to configuration:
```
   Messages -n project -t PROJECT add
```
- save the config (this is necessary so that partial indexer can recognize the project and its repositories):
```
   Messages -n config -t getconf > /opengrok/etc/configuration.xml
```
- reindex
  - Use `OpenGrok indexpart` or `reindex-project.ksh` (in the latter case the previous step is not necessary since the script downloads fresh configuration from the webapp)
- save the configuration (this is necessary so that the indexed flag of the project is persistent) 
```
   Messages -n config -t getconf > /opengrok/etc/configuration.xml
```
- perform any necessary authorization adjustments
- add any per-project settings - go to the 'Changing read-only configuration' section below

## Deleting a project

- backup current config
- delete the project from configuration 
```
   Messages -n project -t PROJECT delete
```
- save the configuration 
```
   Messages -n config -t getconf > /opengrok/etc/configuration.xml
```
- delete the source code under source root
  - the index data for the project was deleted already
- perform any necessary authorization adjustments
- remove any per-project settings - go to the 'Changing read-only configuration' section below

## Changing read-only configuration

The following is assuming that OpenGrok base directory is `/opengrok`.

- backup current config
- make any necessary changes to /opengrok/etc/readonly_configuration.xml
- perform sanity check 
```
  OPENGROK_READ_XML_CONFIGURATION=/opengrok/etc/readonly_configuration.xml Groups list
```
- if you are adding project and changing regular expression of project group, try matching it: 
```
  OPENGROK_READ_XML_CONFIGURATION=/opengrok/etc/readonly_configuration.xml Groups match PROJECT_TO_BE_ADDED
```
- get current config from the webapp if `/opengrok/etc/configuration.xml` is not fresh: 
```
   Messages -n config -t getconf > /opengrok/etc/configuration.xml
```
- merge them together 
```
    ConfigMerge /opengrok/etc/readonly_configuration.xml /opengrok/etc/configuration.xml > /tmp/merged.xml
     mv /tmp/merged.xml  /opengrok/etc/configuration.xml
```
- upload the new config to the webapp 
```
   Messages -n config setconf /opengrok/etc/configuration.xml
```