#!/bin/bash

set -e
set -x

if [[ "${TRAVIS_REPO_SLUG}" != "oracle/opengrok" ||
    "${TRAVIS_PULL_REQUEST}" != "false" ||
    "${TRAVIS_BRANCH}" != "master" ]]; then
	echo "Skipping Javadoc refresh"
	exit 0
fi

echo -e "Publishing javadoc...\n"

./mvnw -DskipTests=true site

git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"
git clone --quiet --branch=gh-pages \
    https://${GH_PAGES_TOKEN}@github.com/oracle/opengrok gh-pages

cd gh-pages
if [[ -d ./javadoc ]]; then
	git rm -rf ./javadoc
fi
cp -Rf ${TRAVIS_BUILD_DIR}/target/site/apidocs ./javadoc
git add -f ./javadoc
git commit -m "Lastest javadoc auto-pushed to gh-pages"
git push -fq origin gh-pages

echo -e "Published Javadoc to gh-pages.\n"
