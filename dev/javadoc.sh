#!/bin/bash

set -e
set -x

if [[ -n $OPENGROK_REF && $OPENGROK_REF == refs/heads/* ]]; then
	OPENGROK_BRANCH=${OPENGROK_REF#"refs/heads/"}
fi

if [[ "${OPENGROK_REPO_SLUG}" != "oracle/opengrok" ||
    -n "${OPENGROK_PULL_REQUEST}" ||
    "${OPENGROK_BRANCH}" != "master" ]]; then
	echo "Skipping Javadoc refresh"
	exit 0
fi

BRANCH="gh-pages"

echo -e "Building Javadoc...\n"
./mvnw -DskipTests=true site

echo -e "Publishing javadoc to $BRANCH...\n"
git config --global user.email "noreply@github.com"
git config --global user.name "Foo Bar"
git clone --quiet --branch=$BRANCH \
    https://github.com/oracle/opengrok "$BRANCH"

cd "$BRANCH"
if [[ -d ./javadoc ]]; then
	git rm -rf ./javadoc
fi
cp -Rf ${OPENGROK_BUILD_DIR}/target/site/apidocs ./javadoc
git add -f ./javadoc
git commit -m "Lastest javadoc auto-pushed to branch $BRANCH"
git push -fq origin "$BRANCH"

echo -e "Published Javadoc to branch $BRANCH.\n"
