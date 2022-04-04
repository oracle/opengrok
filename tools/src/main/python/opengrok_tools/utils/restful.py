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
# Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
#

import json
import logging
import time

import requests

from .webutil import get_proxies, is_web_uri

CONTENT_TYPE = 'Content-Type'
APPLICATION_JSON = 'application/json'   # default


def call_finished(location_uri, headers, timeout):
    """
    :param location_uri: URI to check the status of API call
    :param headers: HTTP headers
    :param timeout: connect timeout
    :return indication and response tuple
    """
    logger = logging.getLogger(__name__)

    logger.debug(f"GET API call: {location_uri}, timeout {timeout} seconds and headers: {headers}")
    response = requests.get(location_uri, headers=headers, proxies=get_proxies(location_uri), timeout=timeout)
    if response is None:
        raise Exception("API call failed")

    response.raise_for_status()
    if response.status_code == 202:
        return False, response
    else:
        return True, response


def wait_for_async_api(response, api_timeout=None, headers=None, timeout=None):
    """
    :param response: request
    :param api_timeout: asynchronous API timeout (will wait forever or until error if None)
    :param headers: request headers
    :param timeout: connect timeout
    :return: request
    """
    logger = logging.getLogger(__name__)

    location_uri = response.headers.get("Location")
    if location_uri is None:
        raise Exception(f"no Location header in {response}")

    start_time = time.time()
    if api_timeout is None:
        while True:
            done, response = call_finished(location_uri, headers, timeout)
            if done:
                break
            time.sleep(1)
    else:
        for _ in range(api_timeout):
            done, response = call_finished(location_uri, headers, timeout)
            if done:
                break
            time.sleep(1)

    if response.status_code == 202:
        wait_time = time.time() - start_time
        logger.warn(f"API request still not completed after {int(wait_time)} seconds: {response}")
        return response

    logger.debug(f"DELETE API call to {location_uri}")
    requests.delete(location_uri, headers=headers, proxies=get_proxies(location_uri), timeout=timeout)

    return response


def do_api_call(method, uri, params=None, headers=None, data=None, timeout=None, api_timeout=None):
    """
    Perform an API call. Will raise an exception if the request fails.
    :param method: string holding HTTP verb
    :param uri: URI string
    :param params: request parameters
    :param headers: HTTP headers dictionary
    :param data: data or None
    :param timeout: optional connect timeout in seconds.
                    Applies also to asynchronous API status calls.
    :param api_timeout: optional timeout for asynchronous API requests in seconds.
    :return: the result of the handler call, can be None
    """
    logger = logging.getLogger(__name__)

    handler = getattr(requests, method.lower())
    if handler is None or not callable(handler):
        raise Exception('Unknown HTTP method: {}'.format(method))

    logger.debug("{} API call: {} with data '{}', connect timeout {} seconds, API timeout {} seconds and headers: {}".
                 format(method, uri, data, timeout, api_timeout, headers))
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
        r = wait_for_async_api(r, api_timeout=api_timeout, headers=headers, timeout=timeout)

    r.raise_for_status()

    return r


def subst(src, substitutions):
    if substitutions:
        for pattern, value in substitutions.items():
            if value:
                src = src.replace(pattern, value)

    return src


def get_call_props(call):
    """
    Retrieve the basic properties of a call.
    :param call: dictionary
    :return: URI, HTTP method, data, headers
    """

    logger = logging.getLogger(__name__)

    uri = call.get("uri")
    if not uri:
        raise Exception(f"no 'uri' key present in {call}")
    if not is_web_uri(uri):
        raise Exception(f"not a valid URI: {uri}")

    method = call.get("method")
    if not method:
        logger.debug(f"no 'method' key in {call}, using GET")
        method = "GET"

    data = call.get("data")

    try:
        headers = call.get("headers")
        if headers and not isinstance(headers, dict):
            raise Exception("headers must be a dictionary")
    except IndexError:
        headers = {}

    if headers is None:
        headers = {}

    return uri, method, data, headers


def call_rest_api(call, substitutions=None, http_headers=None, timeout=None, api_timeout=None):
    """
    Make REST API call. Occurrence of the pattern in the URI
    (first part of the command) or data payload will be replaced by the name.

    Default content type is application/json.

    :param call: dictionary describing the properties of the API call
    :param substitutions: dictionary of pattern:value for command and/or
                          data substitution
    :param http_headers: optional dictionary of HTTP headers to be appended
    :param timeout: optional timeout in seconds for API call response
    :param api_timeout: optional timeout in seconds for asynchronous API call
    :return value from given requests method
    """

    logger = logging.getLogger(__name__)

    uri, verb, data, headers = get_call_props(call)

    logger.debug(f"Headers from the call structure: {headers}")
    if http_headers:
        logger.debug("Updating HTTP headers for call {} with {}".
                     format(call, http_headers))
        headers.update(http_headers)

    logger.debug("Performing URI substitutions")
    uri = subst(uri, substitutions)
    logger.debug(f"URI after the substitutions: {uri}")

    call_api_timeout = call.get("api_timeout")
    if call_api_timeout:
        logger.debug(f"Setting API timeout based on the call to {call_api_timeout}")
        api_timeout = call_api_timeout

    if data:
        header_names = [x.lower() for x in headers.keys()]
        if CONTENT_TYPE.lower() not in header_names:
            logger.debug("Adding HTTP header: {} = {}".
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

    return do_api_call(verb, uri, headers=headers, data=data, timeout=timeout, api_timeout=api_timeout)
