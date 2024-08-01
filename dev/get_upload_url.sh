#!/bin/bash
#
# The purpose of this script is to retrieve upload URL for OpenGrok release given by the tag
# stored in the OPENGROK_TAG environment variable.
# The value is stored in a special file consumed by Github action so that it can be used
# to upload assets to the related OpenGrok release on Github.
#

echo "Getting upload URL for $OPENGROK_TAG"
upload_url=$( curl -s https://api.github.com/repos/oracle/opengrok/releases/$OPENGROK_TAG | jq -r .upload_url )
echo "Got $upload_url"
echo "upload_url=$upload_url" >> $GITHUB_OUTPUT"
