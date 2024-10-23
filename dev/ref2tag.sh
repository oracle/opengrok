#!/bin/bash

tag=${OPENGROK_REF#"refs/tags/"}
echo "tag=$tag" >> $GITHUB_OUTPUT
