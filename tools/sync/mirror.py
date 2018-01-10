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
 This script performs mirroring of single OpenGrok project.

 It is intended to work on Unix systems.

"""


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
import commands
from commands import Commands, CommandsBase
from repository import Repository
from mercurial import MercurialRepository
from repofactory import get_repository
from utils import which, is_exe, check_create_dir, get_dict_val
from hook import run_hook
from readconfig import read_config
from opengrok import get_repos, get_config_value, get_repo_type


major_version = sys.version_info[0]
if (major_version < 3):
    print("Need Python 3, you are running {}".format(major_version))
    sys.exit(1)

__version__ = "0.1"


if __name__ == '__main__':
    ret = 0
    output = []
    dirs_to_process = []

    parser = argparse.ArgumentParser(description='project mirroring')

    parser.add_argument('project')
    parser.add_argument('-D', '--debug', action='store_true',
                        help='Enable debug prints')
    parser.add_argument('-c', '--config', required=True,
                        help='config file in JSON/YAML format')
    parser.add_argument('-m', '--messages',
                        help='path to the Messages binary')
    parser.add_argument('-b', '--batch', action='store_true',
                        help='batch mode - will log into a file')
    args = parser.parse_args()

    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig()

    logger = logging.getLogger(os.path.basename(sys.argv[0]))

    config = read_config(logger, args.config)
    if config is None:
        logger.error("Cannot read config file from {}".format(args.config))
        sys.exit(1)

    # Make sure the log directory exists.
    try:
        logdir = config["logdir"]
    except:
        logger.error("'logdir' does not exist in configuration")
        sys.exit(1)
    else:
        check_create_dir(logdir)

    if args.messages:
        messages_file = which(args.messages)
        if not messages_file:
            logger.error("file {} does not exist".format(args.messages))
            sys.exit(1)
    else:
        messages_file = which("Messages")
        if not messages_file:
            logger.error("cannot determine path to Messages")
            sys.exit(1)
    logger.debug("Messages = {}".format(messages_file))

    source_root = get_config_value(logger, 'sourceRoot', messages_file)
    if not source_root:
        logger.error("Cannot get the sourceRoot config value")
        sys.exit(1)

    logger.debug("Source root = {}".format(source_root))

    project_config = None
    try:
        if config['projects']:
            if config['projects'][args.project]:
                project_config = config['projects'][args.project]
    except:
        # The project has no config, that's fine - defaults will be used.
        pass

    hookdir = get_dict_val(config, 'hookdir')
    if hookdir:
        logger.debug("Hook directory = {}".format(hookdir))

    prehook = None
    posthook = None
    if project_config:
        try:
            if not hookdir:
                logger.error("Need to have 'hookdir' in the configuration " +
                    "to run hooks")
                sys.exit(1)

            if not os.path.isdir(hookdir):
                logger.error("Not a directory: {}".format(hookdir))
                sys.exit(1)

            hooks = project_config['hooks']
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
                    logger.error("hook file {} does not exist or not executable".
                                 format(hookpath))
                    sys.exit(1)
        except KeyError:
            pass

    use_proxy = False
    if project_config:
        try:
            if project_config['proxy']:
                use_proxy = True
        except KeyError:
            pass

    # Log messages to dedicated log file if running in batch mode.
    if args.batch:
        logging.shutdown()

        # Remove the existing handler so that logger can be reconfigured.
        for handler in logging.root.handlers[:]:
            logging.root.removeHandler(handler)

        logging.basicConfig(filename = os.path.join(logdir,
                            args.project + ".log"), filemode = 'a',
                            format = '%(asctime)s - %(levelname)s: %(message)s',
                            datefmt = '%m/%d/%Y %I:%M:%S %p',
                            level = logging.DEBUG if args.debug else logging.INFO)
        logger = logging.getLogger(os.path.basename(sys.argv[0]))

    # We want this to be logged to the log file (if any).
    if project_config:
        try:
            if project_config['disabled']:
                logger.info("Project {} disabled, exiting".format(args.project))
                sys.exit(0)
        except KeyError:
            pass

    lock = filelock.FileLock(os.path.join(tempfile.gettempdir(),
                             args.project + "-mirror.lock"))
    try:
        with lock.acquire(timeout=0):
            if prehook and run_hook(logger, prehook,
                        os.path.join(source_root, args.project)) != 0:
                logger.error("pre hook failed")
                logging.shutdown()
                sys.exit(1)

            #
            # If one of the repositories fails to sync, the whole project sync
            # is treated as failed, i.e. the program will return 1.
            #
            for repo_path in get_repos(logger, args.project, messages_file):
                logger.debug("Repository path = {}".format(repo_path))
                repo_type = get_repo_type(logger, repo_path, messages_file)
                if not repo_type:
                    logger.error("cannot determine type of {}".
                                 format(repopath))
                    continue

                logger.debug("Repository type = {}".format(repo_type))

                repo = get_repository(logger,
                                      source_root + repo_path,
                                      repo_type,
                                      args.project,
                                      get_dict_val(config, 'commands'),
                                      config['proxy'] if use_proxy else None,
                                      None)
                if not repo:
                    logger.error("Cannot get repository for {}".
                                 format(repo_path))
                    ret = 1
                else:
                    if repo.sync() != 0:
                        logger.debug("failed to sync repository {}".
                            format(repo_path))
                        ret = 1

            if posthook and run_hook(logger, posthook,
                        os.path.join(source_root, args.project)) != 0:
                logger.error("post hook failed")
                logging.shutdown()
                sys.exit(1)
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(1)

    logging.shutdown()
    sys.exit(ret)
