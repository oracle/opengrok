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
# Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
#

"""
 This script runs OpenGrok parallel project processing.
"""

import argparse
import multiprocessing
import os
import sys
import tempfile
from multiprocessing import Pool
from os import path

from filelock import Timeout, FileLock

from .utils.commandsequence import CommandSequence, CommandSequenceBase
from .utils.log import get_console_logger, get_class_basename, fatal
from .utils.opengrok import list_indexed_projects, get_config_value
from .utils.parsers import get_base_parser, add_http_headers, get_headers
from .utils.readconfig import read_config
from .utils.utils import is_web_uri
from .utils.exitvals import (
    FAILURE_EXITVAL,
    SUCCESS_EXITVAL
)

major_version = sys.version_info[0]
if (major_version < 3):
    print("Need Python 3, you are running {}".format(major_version))
    sys.exit(1)

__version__ = "1.3"


def worker(base):
    """
    Process one project by calling set of commands.
    """

    x = CommandSequence(base)
    x.run()
    base.fill(x.retcodes, x.outputs, x.failed)

    return base


def do_sync(loglevel, commands, cleanup, dirs_to_process, ignore_errors,
            uri, numworkers, driveon=False, print_output=False, logger=None,
            http_headers=None, timeout=None):
    """
    Process the list of directories in parallel.
    :param logger: logger to be used in this function
    :param loglevel: log level used for executed commands
    :param commands: commands structure
    :param cleanup: cleanup commands
    :param dirs_to_process: directories
    :param ignore_errors: ignore errors for these projects
    :param uri: web app URI
    :param numworkers: number of pool workers
    :param driveon: continue even if encountering failure
    :param print_output: whether to print the output of the commands
                         using the supplied logger
    :param logger: optional logger
    :param http_headers: optional dictionary of HTTP headers
    :param timeout: optional timeout in seconds for API call response
    :return SUCCESS_EXITVAL on success, FAILURE_EXITVAL on error
    """

    cmds_base = []
    for dir in dirs_to_process:
        cmd_base = CommandSequenceBase(dir, commands, loglevel=loglevel,
                                       cleanup=cleanup,
                                       driveon=driveon, url=uri,
                                       http_headers=http_headers,
                                       api_timeout=timeout)
        cmds_base.append(cmd_base)

    # Map the commands into pool of workers so they can be processed.
    retval = SUCCESS_EXITVAL
    with Pool(processes=numworkers) as pool:
        try:
            cmds_base_results = pool.map(worker, cmds_base, 1)
        except KeyboardInterrupt:
            return FAILURE_EXITVAL
        else:
            for cmds_base in cmds_base_results:
                cmds = CommandSequence(cmds_base)
                cmds.fill(cmds_base.retcodes, cmds_base.outputs,
                          cmds_base.failed)
                r = cmds.check(ignore_errors)
                if r != SUCCESS_EXITVAL:
                    retval = FAILURE_EXITVAL

                if print_output and logger:
                    cmds.print_outputs(logger, lines=True)

    return retval


