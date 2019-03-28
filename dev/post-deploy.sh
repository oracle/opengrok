#!/bin/bash
#
# Trigger Travis build of the OpenGrok/docker repository.
#

body='{
  "request": {
    "branch":"master",
    "message": "build triggered by API request"
  }
}'

if [ -n "$TRAVIS_TOKEN" ]; then
	echo "Triggering Travis build of OpenGrok/docker repository"

	curl -s -X POST \
	   -H "Content-Type: application/json" \
	   -H "Accept: application/json" \
	   -H "Travis-API-Version: 3" \
	   -H "Authorization: token $TRAVIS_TOKEN" \
	   -d "$body" \
	   https://api.travis-ci.com/repo/OpenGrok%2Fdocker/requests
fi
