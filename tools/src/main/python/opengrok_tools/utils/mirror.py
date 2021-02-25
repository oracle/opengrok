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
# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
#

import re
import os
import fnmatch
import logging
import urllib

from requests.exceptions import HTTPError

from .exitvals import (
    FAILURE_EXITVAL,
    CONTINUE_EXITVAL,
    SUCCESS_EXITVAL
)
from .patterns import PROJECT_SUBST, COMMAND_PROPERTY
from .utils import is_exe, check_create_dir, get_int, is_web_uri
from .opengrok import get_repos, get_repo_type, get_uri
from .hook import run_hook
from .command import Command
from .restful import call_rest_api, do_api_call

from ..scm.repofactory import get_repository
from ..scm.repository import RepositoryException


# "constants"
HOOK_TIMEOUT_PROPERTY = 'hook_timeout'
CMD_TIMEOUT_PROPERTY = 'command_timeout'
IGNORED_REPOS_PROPERTY = 'ignored_repos'
PROXY_PROPERTY = 'proxy'
COMMANDS_PROPERTY = 'commands'
DISABLED_PROPERTY = 'disabled'
DISABLED_REASON_PROPERTY = 'disabled-reason'
HOOKDIR_PROPERTY = 'hookdir'
HOOKS_PROPERTY = 'hooks'
LOGDIR_PROPERTY = 'logdir'
PROJECTS_PROPERTY = 'projects'
DISABLED_CMD_PROPERTY = 'disabled_command'


def get_repos_for_project(project_name, uri, source_root,
                          ignored_repos=None,
                          commands=None, proxy=None, command_timeout=None,
                          headers=None):
    """
    :param project_name: project name
    :param uri: web application URI
    :param source_root source root
    :param ignored_repos: list of ignored repositories
    :param commands: dictionary of commands - paths to SCM programs
    :param proxy: dictionary of proxy servers - to be used as environment
                  variables
    :param command_timeout: command timeout value in seconds
    :param headers: optional HTTP headers dictionary
    :return: list of Repository objects
    """

    logger = logging.getLogger(__name__)

    repos = []
    for repo_path in get_repos(logger, project_name, uri, headers=headers):
        logger.debug("Repository path = {}".format(repo_path))

        if ignored_repos:
            r_path = os.path.relpath(repo_path, '/' + project_name)
            if any(map(lambda repo: fnmatch.fnmatch(r_path, repo),
                       ignored_repos)):
                logger.info("repository {} ignored".format(repo_path))
                continue

        repo_type = get_repo_type(logger, repo_path, uri, headers=headers)
        if not repo_type:
            raise RepositoryException("cannot determine type of repository {}".
                                      format(repo_path))

        logger.debug("Repository type = {}".format(repo_type))

        repo = None
        try:
            # The OpenGrok convention is that the form of repo_path is absolute
            # so joining the paths would actually spoil things. Hence, be
            # careful.
            if repo_path.startswith(os.path.sep):
                path = source_root + repo_path
            else:
                path = os.path.join(source_root, repo_path)

            repo = get_repository(path,
                                  repo_type,
                                  project_name,
                                  env=proxy,
                                  timeout=command_timeout,
                                  commands=commands)
        except (RepositoryException, OSError) as e:
            logger.error("Cannot get repository for {}: {}".
                         format(repo_path, e))

        if repo:
            repos.append(repo)

    return repos


def get_project_config(config, project_name):
    """
    Return per project configuration, if any.
    :param config: global configuration
    :param project_name name of the project
    :return: project configuration dictionary or None
    """

    logger = logging.getLogger(__name__)

    project_config = None
    projects = config.get(PROJECTS_PROPERTY)
    if projects:
        project_config = projects.get(project_name)
        if not project_config:
            for project_pattern in projects.keys():
                if re.match(project_pattern, project_name):
                    logger.debug("Project '{}' matched pattern '{}'".
                                 format(project_name, project_pattern))
                    project_config = projects.get(project_pattern)
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


