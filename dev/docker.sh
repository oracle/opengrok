#!/bin/bash

#
# Build and optionally push new image to Docker hub.
#
# When pushing, this script uses the following secure variables:
#  - DOCKER_USERNAME
#  - DOCKER_PAT
#
# These are set via https://github.com/oracle/opengrok/settings/secrets
#

set -e

echo "Running linter"
docker run --rm -i hadolint/hadolint:2.6.0 < Dockerfile || exit 1

IMAGE="opengrok/docker"

if [[ -n $OPENGROK_REF && $OPENGROK_REF == refs/tags/* ]]; then
	OPENGROK_TAG=${OPENGROK_REF#"refs/tags/"}
fi

if [[ -n $OPENGROK_TAG ]]; then
	VERSION="$OPENGROK_TAG"
	VERSION_SHORT=$( echo "$VERSION" | cut -d. -f1,2 )

	if [[ -z $VERSION ]]; then
		echo "empty VERSION"
		exit 1
	fi

	if [[ -z $VERSION_SHORT ]]; then
		echo "empty VERSION_SHORT"
		exit 1
	fi

	echo "Version: $VERSION"
	echo "Short version: $VERSION_SHORT"

	TAGS="$VERSION $VERSION_SHORT latest"

	echo "Building docker image for release ($TAGS)"
	docker buildx build \
	    -t "$IMAGE:$VERSION" \
	    -t "$IMAGE:$VERSION_SHORT" \
	    -t "$IMAGE:latest" .
else
	TAGS="master"

	echo "Building docker image for master"
	docker buildx build -t $IMAGE:master .
fi

#
# Run the image in a container. This is not strictly needed however
# serves as additional test in automatic builds.
#
echo "Running the image in container"
docker run -d $IMAGE
docker ps -a

# This can only work on home repository since it needs encrypted variables.
if [[ "$GITHUB_EVENT_NAME" == "pull_request" ]]; then
	echo "Not pushing Docker image for pull requests"
	exit 0
fi

# The push only works on the main repository.
if [[ "$OPENGROK_REPO_SLUG" != "oracle/opengrok" ]]; then
	echo "Not pushing Docker image for non main repository"
	exit 0
fi

if [[ -z $DOCKER_USERNAME ]]; then
	echo "DOCKER_USERNAME is empty, exiting"
	exit 1
fi

if [[ -z $DOCKER_PAT ]]; then
	echo "DOCKER_PAT is empty, exiting"
	exit 1
fi

# Publish the image to Docker hub.
if [ -n "$DOCKER_PAT" -a -n "$DOCKER_USERNAME" -a -n "$TAGS" ]; then
	echo "Logging into Docker Hub"
	echo "$DOCKER_PAT" | docker login -u "$DOCKER_USERNAME" --password-stdin

	# All the tags need to be pushed individually:
	for tag in $TAGS; do
		echo "Pushing Docker image for tag $tag"
		docker push "$IMAGE:$tag"
	done
fi
