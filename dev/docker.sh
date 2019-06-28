#!/bin/bash

#
# Build and push new image to Docker hub.
#
# Uses the following Travis secure variables:
#  - DOCKER_USERNAME
#  - DOCKER_PASSWORD
#
# These are set via https://travis-ci.com/OpenGrok/docker/settings
#

set -x
set -e

# Travis can only work on master since it needs encrypted variables.
if [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
	print "Not build docker image for pull requests"
	exit 0
fi

# Allow Docker builds for release builds only.
if [[ -z $TRAVIS_TAG ]]; then
	print "TRAVIS_TAG is empty"
	exit 0
fi

VERSION="$TRAVIS_TAG"

# Build the image.
docker build -t opengrok/docker:$VERSION -t opengrok/docker:latest .
#
# Run the image in container. This is not strictly needed however
# serves as additional test in automatic builds.
#
docker run -d opengrok/docker
docker ps -a

# Publish the image to Docker hub.
if [ -n "$DOCKER_PASSWORD" -a -n "$DOCKER_USERNAME" -a -n "$VERSION" ]; then
	echo "Logging into docker"
	echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

	# All the tags need to be pushed individually:
	echo "Pushing docker image for tag $VERSION"
	docker push opengrok/docker:$VERSION
	echo "Pushing docker image for tag latest"
	docker push opengrok/docker:latest
fi
