#! /bin/ksh
# Dummy wrapper-script to set up the PYTHONPATH and start bzr..
PYTHONPATH=${HOME}/bin/bazaar/lib/python2.5/site-packages
LC_ALL="C"
export PYTHONPATH LC_ALL
${HOME}/bin/bazaar/bin/bzr $*
exit $?
