#!/usr/bin/env python

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
# or http://www.opensolaris.org/os/licensing.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at usr/src/OPENSOLARIS.LICENSE.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END

#
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
#

'''
Create release using Github v3 API

This is supposed to be no-nonsense one-purpose script that:
  - is standalone, i.e. runs without any non-Python-core modules
  - does HTTP basic authentication for Github user
  - times out after some time of inactivity
  - asks for password of the user interactively
    or gets is from environment variable (no command line passwords !)
  - support HTTPS proxy
  - creates new (pre)release and uploads bunch of files
  - works on Python >= 2.6

Vladimir Kotal

'''

try:
    # Python 2
    from urllib2 import build_opener, ProxyHandler, HTTPSHandler, Request, HTTPError
except:
    # Python 3
    from urllib.request import build_opener, ProxyHandler, HTTPSHandler, HTTPError, Request

import os, sys, json
import logging
import argparse
from getpass import getpass
import base64
from pprint import pprint
from string import split

_files = []
debug = False
logger = logging.getLogger("release")

class MyError(Exception):
    def __init__(self, value):
        self.value = value
    def __str__(self):
        return repr(self.value)

def _process_response(headers):
    '''
    Go through HTTP headers and determine if the content type is JSON.
    This does not yet mean it is valid (parseable) JSON.
    '''
    is_json = False
    if headers:
        for hdr in headers:
             #
             # This comparison is not robust for special characters however
             # for HTTP headers it should work fine.
             #
             if hdr.lower() == 'content-type':
                 is_json = headers[hdr].startswith('application/json')

    return is_json


def get_release_dict(tag, description, prerelease=False):
    _release_dict = {
         "draft" : False,
         "target_commitish" : "master"
    }

    _release_dict["prerelease"] = prerelease
    _release_dict["tag_name"] = tag
    _release_dict["name"] = tag
    _release_dict["body"] = description

    return _release_dict

def _get_auth(username, password):
    userandpass = base64.b64encode(bytes('%s:%s' % (username, password)))
    userandpass = userandpass.decode('ascii')

    return 'Basic %s' % userandpass

def get_args():
    parser = argparse.ArgumentParser(
        description='Create release using Github API.')
    parser.add_argument("-d", "--debug", help="turn on debugging",
	    action="store_true")
    parser.add_argument("-n", "--dryrun",
        help="perform dry run (also can use DO_DRYRUN env var)",
	    action="store_true")
    parser.add_argument("-u", "--user", nargs=1, metavar='user',
        help="GitHub user. Specify user with GITHUB_USER and password with GITHUB_PASSWORD env var.")
    parser.add_argument("-D", "--description", nargs=1,
        metavar='description', required=True,
        help="Description text for the release")
    parser.add_argument("-P", "--prerelease",
        default = False, action="store_true",
        help="Is this a pre-release ? (also can use DO_PRERELEASE env var)")
    parser.add_argument("-t", "--tag", nargs=1, metavar='tag',
        required=True, help="New release tag")
    parser.add_argument("-r", "--repository", nargs=1, metavar='repo',
        required=True, help="Repository path (user/repo)")
    parser.add_argument("-p", "--proxy", nargs=1,
        metavar='host:port',
        help="Proxy host and port (host:port)")
    parser.add_argument("-T", "--timeout", nargs=1, type=int,
        metavar="timeout", default=20,
        help='HTTP request timeout in seconds (default 20)')
    parser.add_argument('files', metavar='files', nargs='+',
	    help='list of files')
    arguments = parser.parse_args()

    return arguments

def post_request(url, timeout, data, headers=[], proxy=None):
    '''Create new release on Github.

    Return JSON data on success or throw exception on failure.

    '''

    if proxy:
        #
        # Register proxy handler for https protocol since this is what
        # we will be using for requests.
        #
        proxy_handler = ProxyHandler({'https': proxy})
        logger.debug("using proxy handler")
        opener = build_opener(proxy_handler, HTTPSHandler)
    else:
        opener = build_opener(HTTPSHandler)

    logger.debug("sending request to URL {}".format(url))
    request = Request(url, data=data)
    # XXX toxic
    # request.get_method = "POST"
    for hdrname in headers.keys():
        request.add_header(hdrname, headers[hdrname])

    try:
        response = opener.open(request, timeout=timeout)
        is_json = _process_response(response.headers)
        if is_json:
            jsonOut = json.loads(response.read().decode('utf-8'))
            return jsonOut
        else:
            raise MyError("server returned non-JSON output")
    except HTTPError as e:
        print e.code
        # XXX print string representatin of the code
        raise MyError("got HTTP error: {} ({})".format(e.code, e.reason))

