#!/bin/bash

#
# Build and optionally push new image to Docker hub.
#
# When pushing, this script uses the following secure variables:
#  - DOCKER_USERNAME
#  - DOCKER_PASSWORD
#
# These are set via https://github.com/oracle/opengrok/settings/secrets
#

set -e

API_URL="https://hub.docker.com/v2"
IMAGE="opengrok/docker"

if [[ -n $OPENGROK_REF && $OPENGROK_REF == refs/tags/* ]]; then
	OPENGROK_TAG=${OPENGROK_REF#"refs/tags/"}
fi

if [[ -n $OPENGROK_TAG ]]; then
	VERSION="$OPENGROK_TAG"
	VERSION_SHORT=$( echo $VERSION | cut -d. -f1,2 )

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
	docker build \
	    -t $IMAGE:$VERSION \
	    -t $IMAGE:$VERSION_SHORT \
	    -t $IMAGE:latest .
else
	TAGS="master"

	echo "Building docker image for master"
	docker build -t $IMAGE:master .
fi

#
# Run the image in a container. This is not strictly needed however
# serves as additional test in automatic builds.
#
echo "Running the image in container"
docker run -d $IMAGE
docker ps -a

# This can only work on home repository since it needs encrypted variables.
if [[ -n "$OPENGROK_PULL_REQUEST" ]]; then
	echo "Not pushing Docker image for pull requests"
	exit 0
fi

# The push only works on the main repository.
if [[ "$OPENGROK_REPO_SLUG" != "oracle/opengrok" ]]; then
	echo "Not pushing Docker image for non main repository"
	exit 0
fi

if [[ -z $DOCKER_USERNAME ]]; then
	echo "DOCKER_USERNAME is empty"
	exit 1
fi

if [[ -z $DOCKER_PASSWORD ]]; then
	echo "DOCKER_PASSWORD is empty"
	exit 1
fi

# Publish the image to Docker hub.
if [ -n "$DOCKER_PASSWORD" -a -n "$DOCKER_USERNAME" -a -n "$TAGS" ]; then
	echo "Logging into Docker Hub"
	echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

	# All the tags need to be pushed individually:
	for tag in $TAGS; do
		echo "Pushing Docker image for tag $tag"
		docker push $IMAGE:$tag
	done
fi

# Update README file in Docker hub.
push_readme() {
	declare -r image="${1}"
	declare -r token="${2}"
	declare -r input_file="${3}"

	if [[ ! -r $input_file ]]; then
		echo "file $input_file is not readable"
		exit 1
	fi

	local code=$(curl -s -o /dev/null -L -w "%{http_code}" \
	           -X PATCH --data-urlencode \
		   full_description@${input_file} \
	           -H "Authorization: JWT ${token}" \
	           ${API_URL}/repositories/"${image}"/)

	if [[ "${code}" = "200" ]]; then
		echo "Successfully pushed README to Docker Hub"
	else
		printf "Unable to push README to Docker Hub, response code: %s\n" "${code}"
		exit 1
	fi
}

# Update README and badge only for release builds.
if [[ -n $OPENGROK_TAG ]]; then
	TOKEN=$(curl -s -H "Content-Type: application/json" -X POST \
	    -d '{"username": "'${DOCKER_USERNAME}'", "password": "'${DOCKER_PASSWORD}'"}' \
	    ${API_URL}/users/login/ | jq -r .token)
	if [[ -z $TOKEN ]]; then
		echo "Cannot get auth token to publish the README file"
		exit 1
	fi

	push_readme "${IMAGE}" "${TOKEN}" "docker/README.md"
fi
