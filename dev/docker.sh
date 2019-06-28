#!/bin/bash

#
# Build and push new image to Docker hub.
#
# Uses the following Travis secure variables:
#  - DOCKER_USERNAME
#  - DOCKER_PASSWORD
#  - GITHUB_TOKEN
#
# These are set via https://travis-ci.com/OpenGrok/docker/settings
#

set -x
set -e

# Travis can only work on master since it needs encrypted variables.
if [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
	exit 0
fi

JSON_OUT="ver.out"

#
# Get the latest OpenGrok version string. Use authenticated request to avoid
# rate limiting induced errors.
#
curl -sS -o "$JSON_OUT" \
    -H "Authorization: token $GITHUB_TOKEN" \
    https://api.github.com/repos/oracle/opengrok/releases/latest
cat "$JSON_OUT"
VERSION=`jq -er .tag_name ver.out`
echo "Latest OpenGrok tag: $VERSION"

# Embed the tarball URL into the Dockerfile.
tarball=`jq -er '.assets[]|select(.name|test("opengrok-.*tar.gz"))|.browser_download_url' "$JSON_OUT"`
echo "Tarball URL: $tarball"
sed "s%OPENGROK_DOWNLOAD_LINK%$tarball%" Dockerfile.tmpl > Dockerfile

# Build and run the image in container.
docker build -t opengrok/docker:$VERSION -t opengrok/docker:latest .
docker run -d opengrok/docker
docker ps -a

# Publish the image to Docker hub.
if [ -n "$DOCKER_PASSWORD" -a -n "$DOCKER_USERNAME" -a -n "$VERSION" ]; then
	echo "Pushing image for version $VERSION"
	echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
	docker push opengrok/docker:$VERSION
	docker push opengrok/docker:latest
fi
