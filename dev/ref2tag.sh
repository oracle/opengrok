#!/bin/bash

tag=${OPENGROK_REF#"refs/tags/"}
echo "::set-output name=tag::$tag"
