#!/usr/bin/env bash

# Create distribution that will be uploaded to Github release.
./mvnw -DskipTests=true -Dmaven.javadoc.skip=false -B -V package
