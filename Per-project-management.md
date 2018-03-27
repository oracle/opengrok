OpenGrok can be run with or without projects. Project is simply a directory under OpenGrok source root directory that can have one or more Source Code Management repositories underneath. In a setup without projects, all of the data have to be indexed at once. With projects however, each project has its own index so it is possible to index projects in parallel, thus speeding the overall process. Better yet, this can include project synchronization which usually involves running commands like `git pull` in all the repositories for given project.

This is handy in case the synchronization, indexing for some of the projects is taking a long time or simply you have lots of projects. Or all of it together.

Previously, it was necessary to index all of source root in order to discover new projects.
Starting with OpenGrok 1.1, using the `projadm` tool (that utilizes the `Messages` and `ConfigMerge` tools) it is possible to manage the projects.
As a result, the indexing of complete source root is only necessary when upgrading across OpenGrok version
with incompatible Lucene indexes.

The following is assuming that the commands `projadm`, `Messages`, `Groups` and `ConfigMerge` tools are in `PATH`.

Combine these procedures with the parallel processing tools under the [tools/sync](https://github.com/OpenGrok/OpenGrok/tree/master/tools/sync) directory and you have per-project management with parallel processing.

The following examples assume that OpenGrok install base is under the `/opengrok` directory.

## Adding project

- backup current config (this could be done by copying the `configuration.xml` file aside, taking file-system snapshot etc.)
- clone the project repositories under source root directory
- perform any necessary authorization adjustments
- add the project to configuration (also refreshes the configuration on disk):
```
   projadm -b /opengrok -a PROJECT
```
- add any per-project settings - go to the 'Changing read-only configuration' section below
- reindex
  - Use `OpenGrok indexpart` or `reindex-project.ksh` (in the latter case the previous step is not necessary since the script downloads fresh configuration from the webapp)
- save the configuration (this is necessary so that the indexed flag of the project is persistent). The -R option can be used to supply path to read-only configuration so that it is merged with current configuration.
```
   projadm -b /opengrok -r
```

## Deleting a project

- backup current config
- remove any per-project settings - go to the 'Changing read-only configuration' section below
- delete the project from configuration (deletes project's index data and refreshes on disk configuration). The -R option can be used to supply path to read-only configuration so that it is merged with current configuration.
```
   projadm -b /opengrok -d PROJECT
```
- perform any necessary authorization adjustments

## Changing read-only configuration

The following is assuming that OpenGrok base directory is `/opengrok`.

- backup current config
- make any necessary changes to /opengrok/etc/readonly_configuration.xml
- perform sanity check 
```
  OPENGROK_READ_XML_CONFIGURATION=/opengrok/etc/readonly_configuration.xml \
     Groups list
```
- if you are adding project and changing regular expression of project group, try matching it: 
```
  OPENGROK_READ_XML_CONFIGURATION=/opengrok/etc/readonly_configuration.xml \
      Groups match PROJECT_TO_BE_ADDED
```
- get current config from the webapp, merge it with read-only configuration and upload the new config to the webapp
```
   projadm -b /opengrok -R /opengrok/etc/readonly_configuration.xml -r -u
```