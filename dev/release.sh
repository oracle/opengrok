#!/bin/bash
#
# Trigger new release creation on Github.
# Assumes working Maven + Git.
#
# see https://github.com/oracle/opengrok/wiki/Release-process
#

set -e

if (( $# > 1 )); then
	echo "usage: `basename $0` [version]"
	exit 1
fi

# Get the latest version (needs curl + jq).
if (( $# == 0 )); then
	curl -s https://api.github.com/repos/oracle/opengrok/releases/latest | \
	    jq .tag_name
	exit 0
fi

VERSION=$1

if ! echo "$VERSION" | grep '^[0-9]\+\.[0-9]\+\.[0-9]\+$' >/dev/null; then
	echo "version needs to be in the form of <num>.<num>.<num>"
	exit 1
fi

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

git pull --ff-only
./mvnw versions:set -DgenerateBackupPoms=false "-DnewVersion=$VERSION"
git commit pom.xml '**/pom.xml' -m "$VERSION"
git push
git tag "$VERSION"
git push origin tag "$VERSION"
