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
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
#

import urllib.parse
from requests.exceptions import RequestException
from .webutil import get_uri
from .restful import do_api_call


def get_repos(logger, project, uri, headers=None, timeout=None):
    """
    :param logger: logger instance
    :param project: project name
    :param uri: web application URI
    :param headers: optional dictionary of HTTP headers
    :param timeout: optional timeout in seconds
    :return: list of repository paths (can be empty if no match)
             or None on failure
    """

    try:
        res = do_api_call('GET', get_uri(uri, 'api', 'v1', 'projects',
                                         urllib.parse.quote_plus(project),
                                         'repositories'),
                          headers=headers, timeout=timeout)
    except RequestException as exception:
        logger.error("could not get repositories for project '{}': {}".
                     format(project, exception))
        return None

    ret = []
    for line in res.json():
        ret.append(line.strip())

    return ret


def get_config_value(logger, name, uri, headers=None, timeout=None):
    """
    Get configuration value.

    Return string with the result on success, None on failure.
    """
    try:
        res = do_api_call('GET', get_uri(uri, 'api', 'v1', 'configuration',
                                         urllib.parse.quote_plus(name)),
                          headers=headers, timeout=timeout)
    except RequestException as exception:
        logger.error("Cannot get the '{}' config value from the web "
                     "application: {}".format(name, exception))
        return None

    return res.text


def set_config_value(logger, name, value, uri, headers=None, timeout=None):
    """
    Set configuration value.
    :param logger: logger instance
    :param name: name of the configuration field
    :param value: field value
    :param uri: web app URI
    :param headers: optional dictionary of HTTP headers
    :param timeout: optional request timeout
    :return: True on success, False on failure
    """
    try:
        local_headers = {}
        if headers:
            local_headers.update(headers)
        local_headers['Content-type'] = 'application/text'
        do_api_call('PUT', get_uri(uri, 'api', 'v1', 'configuration', name),
                    data=value, headers=local_headers, timeout=timeout)
    except RequestException as exception:
        logger.error("Cannot set the '{}' config field to '{}' in the web "
                     "application: {}".format(name, value, exception))
        return False

    return True


def get_repo_type(logger, repository, uri, headers=None, timeout=None):
    """
    Get repository type for given path relative to sourceRoot.

    Return string with the result on success, None on failure.
    """
    payload = {'repository': repository}

    try:
        res = do_api_call('GET', get_uri(uri, 'api', 'v1', 'repositories',
                                         'property', 'type'), params=payload,
                          headers=headers, timeout=timeout)
    except RequestException as exception:
        logger.error("could not get repository type for '{}' from web"
                     "application: {}".format(repository, exception))
        return None

    line = res.text

    idx = line.rfind(":")
    return line[idx + 1:]


def get_configuration(logger, uri, headers=None, timeout=None):
    try:
        res = do_api_call('GET', get_uri(uri, 'api', 'v1', 'configuration'),
                          headers=headers, timeout=timeout)
    except RequestException as exception:
        logger.error('could not get configuration from web application: {}'.
                     format(exception))
        return None

    return res.text


def set_configuration(logger, configuration, uri, headers=None, timeout=None, api_timeout=None):
    try:
        r = do_api_call('PUT', get_uri(uri, 'api', 'v1', 'configuration'),
                        data=configuration, headers=headers, timeout=timeout, api_timeout=api_timeout)
        if r is None or r.status_code != 201:
            logger.error(f'could not set configuration to web application {r}')
            return False
    except RequestException as exception:
        logger.error('could not set configuration to web application: {}'.
                     format(exception))
        return False

    return True


def list_projects(logger, uri, headers=None, timeout=None):
    try:
        res = do_api_call('GET',
                          get_uri(uri, 'api', 'v1', 'projects'),
                          headers=headers, timeout=timeout)
    except RequestException as exception:
        logger.error("could not list projects from web application: {}".
                     format(exception))
        return None

    return res.json()


def list_indexed_projects(logger, uri, headers=None, timeout=None):
    try:
        res = do_api_call('GET',
                          get_uri(uri, 'api', 'v1', 'projects', 'indexed'),
                          headers=headers, timeout=timeout)
    except RequestException as exception:
        logger.error("could not list indexed projects from web application: {}".
                     format(exception))
        return None

    return res.json()


def add_project(logger, project, uri, headers=None, timeout=None, api_timeout=None):
    try:
        r = do_api_call('POST', get_uri(uri, 'api', 'v1', 'projects'),
                        data=project, headers=headers, timeout=timeout, api_timeout=api_timeout)
        if r is None or r.status_code != 201:
            logger.error(f"could not add project '{project}' in web application: {r}")
            return False
    except RequestException as exception:
        logger.error("could not add project '{}' to web application: {}".
                     format(project, exception))
        return False

    return True


def _delete_project(logger, project, uri, headers=None, timeout=None, api_timeout=None):
    try:
        r = do_api_call('DELETE', uri,
                        headers=headers, timeout=timeout, api_timeout=api_timeout)
        if r is None or r.status_code != 204:
            logger.error(f"could not delete project '{project}' in web application: {r}")
            return False
    except RequestException as exception:
        logger.error("could not delete project '{}' in web application: {}".
                     format(project, exception))
        return False

    return True


def delete_project(logger, project, uri, headers=None, timeout=None, api_timeout=None):
    return _delete_project(logger, project, get_uri(uri, 'api', 'v1', 'projects',
                                                    urllib.parse.quote_plus(project)),
                           headers=headers,
                           timeout=timeout, api_timeout=api_timeout)


def delete_project_data(logger, project, uri, headers=None, timeout=None, api_timeout=None):
    return _delete_project(logger, project, get_uri(uri, 'api', 'v1', 'projects',
                                                    urllib.parse.quote_plus(project), 'data'),
                           headers=headers,
                           timeout=timeout, api_timeout=api_timeout)
