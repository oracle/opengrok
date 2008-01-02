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

# If you need to set properties (Ex. override the mercurial binary)
#PROPERTIES=-Dorg.opensolaris.opengrok.history.Mercurial=/home/trond/bin/hg

# Uncomment the following line if your source contains Mercurial repositories.
# SCAN_FOR_REPOS="-S"

# You might want to add more available memory, and perhaps use a server jvm?
#JAVA_OPTS="-server -Xmx1024m"

java ${JAVA_OPTS} ${PROPERTIES} -jar ${PROGDIR}/opengrok.jar ${SCAN_FOR_REPOS} -c ${EXUB_CTAGS} -s ${SRC_ROOT} -d ${DATA_ROOT}

# OPTIONAL
java -classpath ${PROGDIR}/opengrok.jar org.opensolaris.opengrok.web.EftarFile ${PATH_DESC} ${DATA_ROOT}/index/dtags.eftar
