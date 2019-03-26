#!/usr/bin/env python3
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
# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
#

import re
import os
import fnmatch
import logging

from .utils import is_exe, check_create_dir, get_int, diff_list
from .opengrok import get_repos, get_repo_type
from .hook import run_hook

from ..scm.repofactory import get_repository
from ..scm.repository import RepositoryException


# "constants"
HOOK_TIMEOUT_PROPERTY = 'hook_timeout'
CMD_TIMEOUT_PROPERTY = 'command_timeout'
IGNORED_REPOS_PROPERTY = 'ignored_repos'
PROXY_PROPERTY = 'proxy'
COMMANDS_PROPERTY = 'commands'
DISABLED_PROPERTY = 'disabled'
HOOKDIR_PROPERTY = 'hookdir'
HOOKS_PROPERTY = 'hooks'
LOGDIR_PROPERTY = 'logdir'
PROJECTS_PROPERTY = 'projects'

# This is a special exit code that is recognized by sync.py to terminate
# the processing of the command sequence.
CONTINUE_EXITVAL = 2


def get_repos_for_project(logger, project_name, ignored_repos, **kwargs):
    """
    :param logger
    :param project_name: project name
    :param ignored_repos: list of ignored repositories
    :param kwargs: argument dictionary
    :return: list of Repository objects
    """

    repos = []
    for repo_path in get_repos(logger, project_name, kwargs['uri']):
        logger.debug("Repository path = {}".format(repo_path))

        r_path = os.path.relpath(repo_path, '/' + project_name)
        if any(map(lambda repo: fnmatch.fnmatch(r_path, repo), ignored_repos)):
            logger.info("repository {} ignored".format(repo_path))
            continue

        repo_type = get_repo_type(logger, repo_path, kwargs['uri'])
        if not repo_type:
            raise RepositoryException("cannot determine type of repository {}".
                                      format(repo_path))

        logger.debug("Repository type = {}".format(repo_type))

        repo = None
        try:
            # Not joining the path since the form of repo_path is absolute.
            repo = get_repository(kwargs['source_root'] + repo_path,
                                  repo_type,
                                  project_name,
                                  kwargs[COMMANDS_PROPERTY],
                                  kwargs[PROXY_PROPERTY],
                                  None,
                                  kwargs['command_timeout'])
        except (RepositoryException, OSError) as e:
            logger.error("Cannot get repository for {}: {}".
                         format(repo_path, e))

        if repo:
            repos.append(repo)

    return repos


def get_project_config(config, project_name):
    """
    Return per project configuration, if any.
    :param config:
    :param project_name name of the project
    :return: project config or None
    """

    logger = logging.getLogger(__name__)

    project_config = None
    projects = config.get(PROJECTS_PROPERTY)
    if projects:
        if projects.get(project_name):
            project_config = projects.get(project_name)
        else:
            for proj in projects.keys():
                try:
                    pattern = re.compile(proj)
                except re.error:
                    logger.error("Not a valid regular expression: {}".
                                 format(proj))
                    # TODO raise exception, move to config check
                    continue

                if pattern.match(project_name):
                    logger.debug("Project '{}' matched pattern '{}'".
                                 format(project_name, proj))
                    project_config = projects.get(proj)
                    break

    return project_config


def get_project_properties(project_config, project_name, hookdir):
    """
    Get properties of project needed to perform mirroring.
    :param project_config: project configuration dictionary
    :param project_name: name of the project
    :param hookdir: directory with hooks
    :return: list of properties: prehook, posthook, hook_timeout,
    command_timeout, use_proxy, ignored_repos
    """

    prehook = None
    posthook = None
    hook_timeout = None
    command_timeout = None
    use_proxy = False
    ignored_repos = None

    logger = logging.getLogger(__name__)

    if project_config:
        logger.debug("Project '{}' has specific (non-default) config".
                     format(project_name))

        project_command_timeout = get_int(logger, "command timeout for "
                                                  "project {}".
                                          format(project_name),
                                          project_config.
                                          get(CMD_TIMEOUT_PROPERTY))
        if project_command_timeout:
            command_timeout = project_command_timeout
            logger.debug("Project command timeout = {}".
                         format(command_timeout))

        project_hook_timeout = get_int(logger, "hook timeout for "
                                               "project {}".
                                       format(project_name),
                                       project_config.
                                       get(HOOK_TIMEOUT_PROPERTY))
        if project_hook_timeout:
            hook_timeout = project_hook_timeout
            logger.debug("Project hook timeout = {}".
                         format(hook_timeout))

        ignored_repos = project_config.get(IGNORED_REPOS_PROPERTY)
        if ignored_repos:
            logger.debug("has ignored repositories: {}".
                         format(ignored_repos))

        hooks = project_config.get(HOOKS_PROPERTY)
        if hooks:
            for hookname in hooks:
                if hookname == "pre":
                    prehook = os.path.join(hookdir, hooks['pre'])
                    logger.debug("pre-hook = {}".format(prehook))
                elif hookname == "post":
                    posthook = os.path.join(hookdir, hooks['post'])
                    logger.debug("post-hook = {}".format(posthook))

        if project_config.get(PROXY_PROPERTY):
            logger.debug("will use proxy")
            use_proxy = True

    if not ignored_repos:
        ignored_repos = []

    return prehook, posthook, hook_timeout, command_timeout, \
        use_proxy, ignored_repos


