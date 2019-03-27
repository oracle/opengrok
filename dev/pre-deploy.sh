#!/usr/bin/env bash

# Create distribution that will be uploaded to Github release.
mvn -DskipTests=true -Dmaven.javadoc.skip=false -B -V package
