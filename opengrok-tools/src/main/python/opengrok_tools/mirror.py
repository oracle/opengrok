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
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
#

"""
 This script performs mirroring of single OpenGrok project.

 It is intended to work on Unix systems.

"""

import argparse
import logging
import os
import re
import sys
import tempfile
from logging.handlers import RotatingFileHandler

from .utils.filelock import Timeout, FileLock
from .utils.hook import run_hook
from .utils.log import get_console_logger
from .utils.opengrok import get_repos, get_config_value, get_repo_type
from .utils.readconfig import read_config
from .utils.repofactory import get_repository
from .utils.utils import is_exe, check_create_dir, get_int, diff_list,\
    is_web_uri
from .scm.repository import RepositoryException


major_version = sys.version_info[0]
if major_version < 3:
    print("Need Python 3, you are running {}".format(major_version))
    sys.exit(1)

__version__ = "0.5"

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


def get_repos_for_project(logger, project, ignored_repos, **kwargs):
    """
    :param logger: logger
    :param project: project name
    :param ignored_repos: list of ignored repositories
    :param kwargs: argument dictionary
    :return: list of Repository objects
    """
    repos = []
    for repo_path in get_repos(logger, project, kwargs['uri']):
        logger.debug("Repository path = {}".format(repo_path))

        if repo_path in ignored_repos:
            logger.info("repository {} ignored".format(repo_path))
            continue

        repo_type = get_repo_type(logger, repo_path, kwargs['uri'])
        if not repo_type:
            raise RepositoryException("cannot determine type of repository {}".
                                      format(repo_path))

        logger.debug("Repository type = {}".format(repo_type))

        repo = get_repository(logger,
                              # Not joining the path since the form
                              # of repo_path is absolute path.
                              kwargs['source_root'] + repo_path,
                              repo_type,
                              project,
                              kwargs['commands'],
                              kwargs['proxy'],
                              None,
                              kwargs['command_timeout'])
        if not repo:
            raise RepositoryException("Cannot get repository for {}".
                                      format(repo_path))
        else:
            repos.append(repo)

    return repos


