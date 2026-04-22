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
# Run the image in a container with comprehensive smoke tests.
# This validates basic functionality and catches common configuration errors.
#
echo "======================================"
echo "Running Docker image smoke tests"
echo "======================================"

# Determine which tag to test
if [[ -n $OPENGROK_TAG ]]; then
    TEST_IMAGE="$IMAGE:latest"
else
    TEST_IMAGE="$IMAGE:master"
fi
echo "Testing image: $TEST_IMAGE"

# Create temporary directories for volumes (required by entrypoint.sh)
TEST_SRC_DIR=$(mktemp -d)
TEST_DATA_DIR=$(mktemp -d)
echo "Created test directories:"
echo "  Source: $TEST_SRC_DIR"
echo "  Data:   $TEST_DATA_DIR"

# Create a simple test file in source directory
echo "// Test source file" > "$TEST_SRC_DIR/test.java"

# Run container with proper volume mounts
echo ""
echo "Starting container with volume mounts..."
CONTAINER_ID=$(docker run -d \
    -v "$TEST_SRC_DIR:/opengrok/src" \
    -v "$TEST_DATA_DIR:/opengrok/data" \
    "$TEST_IMAGE")

if [ -z "$CONTAINER_ID" ]; then
    echo "ERROR: Failed to start container"
    rm -rf "$TEST_SRC_DIR" "$TEST_DATA_DIR"
    exit 1
fi

echo "Container started: $CONTAINER_ID"
docker ps -a

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "Cleaning up..."
    docker stop "$CONTAINER_ID" >/dev/null 2>&1 || true
    docker rm "$CONTAINER_ID" >/dev/null 2>&1 || true
    rm -rf "$TEST_SRC_DIR" "$TEST_DATA_DIR"
    echo "Cleanup complete"
}
trap cleanup EXIT

# Wait for container to be ready (max 90 seconds)
echo ""
echo "Waiting for container to be ready..."
MAX_WAIT=90
WAITED=0
READY=false

while [ $WAITED -lt $MAX_WAIT ]; do
    # Check if container is still running
    if ! docker ps -q --no-trunc | grep -q "$CONTAINER_ID"; then
        echo "ERROR: Container stopped unexpectedly"
        echo "Container logs:"
        docker logs "$CONTAINER_ID"
        exit 1
    fi

    # Check for Tomcat startup completion
    if docker logs "$CONTAINER_ID" 2>&1 | grep -q "Server startup in"; then
        READY=true
        break
    fi

    sleep 3
    WAITED=$((WAITED + 3))
    echo "  Waited ${WAITED}s..."
done

if [ "$READY" = false ]; then
    echo "ERROR: Container did not start within ${MAX_WAIT}s"
    echo "Container logs:"
    docker logs "$CONTAINER_ID"
    exit 1
fi

echo "✓ Container is ready! (took ${WAITED}s)"

# Check for errors in logs
echo ""
echo "Checking for errors in container logs..."
ERROR_COUNT=$(docker logs "$CONTAINER_ID" 2>&1 | grep -c -iE "^ERROR|^FATAL" || true)
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo "WARNING: Found $ERROR_COUNT error/fatal messages in logs"
    docker logs "$CONTAINER_ID" 2>&1 | grep -iE "^ERROR|^FATAL" || true
    # Don't fail the build for warnings, but show them
else
    echo "✓ No errors found in logs"
fi

# Get container IP for testing
CONTAINER_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER_ID")
echo ""
echo "Container IP: $CONTAINER_IP"

# Test web interface (port 8080)
echo ""
echo "Testing web interface on port 8080..."
if command -v curl >/dev/null 2>&1; then
    if [ -n "$CONTAINER_IP" ]; then
        # Try up to 3 times with 5 second delays
        WEB_SUCCESS=false
        for i in 1 2 3; do
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 "http://${CONTAINER_IP}:8080/" || echo "000")
            if [ "$HTTP_CODE" = "200" ]; then
                echo "✓ Web interface is accessible (HTTP $HTTP_CODE)"
                WEB_SUCCESS=true
                break
            else
                echo "  Attempt $i: HTTP $HTTP_CODE, retrying..."
                sleep 5
            fi
        done

        if [ "$WEB_SUCCESS" = false ]; then
            echo "WARNING: Web interface is not accessible after 3 attempts (HTTP $HTTP_CODE)"
        fi
    else
        echo "WARNING: Could not determine container IP"
    fi
else
    echo "SKIPPED: curl not available"
fi

# Test REST API (port 5000)
echo ""
echo "Testing REST API on port 5000..."
if [ -n "$CONTAINER_IP" ] && command -v curl >/dev/null 2>&1; then
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 "http://${CONTAINER_IP}:5000/" || echo "000")
    if [ "$HTTP_CODE" != "000" ]; then
        echo "✓ REST API is responding (HTTP $HTTP_CODE)"
    else
        echo "WARNING: REST API not accessible"
    fi
else
    echo "SKIPPED: curl not available or no container IP"
fi

# Verify volumes are writable
echo ""
echo "Checking volume mounts..."
if docker exec "$CONTAINER_ID" test -w /opengrok/src && \
   docker exec "$CONTAINER_ID" test -w /opengrok/data; then
    echo "✓ Volume mounts are writable"
else
    echo "ERROR: Volume mounts are not writable"
    exit 1
fi

# Check file ownership (should be appuser:appgroup)
echo ""
echo "Checking file ownership..."
SRC_OWNER=$(docker exec "$CONTAINER_ID" stat -c '%U:%G' /opengrok/src 2>/dev/null || echo "unknown")
DATA_OWNER=$(docker exec "$CONTAINER_ID" stat -c '%U:%G' /opengrok/data 2>/dev/null || echo "unknown")
echo "  /opengrok/src owner: $SRC_OWNER"
echo "  /opengrok/data owner: $DATA_OWNER"

if [ "$SRC_OWNER" = "appuser:appgroup" ] && [ "$DATA_OWNER" = "appuser:appgroup" ]; then
    echo "✓ File ownership is correct"
else
    echo "WARNING: File ownership may be incorrect (expected appuser:appgroup)"
fi

echo ""
echo "======================================"
echo "✓ All smoke tests passed!"
echo "======================================"

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
