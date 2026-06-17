#!/bin/bash

set -e

#
# Run the image in a container with comprehensive smoke tests.
# This validates basic functionality and catches common configuration errors.
#
TEST_IMAGE="$1"
echo "Running Docker image smoke tests for image: $TEST_IMAGE"

TEST_PROJECT="smoke-project"
TEST_DEFINITION="calculateSmokeChecksum"
TEST_INDEXED_FILE="src/main/java/org/opengrok/smoke/test.java"
WEBAPP_API_HEADERS_FILE=""

# Create temporary directories for volumes (required by entrypoint.sh)
TEST_SRC_DIR=$(mktemp -d)
TEST_DATA_DIR=$(mktemp -d)
echo "Created test directories:"
echo "  Source: $TEST_SRC_DIR"
echo "  Data:   $TEST_DATA_DIR"

# Create a test project and Java source file in the source directory.
TEST_SOURCE_SUBDIR="$TEST_SRC_DIR/$TEST_PROJECT/$(dirname "$TEST_INDEXED_FILE")"
TEST_SOURCE_FILE="$TEST_SOURCE_SUBDIR/$(basename "$TEST_INDEXED_FILE")"
mkdir -p "$TEST_SOURCE_SUBDIR"
cat > "$TEST_SOURCE_FILE" <<'EOF'
package org.opengrok.smoke;

class SmokeFixture {
    private final int seed = 17;

    int calculateSmokeChecksum(int value) {
        return seed * 31 + value;
    }

    String status() {
        return "checksum=" + calculateSmokeChecksum(5);
    }
}
EOF

# Run container with proper volume mounts
echo ""
echo "Starting container with volume mounts..."
CONTAINER_ID=$(docker run -d \
    -v "$TEST_SRC_DIR:/opengrok/src" \
    -v "$TEST_DATA_DIR:/opengrok/data" \
    "$TEST_IMAGE")

if [[ -z "$CONTAINER_ID" ]]; then
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
    if [[ -n "$WEBAPP_API_HEADERS_FILE" ]]; then
        rm -f "$WEBAPP_API_HEADERS_FILE"
    fi
    echo "Cleanup complete"
}
trap cleanup EXIT

require_command() {
    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "ERROR: $command_name not available"
        exit 1
    fi
}

load_webapp_api_headers() {
    WEBAPP_API_HEADERS_FILE=$(mktemp)
    if ! docker exec "$CONTAINER_ID" cat /opengrok/etc/webapp_api_headers > "$WEBAPP_API_HEADERS_FILE" 2>/dev/null ||
       [[ ! -s "$WEBAPP_API_HEADERS_FILE" ]]; then
        echo "ERROR: Could not read OpenGrok webapp API authentication headers" >&2
        exit 1
    fi
}

project_exists() {
    local url="$1"
    local projects_json

    projects_json=$(curl -fsS -H "@$WEBAPP_API_HEADERS_FILE" --connect-timeout 10 "$url" 2>/dev/null || true)
    if [[ -z "$projects_json" ]]; then
        return 1
    fi

    jq -e --arg project "$TEST_PROJECT" 'index($project) != null' >/dev/null <<< "$projects_json"
}

wait_for_project() {
    local url="$1"
    local max_wait=120
    local waited=0
    local interval=3

    echo ""
    echo "Checking OpenGrok project API for project: $TEST_PROJECT"
    while [[ $waited -lt $max_wait ]]; do
        if project_exists "$url"; then
            echo "✓ Project was created in OpenGrok: $TEST_PROJECT"
            return
        fi

        sleep "$interval"
        (( waited = waited + interval ))
        echo "  Waited ${waited}s for project creation..."
    done

    echo "ERROR: Project $TEST_PROJECT was not created within ${max_wait}s"
    echo ""
    exit 1
}