def process_hook(hook_ident, hook, source_root, project_name, proxy,
                 hook_timeout):
    """
    :param hook_ident: ident of the hook to be used in log entries
    :param hook: hook
    :param source_root: source root path
    :param project_name: project name
    :param proxy: proxy or None
    :param hook_timeout: hook run timeout
    :return: False if hook failed, else True
    """
    if hook:
        logger = logging.getLogger(__name__)

        logger.info("Running {} hook".format(hook_ident))
        if run_hook(logger, hook,
                    os.path.join(source_root, project_name), proxy,
                    hook_timeout) != SUCCESS_EXITVAL:
            logger.error("{} hook failed for project {}".
                         format(hook_ident, project_name))
            return False

    return True


def process_changes(repos, project_name, uri, headers=None):
    """
    :param repos: repository list
    :param project_name: project name
    :param headers: optional dictionary of HTTP headers
    :return: exit code
    """
    logger = logging.getLogger(__name__)

    changes_detected = False

    # check if the project is a new project - full index is necessary
    try:
        r = do_api_call('GET', get_uri(uri, 'api', 'v1', 'projects',
                                       urllib.parse.quote_plus(project_name),
                                       'property', 'indexed'),
                        headers=headers)
        if not bool(r.json()):
            changes_detected = True
            logger.info('Project {} has not been indexed yet'
                        .format(project_name))
    except ValueError as e:
        logger.error('Unable to parse project \'{}\' indexed flag: {}'
                     .format(project_name, e))
        return FAILURE_EXITVAL
    except HTTPError as e:
        logger.error('Unable to determine project \'{}\' indexed flag: {}'
                     .format(project_name, e))
        return FAILURE_EXITVAL

    # check if the project has any new changes in the SCM
    if not changes_detected:
        for repo in repos:
            try:
                if repo.incoming():
                    logger.debug('Repository {} has incoming changes'.
                                 format(repo))
                    changes_detected = True
                    break
            except RepositoryException:
                logger.error('Cannot determine incoming changes for '
                             'repository {}'.format(repo))
                return FAILURE_EXITVAL

    if not changes_detected:
        logger.info('No incoming changes for repositories in '
                    'project {}'.
                    format(project_name))
        return CONTINUE_EXITVAL

    return SUCCESS_EXITVAL


def run_command(cmd, project_name):
    cmd.execute()
    if cmd.getretcode() != 0:
        logger = logging.getLogger(__name__)

        logger.error("Command for disabled project '{}' failed "
                     "with error code {}: {}".
                     format(project_name, cmd.getretcode(),
                            cmd.getoutputstr()))


def handle_disabled_project(config, project_name, disabled_msg, headers=None):
    disabled_command = config.get(DISABLED_CMD_PROPERTY)
    if disabled_command:
        logger = logging.getLogger(__name__)

        logger.debug("Calling disabled command: {}".format(disabled_command))
        command_args = disabled_command.get(COMMAND_PROPERTY)
        uri = command_args[0]
        if is_web_uri(uri):
            # Is this perhaps OpenGrok API call to supply a Message ?
            # If so and there was a string supplied, append it
            # to the message text.
            data = command_args[2]
            text = None
            if type(data) is dict:
                text = data.get("text")
            if text and uri.find("/api/v1/") > 0 and type(disabled_msg) is str:
                logger.debug("Appending text to message: {}".
                             format(disabled_msg))
                command_args[2]["text"] = text + ": " + disabled_msg

            try:
                call_rest_api(disabled_command, {PROJECT_SUBST: project_name},
                              http_headers=headers)
            except HTTPError as e:
                logger.error("API call failed for disabled command of "
                             "project '{}': {}".
                             format(project_name, e))
        else:
            args = [project_name]
            if disabled_msg and type(disabled_msg) is str:
                args.append(disabled_command)

            cmd = Command(command_args,
                          env_vars=disabled_command.get("env"),
                          resource_limits=disabled_command.get("limits"),
                          args_subst={PROJECT_SUBST: project_name},
                          args_append=args, excl_subst=True)
            run_command(cmd, project_name)