def mirror_project(logger, config, project_name, check_incoming, uri,
                   source_root):
    """
    Mirror the repositories of single project.
    :param logger logger
    :param config global configuration dictionary
    :param project_name: name of the project
    :param check_incoming:
    :param uri
    :param source_root
    :return exit code
    """

    project_config = get_project_config(config, project_name)
    prehook, posthook, hook_timeout, command_timeout, use_proxy, \
        ignored_repos = get_project_properties(config, project_config,
                                               config.get(HOOKDIR_PROPERTY))

    proxy = None
    if use_proxy:
        proxy = config.get(PROXY_PROPERTY)

    # We want this to be logged to the log file (if any).
    if project_config:
        if project_config.get(DISABLED_PROPERTY):
            logger.info("Project {} disabled, exiting".
                        format(project_name))
            return CONTINUE_EXITVAL

    #
    # Cache the repositories first. This way it will be known that
    # something is not right, avoiding any needless pre-hook run.
    #
    ret = 0
    repos = get_repos_for_project(logger, project_name,
                                  ignored_repos,
                                  commands=config.
                                  get(COMMANDS_PROPERTY),
                                  proxy=proxy,
                                  command_timeout=command_timeout,
                                  source_root=source_root,
                                  uri=uri)
    if not repos:
        logger.info("No repositories for project {}".
                    format(project_name))
        return CONTINUE_EXITVAL

    # Check if any of the repositories contains incoming changes.
    if check_incoming:
        got_incoming = False
        for repo in repos:
            try:
                if repo.incoming():
                    logger.debug('Repository {} has incoming changes'.
                                 format(repo))
                    got_incoming = True
                    break
            except RepositoryException:
                logger.error('Cannot determine incoming changes for '
                             'repository {}'.format(repo))
                return 1

        if not got_incoming:
            logger.info('No incoming changes for repositories in '
                        'project {}'.
                        format(project_name))
            return CONTINUE_EXITVAL

    if prehook:
        logger.info("Running pre hook")
        if run_hook(logger, prehook,
                    os.path.join(source_root, project_name), proxy,
                    hook_timeout) != 0:
            logger.error("pre hook failed for project {}".
                         format(project_name))
            logging.shutdown()
            return 1

    #
    # If one of the repositories fails to sync, the whole project sync
    # is treated as failed, i.e. the program will return 1.
    #
    for repo in repos:
        logger.info("Synchronizing repository {}".
                    format(repo.path))
        if repo.sync() != 0:
            logger.error("failed to synchronize repository {}".
                         format(repo.path))
            ret = 1

    if posthook:
        logger.info("Running post hook")
        if run_hook(logger, posthook,
                    os.path.join(source_root, project_name), proxy,
                    hook_timeout) != 0:
            logger.error("post hook failed for project {}".
                         format(project_name))
            logging.shutdown()  # TODO
            return 1

    return ret


def check_project_configuration(multiple_project_config, hookdir, proxy):
    """
    Check configuration of given project
    :param multiple_project_config: project configuration dictionary
    :param hookdir: hook directory
    :param proxy: proxy setting
    :return: True if the configuration checks out, False otherwise
    """

    logger = logging.getLogger(__name__)

    # Quick sanity check.
    known_project_tunables = [DISABLED_PROPERTY, CMD_TIMEOUT_PROPERTY,
                              HOOK_TIMEOUT_PROPERTY, PROXY_PROPERTY,
                              IGNORED_REPOS_PROPERTY, HOOKS_PROPERTY]

    if not multiple_project_config:
        return True

    for project_name in multiple_project_config.keys():
        project_config = multiple_project_config.get(project_name)
        diff = diff_list(project_config.keys(),
                         known_project_tunables)
        if diff:
            logger.error("unknown project configuration option(s) '{}' "
                         "for project {}".format(diff, project_name))
            return False

        if not proxy:
            logger.error("global proxy setting is needed in order to"
                         "have per-project proxy")
            return False

        hooks = project_config.get(HOOKS_PROPERTY)
        if hooks:
            if not hookdir:
                logger.error("Need to have '{}' in the configuration "
                             "to run hooks".format(HOOKDIR_PROPERTY))
                return False

            if not os.path.isdir(hookdir):
                logger.error("Not a directory: {}".format(hookdir))
                return False

            for hookname in hooks.keys():
                if hookname not in ["pre", "post"]:
                    logger.error("Unknown hook name '{}' for project '{}'".
                                 format(hookname, project_name))
                    return False

                hookpath = os.path.join(hookdir, hooks.get(hookname))
                if not is_exe(hookpath):
                    logger.error("hook file {} for project '{}' does not exist"
                                 " or not executable".
                                 format(hookpath, project_name))
                    return False

        ignored_repos = project_config.get(IGNORED_REPOS_PROPERTY)
        if ignored_repos:
            if not isinstance(ignored_repos, list):
                logger.error("{} for project {} is not a list".
                             format(IGNORED_REPOS_PROPERTY, project_name))
                return False

    return True


def check_configuration(config):
    """
    Validate configuration
    :param config: global configuration dictionary
    :return: True if valid, False otherwise
    """

    logger = logging.getLogger(__name__)

    global_tunables = [HOOKDIR_PROPERTY, PROXY_PROPERTY, LOGDIR_PROPERTY,
                       COMMANDS_PROPERTY, PROJECTS_PROPERTY,
                       HOOK_TIMEOUT_PROPERTY, CMD_TIMEOUT_PROPERTY]
    diff = diff_list(config.keys(), global_tunables)
    if diff:
        logger.error("unknown global configuration option(s): '{}'"
                     .format(diff))
        return False

    # Make sure the log directory exists.
    logdir = config.get(LOGDIR_PROPERTY)
    if logdir:
        check_create_dir(logger, logdir)

    if not check_project_configuration(config.get(PROJECTS_PROPERTY),
                                       config.get(HOOKDIR_PROPERTY),
                                       config.get(PROXY_PROPERTY)):
        return False

    return True
