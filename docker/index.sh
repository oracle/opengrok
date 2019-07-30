#!/bin/bash

LOCKFILE=/var/run/opengrok-indexer
URI="http://localhost:8080"
# $OPS can be overwritten by environment variable
OPS=${INDEXER_FLAGS:='-H -P -S -G'}

if [ -f "$LOCKFILE" ]; then
	date +"%F %T Indexer still locked, skipping indexing"
	exit 1
fi

touch $LOCKFILE

if [ -z $NOMIRROR ]; then
	date +"%F %T Mirroring starting"
	opengrok-mirror --all --uri "$URI"
	date +"%F %T Mirroring finished"
fi

date +"%F %T Indexing starting"
opengrok-indexer \
    -a /opengrok/lib/opengrok.jar -- \
    -s /opengrok/src \
    -d /opengrok/data \
    --remote on \
    --leadingWildCards on \
    -W /opengrok/etc/configuration.xml \
    -U "$URI" \
    $OPS \
    $INDEXER_OPT "$@"
date +"%F %T Indexing finished"

rm -f $LOCKFILE