def mirror_project(config, project_name, check_changes, uri,
                   source_root, headers=None):
    """
    Mirror the repositories of single project.
    :param config global configuration dictionary
    :param project_name: name of the project
    :param check_changes: check for changes in the project or its repositories
     and terminate if no change is found
    :param uri
    :param source_root
    :return exit code
    """

    ret = SUCCESS_EXITVAL

    logger = logging.getLogger(__name__)

    project_config = get_project_config(config, project_name)
    prehook, posthook, hook_timeout, command_timeout, use_proxy, \
        ignored_repos = get_project_properties(project_config,
                                               project_name,
                                               config.get(HOOKDIR_PROPERTY))

    if not command_timeout:
        command_timeout = config.get(CMD_TIMEOUT_PROPERTY)
    if not hook_timeout:
        hook_timeout = config.get(HOOK_TIMEOUT_PROPERTY)

    proxy = None
    if use_proxy:
        proxy = config.get(PROXY_PROPERTY)

    # We want this to be logged to the log file (if any).
    if project_config:
        if project_config.get(DISABLED_PROPERTY):
            handle_disabled_project(config, project_name,
                                    project_config.
                                    get(DISABLED_REASON_PROPERTY),
                                    headers=headers)
            logger.info("Project '{}' disabled, exiting".
                        format(project_name))
            return CONTINUE_EXITVAL

    #
    # Cache the repositories first. This way it will be known that
    # something is not right, avoiding any needless pre-hook run.
    #
    repos = get_repos_for_project(project_name,
                                  uri,
                                  source_root,
                                  ignored_repos=ignored_repos,
                                  commands=config.
                                  get(COMMANDS_PROPERTY),
                                  proxy=proxy,
                                  command_timeout=command_timeout,
                                  headers=headers)
    if not repos:
        logger.info("No repositories for project {}".
                    format(project_name))
        return CONTINUE_EXITVAL

    # Check if the project or any of its repositories have changed.
    if check_changes:
        r = process_changes(repos, project_name, uri, headers=headers)
        if r != SUCCESS_EXITVAL:
            return r

    if not process_hook("pre", prehook, source_root, project_name, proxy,
                        hook_timeout):
        return FAILURE_EXITVAL

    #
    # If one of the repositories fails to sync, the whole project sync
    # is treated as failed, i.e. the program will return FAILURE_EXITVAL.
    #
    for repo in repos:
        logger.info("Synchronizing repository {}".
                    format(repo.path))
        if repo.sync() != SUCCESS_EXITVAL:
            logger.error("failed to synchronize repository {}".
                         format(repo.path))
            ret = FAILURE_EXITVAL

    if not process_hook("post", posthook, source_root, project_name, proxy,
                        hook_timeout):
        return FAILURE_EXITVAL

    return ret


def check_project_configuration(multiple_project_config, hookdir=False,
                                proxy=False):
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
                              IGNORED_REPOS_PROPERTY, HOOKS_PROPERTY,
                              DISABLED_REASON_PROPERTY]

    if not multiple_project_config:
        return True

    logger.debug("Checking project configuration")

    for project_name, project_config in multiple_project_config.items():
        logger.debug("Checking configuration of project {}".
                     format(project_name))

        if project_config is None:
            logger.warning("Project {} has empty configuration".
                           format(project_name))
            continue

        diff = set(project_config.keys()).difference(known_project_tunables)
        if diff:
            logger.error("unknown project configuration option(s) '{}' "
                         "for project {}".format(diff, project_name))
            return False

        if project_config.get(PROXY_PROPERTY) and not proxy:
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

        try:
            re.compile(project_name)
        except re.error:
            logger.error("Not a valid regular expression: {}".
                         format(project_name))
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
                       HOOK_TIMEOUT_PROPERTY, CMD_TIMEOUT_PROPERTY,
                       DISABLED_CMD_PROPERTY]
    diff = set(config.keys()).difference(global_tunables)
    if diff:
        logger.error("unknown global configuration option(s): '{}'"
                     .format(diff))
        return False

    # Make sure the log directory exists.
    logdir = config.get(LOGDIR_PROPERTY)
    if logdir:
        check_create_dir(logger, logdir)

    disabled_command = config.get(DISABLED_CMD_PROPERTY)
    if disabled_command:
        logger.debug("Disabled command: {}".format(disabled_command))

    if not check_project_configuration(config.get(PROJECTS_PROPERTY),
                                       config.get(HOOKDIR_PROPERTY),
                                       config.get(PROXY_PROPERTY)):
        return False

    return True
