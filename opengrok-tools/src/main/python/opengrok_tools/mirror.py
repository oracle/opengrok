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

"""
 This script performs mirroring of single OpenGrok project.

 It is intended to work on Unix systems.

"""

import argparse
import fnmatch
import logging
import os
import re
import sys
import tempfile
from logging.handlers import RotatingFileHandler

from filelock import Timeout, FileLock

from .scm.repofactory import get_repository
from .scm.repository import RepositoryException
from .utils.hook import run_hook
from .utils.log import get_console_logger, get_class_basename, \
    print_exc_exit
from .utils.opengrok import get_repos, get_config_value, get_repo_type, \
    list_indexed_projects
from .utils.parsers import get_baseparser
from .utils.readconfig import read_config
from .utils.utils import is_exe, check_create_dir, get_int, diff_list, \
    is_web_uri

major_version = sys.version_info[0]
if major_version < 3:
    print("Need Python 3, you are running {}".format(major_version))
    sys.exit(1)

__version__ = "0.7"

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


def get_repos_for_project(project_name, ignored_repos, **kwargs):
    """
    :param project_name: project name
    :param ignored_repos: list of ignored repositories
    :param kwargs: argument dictionary
    :return: list of Repository objects
    """

    logger = logging.getLogger(__name__)

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

        repo = get_repository(logger,
                              # Not joining the path since the form
                              # of repo_path is absolute path.
                              kwargs['source_root'] + repo_path,
                              repo_type,
                              project_name,
                              kwargs[COMMANDS_PROPERTY],
                              kwargs[PROXY_PROPERTY],
                              None,
                              kwargs['command_timeout'])
        if not repo:
            raise RepositoryException("Cannot get repository for {}".
                                      format(repo_path))
        else:
            repos.append(repo)

    return repos


