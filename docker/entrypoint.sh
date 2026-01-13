#!/usr/bin/env bash
#
# Copyright (c) 2026 Oracle and/or its affiliates. All rights reserved.
#

set -e

#
# This is the Docker container entry point. It makes sure the directories
# used by the start and related programs have the correct ownership
# and then executes the start program as non-root.
#

DATA_ROOT="/opengrok/data"
if [[ ! -d $DATA_ROOT ]]; then
  echo "Not a directory: $DATA_ROOT"
	exit 1
fi

SRC_ROOT="/opengrok/src"
if [[ ! -d $SRC_ROOT ]]; then
  echo "Not a directory: $SRC_ROOT"
	exit 1
fi

# The uid the start program will run as.
OWNER_ID=1111

#
# The Tomcat webapps directory needs to be writable so that the
# start program can deploy the webapp.
#
chown -R $OWNER_ID:$OWNER_ID "/usr/local/tomcat/webapps"

# The start program will create configuration files in the /opengrok/etc/ directory.
chown -R $OWNER_ID:$OWNER_ID "/opengrok/etc"

echo "Making sure the ownership of the files/directories under source root at $SRC_ROOT is $OWNER_ID:$OWNER_ID"

#
# The mirroring performed by the main program needs to have write access to the source root.
# Assumes the ownership of the directories matches its subdirectories/files.
#
chown $OWNER_ID:$OWNER_ID "$SRC_ROOT"

#
# Perform the check per project to avoid unnecessary churn.
#
find "$SRC_ROOT" -maxdepth 1 -mindepth 1 -type d \! -user $OWNER_ID \
    -exec chown -R $OWNER_ID:$OWNER_ID {} \;

echo "Making sure the ownership of the files/directories under data root at $DATA_ROOT is $OWNER_ID:$OWNER_ID"

#
# The data root directory itself has to be owned by the user (assuming certain permissions),
# so that the indexer can create directories and files underneath.
#
chown $OWNER_ID:$OWNER_ID "$DATA_ROOT"

# The timestamp file does not exist prior to first successful indexing.
[[ -f $DATA_ROOT/timestamp ]] && chown $OWNER_ID:$OWNER_ID "$DATA_ROOT/timestamp"

#
# Change ownership of the data directories. This is done separately
# so that if for some reason just one of the directories is off,
# the time is not spent changing the others needlessly.
# Assumes the ownership of the directories matches its subdirectories/files.
#
# We do not care about the gid when performing the check.
#
find "$DATA_ROOT" -maxdepth 1 -mindepth 1 -type d \! -user $OWNER_ID \
    -exec chown -R $OWNER_ID:$OWNER_ID {} \;

exec gosu $OWNER_ID "$@"
