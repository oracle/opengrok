#!/bin/bash

#
# Build and optionally push new image to Docker hub.
#
# When pushing, this script uses the following Travis secure variables:
#  - DOCKER_USERNAME
#  - DOCKER_PASSWORD
#
# These are set via https://travis-ci.com/OpenGrok/docker/settings
#

set -x
set -e

IMAGE="opengrok/docker"

if [[ -n $TRAVIS_TAG ]]; then
	VERSION="$TRAVIS_TAG"
	VERSION_SHORT=$( echo $VERSION | cut -d. -f1,2 )
else
	VERSION="latest"
	VERSION_SHORT="latest"
fi

if [[ -z $VERSION ]]; then
	echo "empty VERSION"
	exit 1
fi

if [[ -z $VERSION_SHORT ]]; then
	echo "empty VERSION_SHORT"
	exit 1
fi

# Build the image.
docker build \
    -t $IMAGE:$VERSION \
    -t $IMAGE:$VERSION_SHORT \
    -t $IMAGE:latest .

#
# Run the image in container. This is not strictly needed however
# serves as additional test in automatic builds.
#
docker run -d $IMAGE
docker ps -a

# Travis can only work on master since it needs encrypted variables.
if [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
	echo "Not pushing Docker image for pull requests"
	exit 0
fi

# The push only works on the main repository.
if [[ "${TRAVIS_REPO_SLUG}" != "oracle/opengrok" ]]; then
	echo "Not pushing Docker image for non main repository"
	exit 0
fi

# Allow Docker push for release builds only.
if [[ -z $TRAVIS_TAG ]]; then
	echo "TRAVIS_TAG is empty"
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
if [ -n "$DOCKER_PASSWORD" -a -n "$DOCKER_USERNAME" -a -n "$VERSION" ]; then
	echo "Logging into Docker Hub"
	echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

	# All the tags need to be pushed individually:
	for tag in $VERSION $VERSION_SHORT latest; do
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

	local code=$(jq -n --arg msg "$(<${input_file})" \
	    '{"registry":"registry-1.docker.io","full_description": $msg }' | \
	        curl -s -o /dev/null  -L -w "%{http_code}" \
	           https://cloud.docker.com/v2/repositories/"${image}"/ \
	           -d @- -X PATCH \
	           -H "Content-Type: application/json" \
	           -H "Authorization: JWT ${token}")

	if [[ "${code}" = "200" ]]; then
		echo "Successfully pushed README to Docker Hub"
	else
		printf "Unable to push README to Docker Hub, response code: %s\n" "${code}"
		exit 1
	fi
}

TOKEN=$(curl -s -H "Content-Type: application/json" -X POST \
    -d '{"username": "'${DOCKER_USERNAME}'", "password": "'${DOCKER_PASSWORD}'"}' \
    https://hub.docker.com/v2/users/login/ | jq -r .token)
if [[ -z $TOKEN ]]; then
	echo "Cannot get auth token to publish the README file"
	exit 1
fi

push_readme "${IMAGE}" "${TOKEN}" "docker/README.md"
