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

if [ ! -f "/usr/local/tomcat/webapps/${WAR_NAME}" ]; then
	date +"%F %T Deployment path changed. Redeploying..."

	# Delete old deployment and (re)deploy
	rm -rf /usr/local/tomcat/webapps/*
	opengrok-deploy -c /opengrok/etc/configuration.xml \
            /opengrok/lib/source.war "/usr/local/tomcat/webapps/${WAR_NAME}"

	# Set up redirect from /source
	mkdir "/usr/local/tomcat/webapps/source"
	echo "<% response.sendRedirect(\"/${URL_ROOT}\"); %>" > "/usr/local/tomcat/webapps/source/index.jsp"
fi

indexer(){
	# Wait for Tomcat startup.
	date +"%F %T Waiting for Tomcat startup..."
	while [ "`curl --silent --write-out '%{response_code}' -o /dev/null \"http://localhost:8080/${URL_ROOT}\"`" == "000" ]; do
		sleep 1;
	done
	date +"%F %T Startup finished"

	if [[ ! -d /opengrok/data/index ]]; then
		# Populate the webapp with bare configuration.
		BODY_INCLUDE_FILE="/opengrok/data/body_include"
		if [[ -f $BODY_INCLUDE_FILE ]]; then
			mv "$BODY_INCLUDE_FILE" "$BODY_INCLUDE_FILE.orig"
		fi
		echo '<p><h1>Waiting on the initial reindex to finish.. Stay tuned !</h1></p>' > "$BODY_INCLUDE_FILE"
		/scripts/index.sh --noIndex
		rm -f "$BODY_INCLUDE_FILE"
		if [[ -f $BODY_INCLUDE_FILE.orig ]]; then
			mv "$BODY_INCLUDE_FILE.orig" "$BODY_INCLUDE_FILE"
		fi

		# Perform initial indexing.
		NOMIRROR=1 /scripts/index.sh
		date +"%F %T Initial reindex finished"
	fi

	# Continue to index every $REINDEX minutes.
	if [ "$REINDEX" == "0" ]; then
		date +"%F %T Automatic reindexing disabled"
		return
	else
		date +"%F %T Automatic reindexing in $REINDEX minutes..."
	fi
	while true; do
		sleep `expr 60 \* $REINDEX`
		/scripts/index.sh
	done
}

# Start all necessary services.
indexer &
catalina.sh run
