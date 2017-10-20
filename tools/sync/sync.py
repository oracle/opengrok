#!/usr/bin/env python3.4
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
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
#

"""
 This script runs OpenGrok parallel project processing.

 It is intended to work on Unix systems. (mainly because it relies on the
 OpenGrok shell script - for the time being)

"""


from multiprocessing import Pool, TimeoutError
import argparse
import subprocess
import time
import os
import sys
from os import path
import filelock
from filelock import Timeout
import command
from command import Command
import logging
import tempfile
import json
import commands
from commands import Commands, CommandsBase


major_version = sys.version_info[0]
if (major_version < 3):
    print("Need Python 3, you are running {}".format(major_version))
    sys.exit(1)

__version__ = "0.2.2"


def worker(base):
    """
    Process one project by calling set of commands.
    """

    x = Commands(base)
    logger.debug(str(os.getpid()) + " " + str(x))
    x.run()
    base.fill(x.retcodes, x.outputs, x.failed)

    return base

if __name__ == '__main__':
    output = []
    dirs_to_process = []

    parser = argparse.ArgumentParser(description='Manage parallel workers.')
    parser.add_argument('-w', '--workers', default=4,
                        help='Number of worker processes')

    # There can be only one way how to supply list of projects to process.
    group1 = parser.add_mutually_exclusive_group()
    group1.add_argument('-d', '--directory', default="/var/opengrok/src",
                        help='Directory to process')
    group1.add_argument('-P', '--projects', nargs='*',
                        help='List of projects to process')

    group2 = parser.add_mutually_exclusive_group()
    group2.add_argument('-D', '--debug', action='store_true',
                        help='Enable debug prints')
    group2.add_argument('-p', '--logplain', action='store_true',
                        help='log plain messages')

    parser.add_argument('-i', '--ignore_errors', nargs='*',
                        help='ignore errors from these projects')
    parser.add_argument('-c', '--config', required=True,
                        help='config file in JSON format')
    parser.add_argument('-I', '--indexed', action='store_true',
                        help='Sync indexed projects only')
    args = parser.parse_args()

    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
    else:
        if args.logplain:
            logging.basicConfig(format="%(message)s")
        else:
            logging.basicConfig()

    logger = logging.getLogger(os.path.basename(sys.argv[0]))

    try:
        with open(args.config) as json_data_file:
            try:
                config = json.load(json_data_file)
            except ValueError as e:
                logger.error("cannot decode {}".format(args.config))
                sys.exit(1)
            else:
                logger.debug("config: {}".format(config))
    except IOError as e:
        logger.error("cannot open '{}'".format(args.config))
        sys.exit(1)

    try:
        foo = config["commands"]
    except KeyError:
        logger.error("The config file has to contain key \"commands\"")
        sys.exit(1)

    ignore_errors = []
    if args.ignore_errors:
        ignore_errors = args.ignore_errors
    else:
        try:
            ignore_errors = config["ignore_errors"]
        except KeyError:
            pass
    logger.debug("Ignored projects: {}".format(ignore_errors))

    lock = filelock.FileLock(os.path.join(tempfile.gettempdir(),
                             "opengrok-sync.lock"))
    try:
        with lock.acquire(timeout=0):
            pool = Pool(processes=int(args.workers))

            if args.projects:
                dirs_to_process = args.projects
            elif args.indexed:
                # XXX replace this with REST request after issue #1801
                cmd = Command(['/usr/opengrok/bin/Messages', '-n', 'project',
                               'list-indexed'])
                cmd.execute()
                if cmd.state is "finished":
                    for line in cmd.getoutput():
                        dirs_to_process.append(line.strip())
                else:
                    logger.error("cannot get list of projects")
                    sys.exit(1)
            else:
                directory = args.directory
                for entry in os.listdir(directory):
                    if path.isdir(path.join(directory, entry)):
                        dirs_to_process.append(entry)

            logger.debug("to process: {}".format(dirs_to_process))

            projects = []
            for d in dirs_to_process:
                proj = CommandsBase(d, config["commands"])
                projects.append(proj)

            try:
                projects = pool.map(worker, projects, 1)
            except KeyboardInterrupt:
                # XXX lock.release() or return 1 ?
                sys.exit(1)
            else:
                # XXX move the code below to Commands once it can work with
                # logger
                for proj in projects:
                    logger.debug("Output from {}:".format(proj.name))
                    for cmd in proj.outputs.keys():
                        if proj.outputs[cmd] and len(proj.outputs[cmd]) > 0:
                            logger.debug("{}: {}".
                                         format(cmd, proj.outputs[cmd]))

                    if proj.name in ignore_errors:
                        continue

                    if any(rv != 0 for rv in proj.retcodes.values()):
                        logger.error("processing of project {} failed".
                                     format(proj))
                        indent = "  "
                        logger.error("{}failed commands:".format(indent))
                        failed_cmds = {k: v for k, v in
                                       proj.retcodes.items() if v != 0}
                        indent = "    "
                        for cmd in failed_cmds.keys():
                            logger.error("{}'{}': {}".
                                         format(indent, cmd, failed_cmds[cmd]))
                            out = proj.get_cmd_output(cmd,
                                                      indent=indent + "  ")
                            if out:
                                logger.error(out)
                        logger.error("")

                    errored_cmds = {k: v for k, v in proj.outputs.items()
                                    if "error" in str(v).lower()}
                    if len(errored_cmds) > 0:
                        logger.error("Command output in project {}"
                                     " contains errors:".format(proj.name))
                        indent = "  "
                        for cmd in errored_cmds.keys():
                            logger.error("{}{}".format(indent, cmd))
                            out = proj.get_cmd_output(cmd,
                                                      indent=indent + "  ")
                            if out:
                                logger.error(out)
                            logger.error("")
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(1)
