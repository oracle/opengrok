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
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
#

import urllib.parse
from .webutil import get_uri
from .restful import do_api_call


def get_repos(logger, project, uri):
    """
    :param logger: logger instance
    :param project: project name
    :param uri: web application URI
    :return: list of repository paths (can be empty if no match)
             or None on failure
    """

    try:
        r = do_api_call('GET', get_uri(uri, 'api', 'v1', 'projects',
                                       urllib.parse.quote_plus(project),
                                       'repositories'))
    except Exception:
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
    try:
        r = do_api_call('GET', get_uri(uri, 'api', 'v1', 'configuration',
                                       urllib.parse.quote_plus(name)))
    except Exception:
        logger.error("Cannot get the '{}' config value from the web "
                     "application on {}".format(name, uri))
        return None

    return r.text


def get_repo_type(logger, repository, uri):
    """
    Get repository type for given path relative to sourceRoot.

    Return string with the result on success, None on failure.
    """
    payload = {'repository': repository}

    try:
        r = do_api_call('GET', get_uri(uri, 'api', 'v1', 'repositories',
                                       'property', 'type'), params=payload)
    except Exception:
        logger.error('could not get repository type for {} from web'
                     'application on {}'.format(repository, uri))
        return None

    line = r.text

    idx = line.rfind(":")
    return line[idx + 1:]


def get_configuration(logger, uri):
    try:
        r = do_api_call('GET', get_uri(uri, 'api', 'v1', 'configuration'))
    except Exception:
        logger.error('could not get configuration from web application on {}'.
                     format(uri))
        return None

    return r.text


def set_configuration(logger, configuration, uri):
    try:
        do_api_call('PUT', get_uri(uri, 'api', 'v1', 'configuration'),
                    data=configuration)
    except Exception:
        logger.error('could not set configuration for web application on {}'.
                     format(uri))
        return False

    return True


def list_projects(logger, uri):
    try:
        r = do_api_call('GET',
                        get_uri(uri, 'api', 'v1', 'projects'))
    except Exception:
        logger.error('could not list projects from web application '
                     'on {}'.format(uri))
        return None

    return r.json()


def list_indexed_projects(logger, uri):
    try:
        r = do_api_call('GET',
                        get_uri(uri, 'api', 'v1', 'projects', 'indexed'))
    except Exception:
        logger.error('could not list indexed projects from web application '
                     'on {}'.format(uri))
        return None

    return r.json()


def add_project(logger, project, uri):
    try:
        do_api_call('POST', get_uri(uri, 'api', 'v1', 'projects'),
                    data=project)
    except Exception:
        logger.error('could not add project {} for web application on {}'.
                     format(project, uri))
        return False

    return True


def delete_project(logger, project, uri):
    try:
        do_api_call('DELETE', get_uri(uri, 'api', 'v1', 'projects',
                                      urllib.parse.quote_plus(project)))
    except Exception:
        logger.error('could not delete project {} in web application on {}'.
                     format(project, uri))
        return False

    return True