def get_project_config(config, project_name):
    """
    Return per project configuration, if any.
    :param config:
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
            if not isinstance(ignored_repos, list):
                logger.error("{} for project {} is not a list".
                             format(IGNORED_REPOS_PROPERTY, project_name))
                sys.exit(1)  # TODO raise exception instead of exiting
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

    return prehook, posthook, hook_timeout, command_timeout, use_proxy, ignored_repos


def mirror_project(config, project_name, check_incoming, uri, source_root):
    """
    Mirror the repositories of single project.
    :param config global configuration dictionary
    :param project_name:
    :param check_incoming:
    :param uri
    :param source_root
    :return exit code
    """

    logger = logging.getLogger(__name__)

    project_config = get_project_config(config, project_name)
    prehook, posthook, hook_timeout, command_timeout, use_proxy, \
        ignored_repos = \
        get_project_properties(config, project_config,
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
    repos = []
    try:
        repos = get_repos_for_project(project_name,
                                      ignored_repos,
                                      commands=config.
                                      get(COMMANDS_PROPERTY),
                                      proxy=proxy,
                                      command_timeout=command_timeout,
                                      source_root=source_root,
                                      uri=uri)
    except RepositoryException as ex:
        logger.error('failed to get repositories for project {}: {}'.
                     format(project_name, ex))
        return 1

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
            logger.error("failed to sync repository {}".
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


def check_project_configuration(project_config, hookdir, proxy):
    """
    Check configuration of given project
    :param project_config: project configuration dictionary
    :param hookdir: hook directory
    :param proxy: proxy setting
    :return: True if the configuration checks out, False otherwise
    """

    logger = logging.getLogger(__name__)

    # Quick sanity check.
    known_project_tunables = [DISABLED_PROPERTY, CMD_TIMEOUT_PROPERTY,
                              HOOK_TIMEOUT_PROPERTY, PROXY_PROPERTY,
                              IGNORED_REPOS_PROPERTY, HOOKS_PROPERTY]

    if not project_config:
        return True

    for project_name in project_config.keys():
        diff = diff_list(project_config.get(project_name).keys(),
                         known_project_tunables)
        if diff:
            logger.error("unknown project configuration option(s) '{}' "
                         "for project {}".format(diff, project_name))
            return False

        if project_config.get(project_name).get(HOOKS_PROPERTY):
            if not hookdir:
                logger.error("Need to have '{}' in the configuration "
                             "to run hooks".format(HOOKDIR_PROPERTY))
                return False

            if not os.path.isdir(hookdir):
                logger.error("Not a directory: {}".format(hookdir))
                return False

        if not proxy:
            logger.error("global proxy setting is needed in order to"
                         "have per-project proxy")
            return False

        hooks = project_config.get(project_name).get(HOOKS_PROPERTY)
        if hooks:
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


def get_batch_logger(logdir, project_name, loglevel):
    """
    Get rotating file logger for storing logs of mirroring of given project.
    :param logdir: log directory
    :param project_name: name of the project
    :param loglevel: logging level
    :return: logger object
    """

    logger = logging.getLogger(__name__)

    logfile = os.path.join(logdir, project_name + ".log")
    logger.debug("Switching logging to the {} file".
                 format(logfile))

    logger = logger.getChild("rotating")
    logger.setLevel(loglevel)
    logger.propagate = False
    handler = RotatingFileHandler(logfile, maxBytes=0, mode='a',
                                  backupCount=args.backupcount)
    formatter = logging.Formatter("%(asctime)s - %(levelname)s: "
                                  "%(message)s", '%m/%d/%Y %I:%M:%S %p')
    handler.setFormatter(formatter)
    handler.doRollover()
    logger.addHandler(handler)

    return logger


def main():
    ret = 0

    parser = argparse.ArgumentParser(description='project mirroring',
                                     parents=[get_baseparser(
                                         tool_version=__version__)
                                     ])

    parser.add_argument('project', nargs='*', default=None)
    parser.add_argument('-a', '--all', action='store_true',
                        help='mirror all indexed projects', default=False)
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
    try:
        args = parser.parse_args()
    except ValueError as e:
        print_exc_exit(e)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    if len(args.project) > 0 and args.all:
        logger.fatal("Cannot use both project list and -a/--all")
        sys.exit(1)

    if args.config:
        config = read_config(logger, args.config)
        if config is None:
            logger.fatal("Cannot read config file from {}".format(args.config))
            sys.exit(1)
    else:
        config = {}

    uri = args.uri
    if not is_web_uri(uri):
        logger.fatal("Not a URI: {}".format(uri))
        sys.exit(1)
    logger.debug("web application URI = {}".format(uri))

    if not check_configuration(config):
        sys.exit(1)

    # Save the source root to avoid querying the web application.
    source_root = get_config_value(logger, 'sourceRoot', uri)
    if not source_root:
        return False

    logger.debug("Source root = {}".format(source_root))

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

    # Log messages to dedicated log file if running in batch mode.
    if args.batch:
        logdir = config.get(LOGDIR_PROPERTY)
        if not logdir:
            logger.fatal("The {} property is required in batch mode".
                         format(LOGDIR_PROPERTY))
            sys.exit(1)

        logger = get_batch_logger(logdir, args.project, args.loglevel)

    projects = args.project
    if len(args.project) == 1:
        lockfile = args.project[0] + "-mirror"
    else:
        lockfile = os.path.basename(sys.argv[0])

    if args.all:
        projects = list_indexed_projects(logger, args.uri)

    lock = FileLock(os.path.join(tempfile.gettempdir(), lockfile + ".lock"))
    try:
        with lock.acquire(timeout=0):
            for project in projects:
                project_result = mirror_project(config, project, args.incoming,
                                                args.uri, source_root)

                #
                # If there is just one project, treat it as if running from
                # within the sync.py script and exit with appropriate return
                # code. Otherwise accumulate failures.
                #
                if len(args.project) == 1:
                    sys.exit(project_result)
                elif project_result == 1:
                    ret = 1
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(1)

    logging.shutdown()
    sys.exit(ret)


if __name__ == '__main__':
    main()