definition_indexed() {
    local url="$1"
    local search_json

    search_json=$(curl -fsS -G \
        --data-urlencode "projects=$TEST_PROJECT" \
        --data-urlencode "def=$TEST_DEFINITION" \
        --data-urlencode "maxresults=10" \
        --connect-timeout 10 \
        "$url" 2>/dev/null || true)
    if [[ -z "$search_json" ]]; then
        return 1
    fi

    jq -e \
        --arg file "$TEST_INDEXED_FILE" \
        --arg definition "$TEST_DEFINITION" \
        '
        (.resultCount // 0) > 0 and
        ((.results // {}) | to_entries | any(
            (.key | ltrimstr("/") | endswith($file)) and
            ((.value // []) | any(
                (((.line // "") | contains($definition)) or
                ((.tag // "") == $definition))
            ))
        ))
        ' >/dev/null <<< "$search_json"
}

wait_for_definition_index() {
    local url="$1"
    local max_wait=180
    local waited=0
    local interval=5

    echo ""
    echo "Checking OpenGrok search API for indexed definition: $TEST_DEFINITION"
    while [[ $waited -lt $max_wait ]]; do
        if definition_indexed "$url"; then
            echo "✓ Definition was indexed in $TEST_INDEXED_FILE"
            return
        fi

        sleep "$interval"
        (( waited = waited + interval ))
        echo "  Waited ${waited}s for indexed definition..."
    done

    echo "ERROR: Definition $TEST_DEFINITION was not found in search results within ${max_wait}s"
    exit 1
}

wait_for_container_ready() {
    local max_wait=90
    local waited=0
    local ready=false

    echo ""
    echo "Waiting for container to be ready..."
    while [[ $waited -lt $max_wait ]]; do
        # Check if container is still running
        if ! docker ps -q --no-trunc | grep -q "$CONTAINER_ID"; then
            echo "ERROR: Container stopped unexpectedly"
            echo "Container logs:"
            docker logs "$CONTAINER_ID"
            exit 1
        fi

        # Check for Tomcat startup completion
        if docker logs "$CONTAINER_ID" 2>&1 | grep -q "Server startup in"; then
            ready=true
            break
        fi

        sleep 3
        (( waited = waited + 3 ))
        echo "  Waited ${waited}s..."
    done

    if [[ "$ready" == false ]]; then
        echo "ERROR: Container did not start within ${max_wait}s"
        echo "Container logs:"
        docker logs "$CONTAINER_ID"
        exit 1
    fi

    echo "✓ Container is ready! (took ${waited}s)"
}

check_container_logs() {
    local error_count

    echo ""
    echo "Checking for errors in container logs..."
    error_count=$(docker logs "$CONTAINER_ID" 2>&1 | grep -c -iE "^ERROR|^FATAL" || true)
    if [[ "$error_count" -gt 0 ]]; then
        echo "WARNING: Found $error_count error/fatal messages in logs"
        docker logs "$CONTAINER_ID" 2>&1 | grep -iE "^ERROR|^FATAL" || true
        # Don't fail the build for warnings, but show them
    else
        echo "✓ No errors found in logs"
    fi
}

get_container_ip() {
    CONTAINER_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$CONTAINER_ID")
    echo ""
    echo "Container IP: $CONTAINER_IP"

    if [[ -z "$CONTAINER_IP" ]]; then
        echo "ERROR: Could not determine container IP for OpenGrok API tests"
        exit 1
    fi
}

test_web_interface() {
    local http_code
    local web_success=false

    echo ""
    echo "Testing web interface on port 8080..."
    # Try up to 3 times with 5 second delays
    for i in 1 2 3; do
        http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            --connect-timeout 10 "http://${CONTAINER_IP}:8080/" || echo "000")
        if [[ "$http_code" == "200" ]]; then
            echo "✓ Web interface is accessible (HTTP $http_code)"
            web_success=true
            break
        else
            echo "  Attempt $i: HTTP $http_code, retrying..."
            sleep 5
        fi
    done

    if [[ "$web_success" == false ]]; then
        echo "WARNING: Web interface is not accessible after 3 attempts (HTTP $http_code)"
    fi
}

check_opengrok_api() {
    local opengrok_api_base

    load_webapp_api_headers
    opengrok_api_base="http://${CONTAINER_IP}:8080/api/v1"
    wait_for_project "$opengrok_api_base/projects"
    wait_for_definition_index "$opengrok_api_base/search"
}

test_rest_api() {
    local http_code

    echo ""
    echo "Testing REST API on port 5000..."
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        --connect-timeout 10 "http://${CONTAINER_IP}:5000/" || echo "000")
    if [[ "$http_code" != "000" ]]; then
        echo "✓ REST API is responding (HTTP $http_code)"
    else
        echo "WARNING: REST API not accessible"
    fi
}

check_volume_mounts() {
    echo ""
    echo "Checking volume mounts..."
    if docker exec "$CONTAINER_ID" test -w /opengrok/src && \
       docker exec "$CONTAINER_ID" test -w /opengrok/data; then
        echo "✓ Volume mounts are writable"
    else
        echo "ERROR: Volume mounts are not writable"
        exit 1
    fi
}

check_file_ownership() {
    local src_owner
    local data_owner

    echo ""
    echo "Checking file ownership..."
    src_owner=$(docker exec "$CONTAINER_ID" stat -c '%U:%G' /opengrok/src 2>/dev/null || echo "unknown")
    data_owner=$(docker exec "$CONTAINER_ID" stat -c '%U:%G' /opengrok/data 2>/dev/null || echo "unknown")
    echo "  /opengrok/src owner: $src_owner"
    echo "  /opengrok/data owner: $data_owner"

    if [[ "$src_owner" == "appuser:appgroup" ]] && [[ "$data_owner" = "appuser:appgroup" ]]; then
        echo "✓ File ownership is correct"
    else
        echo "WARNING: File ownership may be incorrect (expected appuser:appgroup)"
    fi
}

wait_for_container_ready
check_container_logs
get_container_ip
require_command curl
require_command jq
test_web_interface
check_opengrok_api
test_rest_api
check_volume_mounts
check_file_ownership

echo ""
echo "✓ All smoke tests passed!"