def main():
    parser = argparse.ArgumentParser(description='Manage parallel workers.',
                                     parents=[
                                         get_base_parser(
                                             tool_version=__version__)
                                     ])
    parser.add_argument('-w', '--workers', default=multiprocessing.cpu_count(),
                        help='Number of worker processes', type=int)

    # There can be only one way how to supply list of projects to process.
    group1 = parser.add_mutually_exclusive_group()
    group1.add_argument('-d', '--directory',
                        help='Directory to process')
    group1.add_argument('-P', '--project', nargs='*',
                        help='project(s) to process')

    parser.add_argument('-I', '--indexed', action='store_true',
                        help='Sync indexed projects only')
    parser.add_argument('-i', '--ignore_errors', nargs='*',
                        help='ignore errors from these projects')
    parser.add_argument('--ignore_project', nargs='+',
                        help='do not process given project(s)')
    parser.add_argument('-c', '--config', required=True,
                        help='config file in JSON/YAML format')
    parser.add_argument('-U', '--uri', default='http://localhost:8080/source',
                        help='URI of the webapp with context path')
    parser.add_argument('-f', '--driveon', action='store_true', default=False,
                        help='continue command sequence processing even '
                        'if one of the commands requests break')
    parser.add_argument('--nolock', action='store_false', default=True,
                        help='do not acquire lock that prevents multiple '
                        'instances from running')
    parser.add_argument('--api_timeout', type=int,
                        help='Set response timeout in seconds'
                        'for RESTful API calls')
    add_http_headers(parser)

    try:
        args = parser.parse_args()
    except ValueError as e:
        return fatal(e, exit=False)

    logger = get_console_logger(get_class_basename(), args.loglevel)

    uri = args.uri
    if not is_web_uri(uri):
        logger.error("Not a URI: {}".format(uri))
        return FAILURE_EXITVAL
    logger.debug("web application URI = {}".format(uri))

    # First read and validate configuration file as it is mandatory argument.
    config = read_config(logger, args.config)
    if config is None:
        logger.error("Cannot read config file from {}".format(args.config))
        return FAILURE_EXITVAL

    # Changing working directory to root will avoid problems when running
    # programs via sudo/su. Do this only after the config file was read
    # so that its path can be specified as relative.
    try:
        os.chdir("/")
    except OSError:
        logger.error("cannot change working directory to /",
                     exc_info=True)
        return FAILURE_EXITVAL

    try:
        commands = config["commands"]
    except KeyError:
        logger.error("The config file has to contain key \"commands\"")
        return FAILURE_EXITVAL

    headers = get_headers(args.header)
    config_headers = config.get("headers")
    if config_headers:
        logger.debug("Updating HTTP headers with headers from the configuration: {}".
                     format(config_headers))
        headers.update(config_headers)

    directory = args.directory
    if not args.directory and not args.project and not args.indexed:
        # Assume directory, get the source root value from the webapp.
        directory = get_config_value(logger, 'sourceRoot', uri,
                                     headers=headers, timeout=args.api_timeout)
        if not directory:
            logger.error("Neither -d or -P or -I specified and cannot get "
                         "source root from the webapp")
            return FAILURE_EXITVAL
        else:
            logger.info("Assuming directory: {}".format(directory))

    ignore_errors = []
    if args.ignore_errors:
        ignore_errors = args.ignore_errors
    else:
        try:
            ignore_errors = config["ignore_errors"]
        except KeyError:
            pass
    logger.debug("Ignoring errors from projects: {}".format(ignore_errors))

    dirs_to_process = []
    if args.project:
        dirs_to_process = args.project
        logger.debug("Processing directories: {}".
                     format(dirs_to_process))
    elif args.indexed:
        indexed_projects = list_indexed_projects(logger, uri,
                                                 headers=headers,
                                                 timeout=args.api_timeout)
        logger.debug("Processing indexed projects: {}".
                     format(indexed_projects))

        if indexed_projects:
            for line in indexed_projects:
                dirs_to_process.append(line.strip())
        else:
            logger.error("cannot get list of projects")
            return FAILURE_EXITVAL
    else:
        logger.debug("Processing directory {}".format(directory))
        for entry in os.listdir(directory):
            if path.isdir(path.join(directory, entry)):
                dirs_to_process.append(entry)

    ignored_projects = []
    config_ignored_projects = config.get("ignore_projects")
    if config_ignored_projects:
        logger.debug("Updating list of ignored projects list from the configuration: {}".
                     format(config_ignored_projects))
        ignored_projects.extend(config_ignored_projects)

    if args.ignore_project:
        logger.debug("Updating list of ignored projects based on options: {}".
                     format(args.ignore_project))
        ignored_projects.extend(args.ignore_project)

    if ignored_projects:
        dirs_to_process = list(set(dirs_to_process) - set(ignored_projects))
        logger.debug("Removing projects: {}".format(ignored_projects))

    logger.debug("directories to process: {}".format(dirs_to_process))

    if args.project and len(args.project) == 1:
        lockfile_name = args.project[0]
    else:
        lockfile_name = os.path.basename(sys.argv[0])

    if args.nolock:
        r = do_sync(args.loglevel, commands, config.get("cleanup"),
                    dirs_to_process,
                    ignore_errors, uri, args.workers,
                    driveon=args.driveon, http_headers=headers,
                    timeout=args.api_timeout)
    else:
        lock = FileLock(os.path.join(tempfile.gettempdir(),
                                     lockfile_name + ".lock"))
        try:
            with lock.acquire(timeout=0):
                r = do_sync(args.loglevel, commands, config.get("cleanup"),
                            dirs_to_process,
                            ignore_errors, uri, args.workers,
                            driveon=args.driveon, http_headers=headers,
                            timeout=args.api_timeout)
        except Timeout:
            logger.warning("Already running")
            return FAILURE_EXITVAL
        finally:
            if path.exists(lock.lock_file): os.remove(lock.lock_file)

    return r


if __name__ == '__main__':
    sys.exit(main())
