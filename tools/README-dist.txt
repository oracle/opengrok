
This directory contains set of scripts to facilitate project synchronization and
mirroring in a Python package.

The scripts require Python 3 and they rely on a binary/symlink python3 to be
present that points to the latest Python 3.x version present on the system.

All the scripts in the package are installed with the 'opengrok-' prefix.

See https://github.com/oracle/opengrok/wiki/Repository-synchronization for more
details.

The instructions below are pretty much standard way how to deal with Python
packages however may come handy if you never encountered Python package before.

Install
-------

* Installation on the target system so that the package is globally available:

Use the distribution tarball and run pip:

  python3 -m pip install opengrok-tools.tar.gz

This will download all dependencies and install the package to your local
python3 modules.

* Installing to a specified directory:

You can also install the tools to a specified directory, we suggest you to use
the python virtual environment for it:

  cd /opt/opengrok
  python3 -m venv opengrok-tools
  opengrok-tools/bin/python -m pip install opengrok-tools.tar.gz

This will install the package and all the dependencies under the
/opt/opengrok/opengrok-tools directory. You can then call the scripts with

/opt/opengrok/opengrok-tools/bin/opengrok-indexer
/opt/opengrok/opengrok-tools/bin/opengrok-groups
...

Uninstall
---------

* from system level package or from venv:
  (assuming the venv is activated or you are running python3 from the venv
  binary directory)

  python3 -m pip uninstall opengrok_tools