def main():
    ret = 0

    parser = argparse.ArgumentParser(description='project mirroring')

    parser.add_argument('project')
    parser.add_argument('-D', '--debug', action='store_true',
                        help='Enable debug prints')
    parser.add_argument('-c', '--config',
                        help='config file in JSON/YAML format')
    parser.add_argument('-U', '--uri', default='http://localhost:8080/source',
                        help='uri of the webapp with context path')
    parser.add_argument('-b', '--batch', action='store_true',
                        help='batch mode - will log into a file')
    parser.add_argument('-B', '--backupcount', default=8,
                        help='how many log files to keep around in batch mode')
    parser.add_argument('-I', '--incoming', action='store_true',
                        help='Check for incoming changes, terminate the '
                             'processing if not found.')
    args = parser.parse_args()

    loglevel = logging.INFO
    if args.debug:
        loglevel = logging.DEBUG
    logger = get_console_logger(__name__, loglevel)

    if args.config:
        config = read_config(logger, args.config)
        if config is None:
            logger.error("Cannot read config file from {}".format(args.config))
            sys.exit(1)
    else:
        config = {}

    GLOBAL_TUNABLES = [HOOKDIR_PROPERTY, PROXY_PROPERTY, LOGDIR_PROPERTY,
                       COMMANDS_PROPERTY, PROJECTS_PROPERTY,
                       HOOK_TIMEOUT_PROPERTY, CMD_TIMEOUT_PROPERTY]
    diff = diff_list(config.keys(), GLOBAL_TUNABLES)
    if diff:
        logger.error("unknown global configuration option(s): '{}'"
                     .format(diff))
        sys.exit(1)

    # Make sure the log directory exists.
    logdir = config.get(LOGDIR_PROPERTY)
    if logdir:
        check_create_dir(logger, logdir)

    uri = args.uri
    if not is_web_uri(uri):
        logger.error("Not a URI: {}".format(uri))
        sys.exit(1)
    logger.debug("web application URI = {}".format(uri))

    source_root = get_config_value(logger, 'sourceRoot', uri)
    if not source_root:
        sys.exit(1)

    logger.debug("Source root = {}".format(source_root))

    project_config = None
    projects = config.get(PROJECTS_PROPERTY)
    if projects:
        if projects.get(args.project):
            project_config = projects.get(args.project)
        else:
            for proj in projects.keys():
                try:
                    pattern = re.compile(proj)
                except re.error:
                    logger.error("Not a valid regular expression: {}".
                                 format(proj))
                    continue

                if pattern.match(args.project):
                    logger.debug("Project '{}' matched pattern '{}'".
                                 format(args.project, proj))
                    project_config = projects.get(proj)
                    break

    hookdir = config.get(HOOKDIR_PROPERTY)
    if hookdir:
        logger.debug("Hook directory = {}".format(hookdir))

    command_timeout = get_int(logger, "command timeout",
                              config.get(CMD_TIMEOUT_PROPERTY))
    if command_timeout:
        logger.debug("Global command timeout = {}".format(command_timeout))

    hook_timeout = get_int(logger, "hook timeout",
                           config.get(HOOK_TIMEOUT_PROPERTY))
    if hook_timeout:
        logger.debug("Global hook timeout = {}".format(hook_timeout))

    prehook = None
    posthook = None
    use_proxy = False
    ignored_repos = None
    if project_config:
        logger.debug("Project '{}' has specific (non-default) config".
                     format(args.project))

        # Quick sanity check.
        KNOWN_PROJECT_TUNABLES = [DISABLED_PROPERTY, CMD_TIMEOUT_PROPERTY,
                                  HOOK_TIMEOUT_PROPERTY, PROXY_PROPERTY,
                                  IGNORED_REPOS_PROPERTY, HOOKS_PROPERTY]
        diff = diff_list(project_config.keys(), KNOWN_PROJECT_TUNABLES)
        if diff:
            logger.error("unknown project configuration option(s) '{}' "
                         "for project {}".format(diff, args.project))
            sys.exit(1)

        project_command_timeout = get_int(logger, "command timeout for "
                                                  "project {}".
                                          format(args.project),
                                          project_config.
                                          get(CMD_TIMEOUT_PROPERTY))
        if project_command_timeout:
            command_timeout = project_command_timeout
            logger.debug("Project command timeout = {}".
                         format(command_timeout))

        project_hook_timeout = get_int(logger, "hook timeout for "
                                               "project {}".
                                       format(args.project),
                                       project_config.
                                       get(HOOK_TIMEOUT_PROPERTY))
        if project_hook_timeout:
            hook_timeout = project_hook_timeout
            logger.debug("Project hook timeout = {}".
                         format(hook_timeout))

        ignored_repos = project_config.get(IGNORED_REPOS_PROPERTY)
        if ignored_repos:
            if not isinstance(ignored_repos, list):
                logger.error("{} for project {} is not a list".
                             format(IGNORED_REPOS_PROPERTY, args.project))
                sys.exit(1)
            logger.debug("has ignored repositories: {}".
                         format(ignored_repos))

        hooks = project_config.get(HOOKS_PROPERTY)
        if hooks:
            if not hookdir:
                logger.error("Need to have '{}' in the configuration "
                             "to run hooks".format(HOOKDIR_PROPERTY))
                sys.exit(1)

            if not os.path.isdir(hookdir):
                logger.error("Not a directory: {}".format(hookdir))
                sys.exit(1)

            for hookname in hooks:
                if hookname == "pre":
                    prehook = hookpath = os.path.join(hookdir, hooks['pre'])
                    logger.debug("pre-hook = {}".format(prehook))
                elif hookname == "post":
                    posthook = hookpath = os.path.join(hookdir, hooks['post'])
                    logger.debug("post-hook = {}".format(posthook))
                else:
                    logger.error("Unknown hook name {} for project {}".
                                 format(hookname, args.project))
                    sys.exit(1)

                if not is_exe(hookpath):
                    logger.error("hook file {} does not exist or not "
                                 "executable".format(hookpath))
                    sys.exit(1)

        if project_config.get(PROXY_PROPERTY):
            if not config.get(PROXY_PROPERTY):
                logger.error("global proxy setting is needed in order to"
                             "have per-project proxy")
                sys.exit(1)

            logger.debug("will use proxy")
            use_proxy = True

    if not ignored_repos:
        ignored_repos = []

    # Log messages to dedicated log file if running in batch mode.
    if args.batch:
        if not logdir:
            logger.error("The logdir property is required in batch mode")
            sys.exit(1)

        logfile = os.path.join(logdir, args.project + ".log")
        logger.debug("Switching logging to the {} file".
                     format(logfile))

        logger = logger.getChild("rotating")
        logger.setLevel(logging.DEBUG if args.debug
                        else logging.INFO)
        logger.propagate = False
        handler = RotatingFileHandler(logfile, maxBytes=0, mode='a',
                                      backupCount=args.backupcount)
        formatter = logging.Formatter("%(asctime)s - %(levelname)s: "
                                      "%(message)s", '%m/%d/%Y %I:%M:%S %p')
        handler.setFormatter(formatter)
        handler.doRollover()
        logger.addHandler(handler)

    # We want this to be logged to the log file (if any).
    if project_config:
        if project_config.get(DISABLED_PROPERTY):
            logger.info("Project {} disabled, exiting".
                        format(args.project))
            sys.exit(CONTINUE_EXITVAL)

    lock = FileLock(os.path.join(tempfile.gettempdir(),
                                 args.project + "-mirror.lock"))
    try:
        with lock.acquire(timeout=0):
            proxy = config.get(PROXY_PROPERTY) if use_proxy else None

            #
            # Cache the repositories first. This way it will be known that
            # something is not right, avoiding any needless pre-hook run.
            #
            repos = []
            try:
                repos = get_repos_for_project(logger, args.project,
                                              ignored_repos,
                                              commands=config.
                                              get(COMMANDS_PROPERTY),
                                              proxy=proxy,
                                              command_timeout=command_timeout,
                                              source_root=source_root,
                                              uri=uri)
            except RepositoryException:
                logger.error('failed to get repositories for project {}'.
                             format(args.project))
                sys.exit(1)

            if not repos:
                logger.info("No repositories for project {}".
                            format(args.project))
                sys.exit(CONTINUE_EXITVAL)

            # Check if any of the repositories contains incoming changes.
            if args.incoming:
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
                                     'repository {}, driving on'.format(repo))

                if not got_incoming:
                    logger.info('No incoming changes for repositories in '
                                'project {}'.
                                format(args.project))
                    sys.exit(CONTINUE_EXITVAL)

            if prehook:
                logger.info("Running pre hook")
                if run_hook(logger, prehook,
                            os.path.join(source_root, args.project), proxy,
                            hook_timeout) != 0:
                    logger.error("pre hook failed for project {}".
                                 format(args.project))
                    logging.shutdown()
                    sys.exit(1)

            #
            # If one of the repositories fails to sync, the whole project sync
            # is treated as failed, i.e. the program will return 1.
            #
            for repo in repos:
                logger.info("Synchronizing repository {}".
                            format(repo.path))
                if repo.sync() != 0:
                    logger.error("failed to sync repository {}".
                                 format(repo.path))
                    ret = 1

            if posthook:
                logger.info("Running post hook")
                if run_hook(logger, posthook,
                            os.path.join(source_root, args.project), proxy,
                            hook_timeout) != 0:
                    logger.error("post hook failed for project {}".
                                 format(args.project))
                    logging.shutdown()
                    sys.exit(1)
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(1)

    logging.shutdown()
    sys.exit(ret)


if __name__ == '__main__':
    main()
