#!/usr/bin/env bash

# Create distribution that will be uploaded to Github release.
mvn -DskipTests=true -Dmaven.javadoc.skip=false -B -V package

# Trigger Travis build of the OpenGrok/docker repository.
echo "Triggering Travis build of OpenGrok/docker repository"

body='{
"request": {
"branch":"master"
}}'

if [ -n "$TRAVIS_TOKEN" ]; then
	curl -s -X POST \
	   -H "Content-Type: application/json" \
	   -H "Accept: application/json" \
	   -H "Travis-API-Version: 3" \
	   -H "Authorization: token $TRAVIS_TOKEN" \
	   -d "$body" \
	   https://api.travis-ci.com/repo/OpenGrok%2Fdocker/requests
fi
