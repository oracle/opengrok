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
# Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
#

import json
import logging
import time

import requests

from .patterns import COMMAND_PROPERTY
from .webutil import get_proxies

CONTENT_TYPE = 'Content-Type'
APPLICATION_JSON = 'application/json'   # default


def wait_for_async_api(response, api_timeout, headers=None, timeout=None):
    """
    :param response: request
    :param api_timeout: asynchronous API timeout
    :param headers: request headers
    :param timeout: connect timeout
    :return: request
    """
    logger = logging.getLogger(__name__)

    location_uri = response.headers.get("Location")
    if location_uri is None:
        raise Exception(f"no Location header in {response}")

    for _ in range(api_timeout):
        logger.debug(f"GET API call: {location_uri}, timeout {timeout} seconds and headers: {headers}")
        response = requests.get(location_uri, headers=headers, proxies=get_proxies(location_uri), timeout=timeout)
        if response is None:
            raise Exception("API call failed")

        if response.status_code == 202:
            time.sleep(1)
        else:
            break

    if response.status_code == 202:
        logger.warn(f"API request still not completed: {response}")
        return response

    logger.debug(f"DELETE API call to {location_uri}")
    requests.delete(location_uri, headers=headers, proxies=get_proxies(location_uri), timeout=timeout)

    return response


def do_api_call(verb, uri, params=None, headers=None, data=None, timeout=None, api_timeout=None):
    """
    Perform an API call. Will raise an exception if the request fails.
    :param verb: string holding HTTP verb
    :param uri: URI string
    :param params: request parameters
    :param headers: HTTP headers dictionary
    :param data: data or None
    :param timeout: optional connect timeout in seconds.
                    Applies also to asynchronous API status calls.
                    If None, default (60 seconds) will be used.
    :param api_timeout: optional timeout for asynchronous API requests in seconds.
                    If None, default (300 seconds) will be used.
    :return: the result of the handler call, can be None
    """
    logger = logging.getLogger(__name__)

    handler = getattr(requests, verb.lower())
    if handler is None or not callable(handler):
        raise Exception('Unknown HTTP verb: {}'.format(verb))

    if timeout is None:
        timeout = 60

    if api_timeout is None:
        api_timeout = 300

    logger.debug("{} API call: {} with data '{}', timeout {} seconds and headers: {}".
                 format(verb, uri, data, timeout, headers))
    r = handler(
        uri,
        data=data,
        params=params,
        headers=headers,
        proxies=get_proxies(uri),
        timeout=timeout
    )

    if r is None:
        raise Exception("API call failed")

    if r.status_code == 202:
        r = wait_for_async_api(r, api_timeout, headers=headers, timeout=timeout)

    r.raise_for_status()

    return r


def subst(src, substitutions):
    if substitutions:
        for pattern, value in substitutions.items():
            if value:
                src = src.replace(pattern, value)

    return src


def call_rest_api(command, substitutions=None, http_headers=None, timeout=None):
    """
    Make RESTful API call. Occurrence of the pattern in the URI
    (first part of the command) or data payload will be replaced by the name.

    Default content type is application/json.

    :param command: command (list of URI, HTTP verb, data payload,
                             HTTP header dictionary)
    :param substitutions: dictionary of pattern:value for command and/or
                          data substitution
    :param http_headers: optional dictionary of HTTP headers to be appended
    :param timeout: optional timeout in seconds for API call response
    :return return value from given requests method
    """

    logger = logging.getLogger(__name__)

    if not isinstance(command, dict) or command.get(COMMAND_PROPERTY) is None:
        raise Exception("invalid command")

    command = command[COMMAND_PROPERTY]

    uri, verb, data, *_ = command
    try:
        headers = command[3]
        if headers and not isinstance(headers, dict):
            raise Exception("headers must be a dictionary")
    except IndexError:
        headers = {}

    if headers is None:
        headers = {}

    logger.debug("Headers from the command: {}".format(headers))
    if http_headers:
        logger.debug("Updating HTTP headers for command {} with {}".
                     format(command, http_headers))
        headers.update(http_headers)

    uri = subst(uri, substitutions)
    header_names = [x.lower() for x in headers.keys()]

    if data:
        if CONTENT_TYPE.lower() not in header_names:
            logger.debug("Adding header: {} = {}".
                         format(CONTENT_TYPE, APPLICATION_JSON))
            headers[CONTENT_TYPE] = APPLICATION_JSON

        for (k, v) in headers.items():
            if k.lower() == CONTENT_TYPE.lower():
                if headers[k].lower() == APPLICATION_JSON.lower():
                    logger.debug("Converting {} to JSON".format(data))
                    data = json.dumps(data)
                break

        data = subst(data, substitutions)
        logger.debug("entity data: {}".format(data))

    return do_api_call(verb, uri, headers=headers, data=data, timeout=timeout)
