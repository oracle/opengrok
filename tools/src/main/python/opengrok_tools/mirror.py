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
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>
#

"""
 This script performs mirroring of single OpenGrok project.

 It is intended to work on Unix systems.

"""

import argparse
import logging
import os
import sys
import tempfile
from multiprocessing import Pool, cpu_count

from filelock import Timeout, FileLock

from .utils.exitvals import (
    FAILURE_EXITVAL,
    CONTINUE_EXITVAL,
    SUCCESS_EXITVAL
)
from .utils.log import get_console_logger, get_class_basename, \
    fatal, get_batch_logger
from .utils.opengrok import get_config_value, list_indexed_projects
from .utils.parsers import get_base_parser, add_http_headers, get_headers
from .utils.readconfig import read_config
from .utils.utils import get_int, is_web_uri
from .utils.mirror import check_configuration, LOGDIR_PROPERTY, \
    mirror_project, HOOKDIR_PROPERTY, CMD_TIMEOUT_PROPERTY, \
    HOOK_TIMEOUT_PROPERTY

major_version = sys.version_info[0]
if major_version < 3:
    fatal("Need Python 3, you are running {}".format(major_version))

__version__ = "1.0"


def worker(args):
    project_name, logdir, loglevel, backupcount, config, check_changes, uri, \
        source_root, batch, headers = args

    if batch:
        get_batch_logger(logdir, project_name,
                         loglevel,
                         backupcount,
                         get_class_basename())

    return mirror_project(config, project_name,
                          check_changes,
                          uri, source_root, headers=headers)


def main():
    ret = SUCCESS_EXITVAL

    parser = argparse.ArgumentParser(description='project mirroring',
                                     parents=[get_base_parser(
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
    parser.add_argument('-L', '--logdir',
                        help='log directory')
    parser.add_argument('-B', '--backupcount', default=8,
                        help='how many log files to keep around in batch mode')
    parser.add_argument('-I', '--check-changes', action='store_true',
                        help='Check for changes in the project or its'
                             ' repositories,'
                             ' terminate the processing'
                             ' if no change is found.')
    parser.add_argument('-w', '--workers', default=cpu_count(),
                        help='Number of worker processes')
    add_http_headers(parser)

    try:
        args = parser.parse_args()
    except ValueError as e:
        return fatal(e, False)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    headers = get_headers(args.header)

    nomirror = os.environ.get("OPENGROK_NO_MIRROR")
    if nomirror and len(nomirror) > 0:
        logger.debug("skipping mirror based on the OPENGROK_NO_MIRROR " +
                     "environment variable")
        return SUCCESS_EXITVAL

    if len(args.project) > 0 and args.all:
        return fatal("Cannot use both project list and -a/--all", False)

    if not args.all and len(args.project) == 0:
        return fatal("Need at least one project or --all", False)

    if args.config:
        config = read_config(logger, args.config)
        if config is None:
            return fatal("Cannot read config file from {}".
                         format(args.config), False)
    else:
        config = {}

    uri = args.uri
    if not is_web_uri(uri):
        return fatal("Not a URI: {}".format(uri), False)
    logger.debug("web application URI = {}".format(uri))

    if not check_configuration(config):
        return 1

    # Save the source root to avoid querying the web application.
    source_root = get_config_value(logger, 'sourceRoot', uri,
                                   headers=headers)
    if not source_root:
        return 1

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

    logdir = None
    # Log messages to dedicated log file if running in batch mode.
    if args.batch:
        if args.logdir:
            logdir = args.logdir
        else:
            logdir = config.get(LOGDIR_PROPERTY)
            if not logdir:
                return fatal("The {} property is required in batch mode".
                             format(LOGDIR_PROPERTY), False)

    projects = args.project
    if len(projects) == 1:
        lockfile = projects[0] + "-mirror"
    else:
        lockfile = os.path.basename(sys.argv[0])

    if args.all:
        projects = list_indexed_projects(logger, args.uri, headers=headers)

    lock = FileLock(os.path.join(tempfile.gettempdir(), lockfile + ".lock"))
    try:
        with lock.acquire(timeout=0):
            with Pool(processes=int(args.workers)) as pool:
                worker_args = []
                for x in projects:
                    worker_args.append([x, logdir, args.loglevel,
                                        args.backupcount, config,
                                        args.check_changes,
                                        args.uri, source_root,
                                        args.batch, headers])
                try:
                    project_results = pool.map(worker, worker_args, 1)
                except KeyboardInterrupt:
                    return FAILURE_EXITVAL
                else:
                    if any([x == FAILURE_EXITVAL for x in project_results]):
                        ret = FAILURE_EXITVAL
                    if all([x == CONTINUE_EXITVAL for x in project_results]):
                        ret = CONTINUE_EXITVAL
    except Timeout:
        logger.warning("Already running, exiting.")
        return FAILURE_EXITVAL

    logging.shutdown()
    return ret


if __name__ == '__main__':
    sys.exit(main())
