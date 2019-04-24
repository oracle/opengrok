#!/bin/bash

LOCKFILE=/var/run/opengrok-indexer
URI="http://localhost:8080"

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
    -H -P -S -G \
    --leadingWildCards on \
    -W /var/opengrok/etc/configuration.xml \
    -U "$URI" \
    $INDEXER_OPT "$@"
date +"%F %T Indexing finished"

rm -f $LOCKFILE
