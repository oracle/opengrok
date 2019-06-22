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
import logging
import os
import sys
import tempfile
from multiprocessing import Pool, cpu_count

from filelock import Timeout, FileLock

from .utils.log import get_console_logger, get_class_basename, \
    fatal, get_batch_logger
from .utils.opengrok import get_config_value, list_indexed_projects
from .utils.parsers import get_baseparser
from .utils.readconfig import read_config
from .utils.utils import get_int, is_web_uri
from .utils.mirror import check_configuration, LOGDIR_PROPERTY, \
    mirror_project, HOOKDIR_PROPERTY, CMD_TIMEOUT_PROPERTY, \
    HOOK_TIMEOUT_PROPERTY

major_version = sys.version_info[0]
if major_version < 3:
    fatal("Need Python 3, you are running {}".format(major_version))

__version__ = "0.8"


def worker(args):
    project_name, logdir, loglevel, backupcount, config, incoming, uri, \
        source_root, batch = args

    if batch:
        get_batch_logger(logdir, project_name,
                         loglevel,
                         backupcount,
                         get_class_basename())

    return mirror_project(config, project_name,
                          incoming,
                          uri, source_root)


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
    parser.add_argument('-w', '--workers', default=cpu_count(),
                        help='Number of worker processes')

    try:
        args = parser.parse_args()
    except ValueError as e:
        fatal(e)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    if len(args.project) > 0 and args.all:
        fatal("Cannot use both project list and -a/--all")

    if not args.all and len(args.project) == 0:
        fatal("Need at least one project or --all")

    if args.config:
        config = read_config(logger, args.config)
        if config is None:
            fatal("Cannot read config file from {}".format(args.config))
    else:
        config = {}

    uri = args.uri
    if not is_web_uri(uri):
        fatal("Not a URI: {}".format(uri))
    logger.debug("web application URI = {}".format(uri))

    if not check_configuration(config):
        sys.exit(1)

    # Save the source root to avoid querying the web application.
    source_root = get_config_value(logger, 'sourceRoot', uri)
    if not source_root:
        sys.exit(1)

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
        logdir = config.get(LOGDIR_PROPERTY)
        if not logdir:
            fatal("The {} property is required in batch mode".
                  format(LOGDIR_PROPERTY))

    projects = args.project
    if len(projects) == 1:
        lockfile = projects[0] + "-mirror"
    else:
        lockfile = os.path.basename(sys.argv[0])

    if args.all:
        projects = list_indexed_projects(logger, args.uri)

    lock = FileLock(os.path.join(tempfile.gettempdir(), lockfile + ".lock"))
    try:
        with lock.acquire(timeout=0):
            with Pool(processes=int(args.workers)) as pool:
                worker_args = []
                for x in projects:
                    worker_args.append([x, logdir, args.loglevel,
                                        args.backupcount, config,
                                        args.incoming,
                                        args.uri, source_root,
                                        args.batch])
                try:
                    project_results = pool.map(worker, worker_args, 1)
                except KeyboardInterrupt:
                    sys.exit(1)
                else:
                    if any([True for x in project_results if x == 1]):
                        ret = 1
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(1)

    logging.shutdown()
    sys.exit(ret)


if __name__ == '__main__':
    main()
