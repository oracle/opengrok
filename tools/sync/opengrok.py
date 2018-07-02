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

import logging
import requests
import urllib.parse
import traceback


def get(logger, uri, params=None, headers=None):
    try:
        return requests.get(uri, params=params)
    except Exception as e:
        logger.debug(traceback.format_exc())
        return None


def delete(logger, uri, params=None, headers=None):
    try:
        return requests.delete(uri, params=params)
    except Exception as e:
        logger.debug(traceback.format_exc())
        return None


def post(logger, uri, headers=None, data=None):
    rv = None
    try:
        rv = requests.post(uri, data=data, headers=headers)
    except Exception as e:
        logger.debug(traceback.format_exc())
        return None

    return rv


def put(logger, uri, headers=None, data=None):
    rv = None
    try:
        rv = requests.put(uri, data=data, headers=headers)
    except Exception as e:
        logger.debug(traceback.format_exc())
        return None

    return rv


def get_repos(logger, project, uri):
    """
    Get list of repositories for given project name.

    Return  string with the result on success, None on failure.
    """

    r = get(logger, get_uri(uri, 'api', 'v1', 'projects',
                            urllib.parse.quote_plus(project), 'repositories'))

    if not r:
        logger.error('could not get repositories for ' + project)
        return None

    ret = []
    for line in r.json():
        ret.append(line.strip())

    return ret


def get_config_value(logger, name, uri):
    """
    Get list of repositories for given project name.

    Return string with the result on success, None on failure.
    """
    r = get(logger, get_uri(uri, 'api', 'v1', 'configuration',
                            urllib.parse.quote_plus(name)))

    if not r:
        logger.error('could not get config value ' + name)
        return None

    return r.text


def get_repo_type(logger, repository, uri):
    """
    Get repository type for given path relative to sourceRoot.

    Return string with the result on success, None on failure.
    """
    payload = {'repository': repository}

    r = get(logger, get_uri(uri, 'api', 'v1', 'repositories', 'type'),
            params=payload)
    if not r:
        logger.error('could not get repo type for ' + repository)
        return None

    line = r.text

    idx = line.rfind(":")
    return line[idx + 1:]


def get_configuration(logger, uri):
    r = get(logger, get_uri(uri, 'api', 'v1', 'configuration'))
    if not r:
        logger.error('could not get configuration')
        return None

    return r.text


def set_configuration(logger, configuration, uri):
    r = put(logger, get_uri(uri, 'api', 'v1', 'configuration'),
            data=configuration)

    if not r:
        logger.error('could not set configuration')
        return False

    return True


def list_indexed_projects(logger, uri):
    r = get(logger, get_uri(uri, 'api', 'v1', 'projects', 'indexed'))
    if not r:
        logger.error('could not list indexed projects')
        return None

    return r.json()


def add_project(logger, project, uri):
    r = post(logger, get_uri(uri, 'api', 'v1', 'projects'), data=project)

    if not r:
        logger.error('could not add project ' + project)
        return False

    return True


def delete_project(logger, project, uri):
    r = delete(logger, get_uri(uri, 'api', 'v1', 'projects',
                               urllib.parse.quote_plus(project)))

    if not r:
        logger.error('could not delete project ' + project)
        return False

    return True


def get_uri(*uri_parts):
    return '/'.join(s.strip('/') for s in uri_parts)
