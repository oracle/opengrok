#!/bin/bash

# default period for reindexing (in minutes)
if [ -z "$REINDEX" ]; then
	REINDEX=10
fi

indexer(){
	# Wait for Tomcat startup.
	date +"%F %T Waiting for Tomcat startup..."
	while [ "`curl --silent --write-out '%{response_code}' -o /dev/null 'http://localhost:8080/'`" == "000" ]; do
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
