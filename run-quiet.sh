#!/bin/sh

PROGDIR=`dirname $0`

# REQUIRED The root of your source tree
SRC_ROOT=/your/src/tree/

# REQUIRED  The directory where the data files like
# Lucene index and hypertext cross-references are stored
DATA_ROOT=/var/tmp/opengrok_data

# OPTIONAL A tab separated files that contains small
# descriptions for paths in the source tree
PATH_DESC=${PROGDIR}/paths.tsv

# A modern Exubrant Ctags program 
# from http://ctags.sf.net
EXUB_CTAGS=/usr/local/bin/ctags

java -jar ${PROGDIR}/opengrok.jar -q -c ${EXUB_CTAGS} -s ${SRC_ROOT} -d ${DATA_ROOT}

# OPTIONAL
java -classpath ${PROGDIR}/opengrok.jar org.opensolaris.opengrok.web.EftarFile ${PATH_DESC} ${DATA_ROOT}/index/dtags.eftar
