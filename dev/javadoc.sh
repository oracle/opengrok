#!/bin/bash

set -e
set -x

if [[ -z "$OPENGROK_BUILD_DIR" ]]; then
	echo -e "empty OPENGROK_BUILD_DIR"
	exit 1
fi

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
git config --global user.name "github-actions[bot]"
git config --global user.email "41898282+github-actions[bot]@users.noreply.github.com"

cd "$BRANCH"
if [[ -d ./javadoc ]]; then
	git rm -rf ./javadoc
fi
cp -Rf "$OPENGROK_BUILD_DIR/target/site/apidocs" ./javadoc
git add -f ./javadoc
git commit -m "Lastest javadoc auto-pushed to branch $BRANCH"
git push -fq origin "$BRANCH"

echo -e "Published Javadoc to branch $BRANCH.\n"
