#!/bin/bash

set -e
set -x

if [[ "${TRAVIS_REPO_SLUG}" != "oracle/opengrok" ||
    "${TRAVIS_PULL_REQUEST}" != "false" ||
    "${TRAVIS_BRANCH}" != "master" ]]; then
	echo "Skipping Javadoc refresh"
	exit 0
fi

BRANCH="gh-pages"
echo -e "Publishing javadoc to $BRANCH...\n"

./mvnw -DskipTests=true site

git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"
git clone --quiet --branch=$BRANCH \
    https://${GH_PAGES_TOKEN}@github.com/oracle/opengrok "$BRANCH"

cd "$BRANCH"
if [[ -d ./javadoc ]]; then
	git rm -rf ./javadoc
fi
cp -Rf ${TRAVIS_BUILD_DIR}/target/site/apidocs ./javadoc
git add -f ./javadoc
git commit -m "Lastest javadoc auto-pushed to branch $BRANCH"
git push -fq origin "$BRANCH"

echo -e "Published Javadoc to branch $BRANCH.\n"
