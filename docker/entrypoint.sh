#!/usr/bin/env bash
#
# Copyright (c) 2026 Oracle and/or its affiliates. All rights reserved.
#

set -euo pipefail

#
# This is the Docker container entry point. It makes sure the directories
# used by the start and related programs have the correct ownership
# and then executes the start program as non-root.
#

function fix_ownership() {
  typeset dir="$1"

  echo "Making sure the ownership of the files/directories under '$dir' is $OWNER_USER:$OWNER_GROUP"

  #
  # The directory itself has to be owned by the user (assuming certain permissions),
  # so that the indexer can create directories and files underneath.
  #
  chown $OWNER_USER:$OWNER_GROUP "$dir"

  #
  # Perform the check per subdirectory to avoid unnecessary churn.
  # Assumes the ownership of the directories matches its subdirectories/files.
  #
  find "$dir" -maxdepth 1 -mindepth 1 \! -user $OWNER_USER -o \! -group $OWNER_GROUP | \
      xargs -r chown -R $OWNER_USER:$OWNER_GROUP
}

command -v gosu >/dev/null 2>&1 || { echo "gosu missing"; exit 1; }

DATA_ROOT="/opengrok/data"
if [[ ! -d $DATA_ROOT ]]; then
  echo "Expected mounted directory at '$DATA_ROOT' but found none; create volume or directory"
	exit 1
fi

SRC_ROOT="/opengrok/src"
if [[ ! -d $SRC_ROOT ]]; then
  echo "Expected mounted directory at '$SRC_ROOT' but found none; create volume or directory"
	exit 1
fi

# The user/group the start program will run as.
# This has to match the user and group name in the Dockerfile.
OWNER_USER=appuser
OWNER_GROUP=appgroup

#
# The Tomcat webapps directory needs to be writable so that the
# start program can deploy the webapp.
#
chown -R $OWNER_USER:$OWNER_GROUP "/usr/local/tomcat/webapps"

# The start program needs to be able to create configuration files in the /opengrok/etc/ directory.
chown -R $OWNER_USER:$OWNER_GROUP "/opengrok/etc"

fix_ownership "$SRC_ROOT"
fix_ownership "$DATA_ROOT"

exec gosu $OWNER_USER "$@"
