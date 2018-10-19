#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#

#
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
#

import requests
import traceback
from urllib.parse import urlparse


def get(logger, uri, params=None, headers=None):
    try:
        proxies = get_proxies(uri)
        return requests.get(uri, params=params, proxies=proxies)
    except Exception:
        logger.debug(traceback.format_exc())
        return None


def delete(logger, uri, params=None, headers=None):
    try:
        proxies = get_proxies(uri)
        return requests.delete(uri, params=params, proxies=proxies)
    except Exception:
        logger.debug(traceback.format_exc())
        return None


def post(logger, uri, headers=None, data=None):
    rv = None
    try:
        proxies = get_proxies(uri)
        rv = requests.post(uri, data=data, headers=headers, proxies=proxies)
    except Exception:
        logger.debug(traceback.format_exc())
        return None

    return rv


def put(logger, uri, headers=None, data=None):
    rv = None
    try:
        proxies = get_proxies(uri)
        rv = requests.put(uri, data=data, headers=headers, proxies=proxies)
    except Exception:
        logger.debug(traceback.format_exc())
        return None

    return rv


def get_uri(*uri_parts):
    return '/'.join(s.strip('/') for s in uri_parts)


def is_localhost_url(url):
    """
    Check if given URL is based on localhost.
    """

    o = urlparse(url)
    return o.hostname in ['localhost', '127.0.0.1', '::1']


def get_proxies(url):
    """
    For localhost based requests it is undesirable to use proxies.
    """
    if is_localhost_url(url):
        return {'http': None, 'https': None}
    else:
        return None
