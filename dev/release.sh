#!/bin/bash
#
# Trigger new release creation on Github.
# Assumes working Maven + Git.
#
# see https://github.com/OpenGrok/opengrok/wiki/Release-process
#

if (( $# != 1 )); then
	echo "usage: `basename $0` <version>"
	exit 1
fi

VERSION=$1

if [[ ! -d $PWD/opengrok-indexer ]]; then
	echo "This needs to be run from top-level directory of the repository"
	exit 1
fi

ver=$( git tag -l "$VERSION" )
if (( $? != 0 )); then
	echo "Cannot determine tag"
	exit 1
fi
if [[ $ver == $VERSION ]]; then
	echo "Tag $VERSION already exists"
	exit 1
fi

git pull --ff-only && \
    mvn versions:set -DgenerateBackupPoms=false -DnewVersion=$VERSION && \
    git commit pom.xml **/pom.xml -m $VERSION && \
    git push && \
    git tag $VERSION
    git push origin tag $VERSION
