#!/usr/bin/env python3

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
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
#

"""
    This script is wrapper of commands to add/remove project or refresh
    configuration using read-only configuration.
"""


import os
from os import path
import sys
import argparse
import filelock
from filelock import Timeout
from command import Command
import logging
import tempfile
import shutil
import stat
from utils import get_command


MAJOR_VERSION = sys.version_info[0]
if (MAJOR_VERSION < 3):
    print("Need Python 3, you are running {}".format(MAJOR_VERSION))
    sys.exit(1)

__version__ = "0.1"


def exec_command(doit, logger, cmd, msg):
    """
    Execute given command and return its output.
    Exit the program on failure.
    """
    cmd = Command(cmd, logger=logger)
    if not doit:
        logger.info(cmd)
        return
    cmd.execute()
    if cmd.getstate() is not Command.FINISHED or cmd.getretcode() != 0:
        logger.error(msg)
        logger.error(cmd.getoutput())
        sys.exit(1)

    return cmd.getoutput()


def get_config_file(basedir):
    """
    Return configuration file in basedir
    """

    return path.join(basedir, "etc", "configuration.xml")


def config_refresh(doit, logger, basedir, messages, configmerge, roconfig):
    """
    Refresh current configuration file with configuration retrieved
    from webapp merged with readonly configuration.

      1. retrieves current configuration from the webapp,
         stores it into temporary file
      2. merge the read-only config with the config from previous step
         - this is done as a workaround for
           https://github.com/oracle/opengrok/issues/2002
    """

    if not roconfig:
        logger.debug("No read-only configuration specified, not refreshing")
        return

    logger.info('Refreshing configuration and merging with read-only '
                'configuration')
    current_config = exec_command(doit, logger,
                                  [messages, '-n', 'config', '-t', 'getconf'],
                                  "getting configuration failed")
    with tempfile.NamedTemporaryFile() as fc:
        logger.debug("Temporary file for current config: {}".format(fc.name))
        if doit:
            fc.write(bytearray(''.join(current_config), "UTF-8"))
        merged_config = exec_command(doit, logger,
                                     [configmerge, roconfig, fc.name],
                                     "cannot merge configuration")
        with tempfile.NamedTemporaryFile() as fm:
            logger.debug("Temporary file for merged config: {}".
                         format(fm.name))
            if doit:
                fm.write(bytearray(''.join(merged_config), "UTF-8"))
            main_config = get_config_file(basedir)
            if path.isfile(main_config):
                if doit:
                    #
                    # Copy the file so that close() triggered unlink()
                    # does not fail.
                    #
                    logger.debug("Copying {} to {}".
                                 format(fm.name, main_config))
                    try:
                        shutil.copyfile(fm.name, main_config)
                    except PermissionError:
                        logger.error('Failed to copy {} to {} (permissions)'.
                                     format(fm.name, main_config))
                        sys.exit(1)
                    except OSError:
                        logger.error('Failed to copy {} to {} (I/O)'.
                                     format(fm.name, main_config))
                        sys.exit(1)
            else:
                logger.error("file {} does not exist".format(main_config))
                sys.exit(1)


def project_add(doit, logger, project, messages):
    """
    Adds a project to configuration. Works in multiple steps:

      1. add the project to configuration
      2. refresh on disk configuration
    """

    logger.info("Adding project {}".format(project))
    exec_command(doit, logger,
                 [messages, '-n', 'project', '-t', project, 'add'],
                 "adding of the project failed")


