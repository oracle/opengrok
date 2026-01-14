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

function fix_ownership() {
  typeset dir="$1"

  echo "Making sure the ownership of the files/directories under at '$dir' is $OWNER_ID:$OWNER_ID"

  #
  # The directory itself has to be owned by the user (assuming certain permissions),
  # so that the indexer can create directories and files underneath.
  #
  chown $OWNER_ID:$OWNER_ID "$dir"

  #
  # Perform the check per subdirectory to avoid unnecessary churn.
  # Assumes the ownership of the directories matches its subdirectories/files.
  #
  find "$dir" -maxdepth 1 -mindepth 1 \! -user $OWNER_ID \
      -exec chown -R $OWNER_ID:$OWNER_ID {} \;
}

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
# This has to match the ids of the user/group created in the Dockerfile.
OWNER_ID=1111

#
# The Tomcat webapps directory needs to be writable so that the
# start program can deploy the webapp.
#
chown -R $OWNER_ID:$OWNER_ID "/usr/local/tomcat/webapps"

# The start program needs to be able to create configuration files in the /opengrok/etc/ directory.
chown -R $OWNER_ID:$OWNER_ID "/opengrok/etc"

fix_ownership "$SRC_ROOT"
fix_ownership "$DATA_ROOT"

exec gosu $OWNER_ID "$@"
