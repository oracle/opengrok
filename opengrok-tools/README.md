
# OpenGrok tools

Set of scripts to facilitate project synchronization and mirroring

The scripts require Python 3 and they rely on a binary/symlink `python3` to be
present that points to the latest Python 3.x version present on the system.

Currently it is assumed that Python 3.6 and greater is used.

See https://github.com/oracle/opengrok/wiki/Repository-synchronization
for more details.

# Content

This is a list of the binaries in this package.

```text
opengrok-config-merge
opengrok-deploy
opengrok-groups
opengrok
opengrok-indexer
opengrok-java
opengrok-mirror
opengrok-projadm
opengrok-reindex-project
opengrok-sync
```

# Development

## Environment

Prepare a virtual environment

```bash
python3 -m venv env
. env/bin/activate
```

## Developing

When you start developing, install the package in a development mode.

```bash
python setup.py develop
```

This installs the package however keeping the links directly to your source,
so you can edit the files and see the immediate results.

Start developing, making changes in files. Test your changes with calling the entry points.

```bash
export PYTHONPATH=`pwd`/src/main/python:$PYTHONPATH
opengrok-groups
opengrok-sync
```

It is necessary to set the python path as the python interpreter is not able to find the packages
in our provided structure on its own.

Also you call the opengrok tools scripts by the entry points then (`opengrok-groups`, ...).
Calling directly the python script `groups.py` would lead to error related to relative imports.

Note that on macOS, you will need to install libgit2 library for the tests
to pass.

## Installation

Test installing your package into the local environment

```bash
python setup.py install
# now you can try console scripts
opengrok-groups
opengrok-sync
```

or make a distribution tarball.

```bash
python setup.py sdist
ls -l dist/
```

### Installation on the target system

Use the distribution tarball and run `pip`.

```bash
python3 -m pip install opengrok_tools.tar.gz
```

This will download all dependencies and install the package to your local python3 modules.
You can use console scripts to run the package binaries.

#### Installing to a specified directory

You can also install the tools to a specified directory, we suggest you to use the python virtual environment for it.

```bash
cd /opt/opengrok
python3 -m venv opengrok-tools
opengrok-tools/bin/python -m pip install opengrok_tools.tar.gz
```

This will install the package and all the dependencies under the `/opt/opengrok/opengrok-tools` directory.
You can then call the scripts with

```bash
/opt/opengrok/opengrok-tools/bin/opengrok-indexer
/opt/opengrok/opengrok-tools/bin/opengrok-groups
...
```

#### Uninstalling

```bash
python3 -m pip uninstall opengrok_tools
```

## Testing

```bash
python setup.py install test
./mvnw test
```

## Cleanup

Deactivate the virtual environment
```bash
deactivate
# optionally
# rm -r env
```

