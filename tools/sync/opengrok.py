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


def get_repos(logger, project, host):
    """
    Get list of repositories for given project name.

    Return  string with the result on success, None on failure.
    """
    payload = {'projects': [project]}

    r = requests.get(host + '/api/projects/repositories', params=payload)

    if not r:
        logger.error('could not get repositories for ' + project)
        return None

    ret = []
    for line in r.json():
        ret.append(line.strip())

    return ret


def get_config_value(logger, name, host):
    """
    Get list of repositories for given project name.

    Return string with the result on success, None on failure.
    """
    r = requests.get(host + '/api/configuration/' + name)
    if not r:
        logger.error('could not get config value ' + name)
        return None

    return r.text


def get_repo_type(logger, repository, host):
    """
    Get repository type for given path relative to sourceRoot.

    Return string with the result on success, None on failure.
    """
    payload = {'repositories': [repository]}

    r = requests.get(host + '/api/repositories/types', params=payload)
    if not r:
        logger.error('could not get repo type for ' + repository)
        return None

    line = r.json()[0]

    idx = line.rfind(":")
    return line[idx + 1:]


def get_configuration(logger, host):
    r = requests.get(host + '/api/configuration')
    if not r:
        logger.error('could not get configuration')
        return None

    return r.text


def set_configuration(logger, configuration, host):
    r = requests.put(host + '/api/configuration', data=configuration)

    if not r:
        logger.error('could not set configuration')
        return False

    return True


def list_indexed_projects(logger, host):
    r = requests.get(host + '/api/projects/indexed')
    if not r:
        logger.error('could not list indexed projects')
        return None

    return r.json()


def add_project(logger, project, host):
    r = requests.put(host + '/api/projects', json=[project])

    if not r:
        logger.error('could not add project ' + project)
        return False

    return True


def delete_project(logger, project, host):
    payload = {'projects': [project]}

    r = requests.delete(host + '/api/projects', params=payload)

    if not r:
        logger.error('could not delete project ' + project)
        return False

    return True