def project_delete(doit, logger, project, messages):
    """
    Delete the project for configuration and all its data.
    Works in multiple steps:

      1. delete the project from configuration and its indexed data
      2. refresh on disk configuration
      3. delete the source code for the project
    """

    # Be extra careful as we will be recursively removing directory structure.
    if not project or len(project) == 0:
        raise Exception("invalid call to project_delete(): missing project")

    logger.info("Deleting project {} and its index data".format(project))
    exec_command(doit, logger,
                 [messages, '-n', 'project', '-t', project, 'delete'],
                 "deletion of the project failed")

    src_root = exec_command(True, logger,
                            [messages, '-n', 'config', '-t', 'get',
                             'sourceRoot'], "cannot get config")
    src_root = src_root[0].rstrip()
    logger.debug("Source root = {}".format(src_root))
    if not src_root or len(src_root) == 0:
        raise Exception("source root empty")
    sourcedir = path.join(src_root, project)
    logger.debug("Removing directory tree {}".format(sourcedir))
    if doit:
        logger.info("Removing source code under {}".format(sourcedir))
        shutil.rmtree(sourcedir)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='grok configuration '
                                     'management.',
                                     formatter_class=argparse.
                                     ArgumentDefaultsHelpFormatter)
    parser.add_argument('-D', '--debug', action='store_true',
                        help='Enable debug prints')
    parser.add_argument('-b', '--base', default="/var/opengrok",
                        help='OpenGrok instance base directory')
    parser.add_argument('-R', '--roconfig',
                        help='OpenGrok read-only configuration file')
    parser.add_argument('-m', '--messages',
                        help='path to the Messages binary')
    parser.add_argument('-c', '--configmerge',
                        help='path to the ConfigMerge binary')
    parser.add_argument('-u', '--upload', action='store_true',
                        help='Upload configuration at the end')
    parser.add_argument('-n', '--noop', action='store_false', default=True,
                        help='Do not run any commands or modify any config'
                        ', just report. Usually implies the --debug option.')

    group = parser.add_mutually_exclusive_group()
    group.add_argument('-a', '--add', metavar='project', nargs='+',
                       help='Add project (assumes its source is available '
                       'under source root')
    group.add_argument('-d', '--delete', metavar='project', nargs='+',
                       help='Delete project and its data and source code')
    group.add_argument('-r', '--refresh', action='store_true',
                       help='Refresh configuration from read-only '
                       'configuration')

    args = parser.parse_args()

    #
    # Setup logger as a first thing after parsing arguments so that it can be
    # used through the rest of the program.
    #
    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig(format="%(message)s", level=logging.INFO)

    logger = logging.getLogger(os.path.basename(sys.argv[0]))

    # Set the base directory
    if args.base:
        if path.isdir(args.base):
            logger.debug("Using {} as instance base".
                         format(args.base))
        else:
            logger.error("Not a directory: {}".format(args.base))
            sys.exit(1)

    # read-only configuration file.
    if args.roconfig:
        if path.isfile(args.roconfig):
            logger.debug("Using {} as read-only config".format(args.roconfig))
        else:
            logger.error("File {} does not exist".format(args.roconfig))
            sys.exit(1)

    if args.refresh and not args.roconfig:
        logger.error("-r requires -R")
        sys.exit(1)

    # XXX replace Messages with REST request after issue #1801
    messages_file = get_command(logger, args.messages, "Messages")
    configmerge_file = get_command(logger, args.configmerge, "ConfigMerge")

    lock = filelock.FileLock(os.path.join(tempfile.gettempdir(),
                             os.path.basename(sys.argv[0]) + ".lock"))
    try:
        with lock.acquire(timeout=0):
            if args.add:
                for proj in args.add:
                    project_add(doit=args.noop, logger=logger,
                                project=proj,
                                messages=messages_file)

                config_refresh(doit=args.noop, logger=logger,
                               basedir=args.base,
                               messages=messages_file,
                               configmerge=configmerge_file,
                               roconfig=args.roconfig)
            elif args.delete:
                for proj in args.delete:
                    project_delete(doit=args.noop, logger=logger,
                                   project=proj,
                                   messages=messages_file)

                config_refresh(doit=args.noop, logger=logger,
                               basedir=args.base,
                               messages=messages_file,
                               configmerge=configmerge_file,
                               roconfig=args.roconfig)
            elif args.refresh:
                config_refresh(doit=args.noop, logger=logger,
                               basedir=args.base,
                               messages=messages_file,
                               configmerge=configmerge_file,
                               roconfig=args.roconfig)
            else:
                parser.print_help()
                sys.exit(1)

            if args.upload:
                main_config = get_config_file(basedir=args.base)
                if path.isfile(main_config):
                    exec_command(doit=args.noop, logger=logger,
                                 cmd=[messages_file, '-n', 'config', '-t',
                                      'setconf', main_config],
                                 msg="cannot upload configuration to webapp")
                else:
                    logger.error("file {} does not exist".format(main_config))
                    sys.exit(1)
    except Timeout:
        logger.warning("Already running, exiting.")
        sys.exit(1)
