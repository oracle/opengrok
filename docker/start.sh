#!/bin/bash

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
OPS=${INDEXER_FLAGS:='-H -P -S -G'}
BODY_INCLUDE_FILE="/opengrok/data/body_include"

function deploy {
	if [[ ! -f "/usr/local/tomcat/webapps/${WAR_NAME}" ]]; then
		date +"%F %T Deployment path does not exist. Deploying..."

		# Delete old deployment and (re)deploy.
		rm -rf /usr/local/tomcat/webapps/*
		opengrok-deploy -c /opengrok/etc/configuration.xml \
	            /opengrok/lib/source.war "/usr/local/tomcat/webapps/${WAR_NAME}"

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

	opengrok-indexer \
	    -a /opengrok/lib/opengrok.jar -- \
	    -s /opengrok/src \
	    -d /opengrok/data \
	    --remote on \
	    -W /opengrok/etc/configuration.xml \
	    --noIndex
}

function wait_for_tomcat {
	# Wait for Tomcat startup.
	date +"%F %T Waiting for Tomcat startup..."
	while [ "`curl --silent --write-out '%{response_code}' -o /dev/null \"http://localhost:8080/${URL_ROOT}\"`" == "000" ]; do
		sleep 1;
	done
	date +"%F %T Tomcat startup finished"
}

function data_sync {
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
	    -W /opengrok/etc/configuration.xml \
	    -U "$URI" \
	    $OPS \
	    $INDEXER_OPT "$@"
	date +"%F %T Indexing finished"
}

function indexer {
	wait_for_tomcat

	while true; do
		data_sync

		# If this was a case of initial indexing, move the include away.
		rm -f "$BODY_INCLUDE_FILE"
		if [[ -f $BODY_INCLUDE_FILE.orig ]]; then
			mv "$BODY_INCLUDE_FILE.orig" "$BODY_INCLUDE_FILE"
		fi

		# Index every $REINDEX minutes.
		if [ "$REINDEX" == "0" ]; then
			date +"%F %T Automatic reindexing disabled"
			return
		else
			date +"%F %T Automatic reindexing in $REINDEX minutes..."
		fi
		sleep `expr 60 \* $REINDEX`
	done
}

deploy

#
# Create empty configuration to avoid the non existent file exception
# during the first web app startup.
#
if [[ ! -f /opengrok/etc/configuration.xml ]]; then
	bare_config
fi

indexer &
catalina.sh run
