OpenGrok can be run with or without projects. Project is simply a directory under OpenGrok source root directory that can have one or more Source Code Management repositories underneath. In a setup without projects, all of the data have to be indexed at once. With projects however, each project has its own index so it is possible to index projects in parallel, thus speeding the overall process. Better yet, this can include project synchronization which usually involves running commands like `git pull` in all the repositories for given project.

This is handy in case the synchronization, indexing for some of the projects is taking a long time or simply you have lots of projects. Or all of it together.

Previously, it was necessary to index all of source root in order to discover new projects.
Starting with OpenGrok 1.1, using the `opengrok-projadm` tool (that utilizes the `opengrok-config-merge` tool and RESTful API) it is possible to manage the projects.
As a result, the indexing of complete source root is only necessary when upgrading across OpenGrok version
with incompatible Lucene indexes.

The following is assuming that the commands `opengrok-projadm`, `Groups` and `opengrok-config-merge` tools are in `PATH`. You can install these from the opengrok-tools python package available in the [release tarball](https://github.com/oracle/opengrok/wiki/How-to-setup-OpenGrok#step1---install-management-tools-optional).

Combine these procedures with the parallel processing tools and you have per-project management with parallel processing.

The following examples assume that OpenGrok install base is under the `/opengrok` directory.

## Adding project

- backup current config (this could be done by copying the `configuration.xml` file aside, taking file-system snapshot etc.)
- clone the project repositories under source root directory
- perform any necessary authorization adjustments
- add the project to configuration (also refreshes the configuration on disk):
```
   opengrok-projadm -b /opengrok -a PROJECT
```
- change any per-project settings (see [Web services wiki](/OpenGrok/OpenGrok/wiki/Web-services#rest-api))
- if per-project configuration was changed in the previous step, get the changed configuration (so that the indexer can follow it):
```
   opengrok-projadm -b /opengrok -r
```
- reindex
  - Use `OpenGrok indexpart` or `reindex-project.ksh` (in the latter case the previous step is not necessary since the script downloads fresh configuration from the webapp)
- save the configuration (this is necessary so that the indexed flag of the project is persistent). The -R option can be used to supply path to read-only configuration so that it is merged with current configuration.
```
   opengrok-projadm -b /opengrok -r
```
- now it is possible to reindex the project with `reindex-project.py` (see [repository synchronization](/OpenGrok/OpenGrok/wiki/Repository-synchronization#syncpy))

## Deleting a project

- backup current config
- delete the project from configuration (deletes project's index data and refreshes on disk configuration). The -R option can be used to supply path to read-only configuration so that it is merged with current configuration.
```
   opengrok-projadm -b /opengrok -d PROJECT
```
- perform any necessary authorization, group, per-project adjustments in read-only configuration (if any) - see [putting read-only configuration into effect](https://github.com/oracle/opengrok/wiki/Read-only-configuration#putting-read-only-configuration-into-effect) 