def upload_file(filepath, upload_url, headers, timeout, proxy=None):
    '''Upload file to given Github upload URL

    The upload_url is template which needs modification.

    '''
    # TODO: is there better way how transform the template ?
    [ url, rest ] = split(upload_url, '{')
    url = url + "?name=" + os.path.basename(filepath)
    headers["Content-Length"] = os.path.getsize(filepath)
    fileObj = open(filepath, 'r')
    logger.debug("Uploading file to {}".format(url))
    post_request(url, timeout, fileObj, headers, proxy)


def main():
    arguments = get_args()

    user = None
    if not arguments.user:
        try:
            user = os.environ["GITHUB_USER"]
        except:
            print "Need -u or specify user with GITHUB_USER env var"
            sys.exit(1)
    else:
        user = arguments.user[0]

    if arguments.debug:
        logging.basicConfig(
            level=logging.DEBUG,
            format="%(asctime)s [%(levelname)-7s] [line %(lineno)d] %(name)s: %(message)s",
            stream=sys.stderr)
        logger.setLevel(logging.DEBUG)
        debug = True

    # There is exactly 1 item in the list.
    # TODO: there should be better way how to achieve this.
    description = arguments.description[0]
    tag = arguments.tag[0].strip()
    logger.debug("using tag '{}'".format(tag))
    repo = arguments.repository[0].strip()

    proxy = None
    if arguments.proxy:
        proxy = arguments.proxy[0]
        logger.debug("using proxy %s", proxy)
    else:
        try:
            os.environ["https_proxy"]
        except:
            print "no proxy"
            sys.exit(1)

    # First check that the files indeed exist and are readable.
    # TODO: can probably do it from parser using action
    for file in arguments.files:
        logger.debug("checking file {}".format(file))
        if not os.path.isfile(file) or not os.access(file, os.R_OK):
	    print "file '" + file + "' does not exist or is not readable"
            sys.exit(1)
	_files.append(file)

    # TODO: Check if the tag is not already present in the repo.

    try:
        password = os.environ["GITHUB_PASSWORD"]
    except:
        #
        # If interrupted via e.g. ctrl+D this will throw an exception
        # which will tear down the whole program which is fine so there
        # is no need to handle it.
        #
        password = getpass()

    headers = {}
    headers['Authorization'] = _get_auth(user, password)

    prerelease = False
    if arguments.prerelease:
        prerelease = True
    else:
        try:
            if os.environ["DO_PRERELEASE"]:
                prerelease = True
        except:
            prerelease = False

    # Create new release and get the upload URL.
    rel_dict = get_release_dict(tag, description, prerelease)
    payload = json.dumps(rel_dict)
    if (arguments.debug):
        pprint(rel_dict)
        pprint(payload)
    upload_url = None

    dryrun = False
    if arguments.dryrun:
        dryrun = True
    else:
        try:
            os.environ["DO_DRYRUN"]
            dryrun = True
        except:
            dryrun = False
    if dryrun:
        print "Dry run in effect, exiting"
        sys.exit(0)

    try:
        _url = "https://api.github.com"
        _path = '%s%s%s' % ("/repos/", repo, "/releases")
        url = '%s%s' % (_url, _path)
        release_json = post_request(url,
            arguments.timeout, payload, headers, proxy)
        upload_url = release_json["upload_url"]
    except MyError as e:
        print 'My exception occurred, value:', e.value
        sys.exit(1)

    if upload_url:
        # Use zip content type as this works for anything.
        headers["Content-Type"] = "application/zip"
        for filename in arguments.files:
            try:
                upload_file(filename, upload_url, headers, arguments.timeout,
                    proxy)
            except MyError as e:
                print "failed to upload file {}".format(file)


if __name__ == '__main__':
    main()

# vim: set ft=python ts=4 sw=4 expandtab :
