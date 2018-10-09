
# OpenGrok tools

Set of scripts to facilitate project synchronization and mirroring

The scripts require Python 3 and they rely on a binary/symlink `python3` to be
present that points to the latest Python 3.x version present on the system.

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

Install development dependencies

```bash
python -m pip install -r requirements.txt
```

## Developing

Start developing, making changes in files. Test your changes with usual python commands.

```bash
python src/main/python/opengrok_tools/groups.py
python src/main/python/opengrok_tools/sync.py
```

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
python3 -m pip install opengrok_tools-{version}.tar.gz
```

This will download all dependencies and install the package to your local python3 modules.
You can use console scripts to run the package binaries.

#### Uninstalling

```bash
python3 -m pip uninstall opengrok_tools
```

## Testing

```bash
python setup.py install test
mvn test
```

## Cleanup

Deactivate the virtual environment
```bash
deactivate
# optionally
# rm -r env
```

