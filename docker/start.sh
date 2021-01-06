#!/bin/bash

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END


# default period for reindexing (in minutes)
if [ -z "$REINDEX" ]; then
	REINDEX=10
fi

if [[ "${URL_ROOT}" = *" "* ]]; then
	date +"%F %T Deployment path contains spaces. Deploying to root..."
	export URL_ROOT="/"
fi

# Remove leading and trailing slashes
URL_ROOT="${URL_ROOT#/}"
URL_ROOT="${URL_ROOT%/}"

if [ "${URL_ROOT}" = "" ]; then
	WAR_NAME="ROOT.war"
else
	WAR_NAME="${URL_ROOT//\//#}.war"
fi

URI="http://localhost:8080/${URL_ROOT}"

OPENGROK_BASE_DIR="/opengrok"
OPENGROK_DATA_ROOT="$OPENGROK_BASE_DIR/data"
OPENGROK_SRC_ROOT="$OPENGROK_BASE_DIR/src"
BODY_INCLUDE_FILE="$OPENGROK_DATA_ROOT/body_include"
OPENGROK_CONFIG_FILE="$OPENGROK_BASE_DIR/etc/configuration.xml"

export OPENGROK_INDEXER_OPTIONAL_ARGS=$INDEXER_OPT
export OPENGROK_NO_MIRROR=$NOMIRROR

function deploy {
	if [[ ! -f "/usr/local/tomcat/webapps/${WAR_NAME}" ]]; then
		date +"%F %T Deployment path does not exist. Deploying..."

		# Delete old deployment and (re)deploy.
		rm -rf /usr/local/tomcat/webapps/*
		opengrok-deploy -c "$OPENGROK_CONFIG_FILE" \
	            "$OPENGROK_BASE_DIR/lib/source.war" "/usr/local/tomcat/webapps/${WAR_NAME}"

		# Set up redirect from /source
		mkdir "/usr/local/tomcat/webapps/source"
		echo "<% response.sendRedirect(\"/${URL_ROOT}\"); %>" > "/usr/local/tomcat/webapps/source/index.jsp"
	fi
}

function bare_config {
	# Populate the webapp with bare configuration.
	if [[ -f "$BODY_INCLUDE_FILE" ]]; then
		mv "$BODY_INCLUDE_FILE" "$BODY_INCLUDE_FILE.orig"
	fi
	echo '<p><h1>Waiting on the initial reindex to finish.. Stay tuned !</h1></p>' > "$BODY_INCLUDE_FILE"

  # The options used here should match the options used by the per-project
  # indexer in sync.yml.
  echo "Creating bare configuration"
	opengrok-indexer \
	    -a /opengrok/lib/opengrok.jar -- \
	    -s "$OPENGROK_SRC_ROOT" \
	    -d "$OPENGROK_DATA_ROOT" \
	    --remote on \
	    -P -H \
	    -W "$OPENGROK_CONFIG_FILE" \
	    --noIndex
  echo "Done creating bare config"
}

function wait_for_tomcat {
	# Wait for Tomcat startup and web app deploy.
	date +"%F %T Waiting for Tomcat startup and web app deployment..."
	while [ "`curl --silent --write-out '%{response_code}' -o /dev/null \"http://localhost:8080/${URL_ROOT}\"`" != "200" ]; do
		sleep 1;
	done
	date +"%F %T Tomcat startup finished and web app deployed"
}

function save_config {
  echo "Saving configuration"
  # Note: URI ends with a slash
  # TODO: check result of the API call and move only if successful
  curl -s -o "$OPENGROK_CONFIG_FILE" -X GET "${URI}api/v1/configuration"
  echo "Done saving configuration"
}

function add_projects {
  # Add each directory under source root as a project.
  # TODO: remove projects that no longer have the directory under source root
  # For https://github.com/oracle/opengrok/issues/3403 this should be replaced
  # with query to get all projects and add only those that are not already present.
  echo "Adding projects"
  find $OPENGROK_SRC_ROOT/* -maxdepth 0 -type d -print | xargs -n 1 basename | \
    while read dir; do
      echo "Adding $dir"
      # Note: URI ends with a slash
      curl -s -X POST -H 'Content-Type: text/plain' -d "$dir" "${URI}api/v1/projects"
    done
  echo "Done adding projects"
}

# Perform mirroring and indexing of all projects.
function data_sync {
  add_projects

	date +"%F %T Sync starting"
	# TODO: $URI vs sync.yml
	opengrok-sync -U "$URI" --driveon --config /scripts/sync.yml

	# Workaround for https://github.com/oracle/opengrok/issues/1670
	touch $OPENGROK_DATA_ROOT/timestamp

	date +"%F %T Sync finished"
	save_config
}

function indexer {
	wait_for_tomcat

	while true; do
		data_sync

		# If this was the case of initial indexing, move the include away.
		rm -f "$BODY_INCLUDE_FILE"
		if [[ -f $BODY_INCLUDE_FILE.orig ]]; then
			mv "$BODY_INCLUDE_FILE.orig" "$BODY_INCLUDE_FILE"
		fi

		# Want to reload the includes anyway in case the user modified them.
		curl -s -X PUT "${URI}api/v1/system/includes/reload"

		# Sync every $REINDEX minutes.
		if [ "$REINDEX" == "0" ]; then
			date +"%F %T Automatic sync disabled"
			return
		else
			date +"%F %T Automatic sync in $REINDEX minutes..."
		fi
		sleep `expr 60 \* $REINDEX`
	done
}

deploy

#
# Create empty configuration to avoid the non existent file exception
# during the first web app startup.
#
if [[ ! -f "$OPENGROK_CONFIG_FILE" ]]; then
	bare_config
fi

indexer &
catalina.sh run
