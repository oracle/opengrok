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
# Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
# Portions Copyright (c) 2017-2018, Chris Fraire <cfraire@me.com>.
#

import os
import sys
import argparse
from utils import get_command
from command import Command
from java import Java, get_javaparser
import logging


"""
  opengrok.jar wrapper

  This script can be used to run the OpenGrok indexer.
"""


class Indexer(Java):
    """
    Wrapper class to make it easier to execute the OpenGrok indexer.
    """

    def __init__(self, command, logger=None, java=None, jar='opengrok.jar',
                 java_opts=None, env_vars=None):

        java_options = []
        java_options.extend(self.get_SCM_properties(logger))
        if java_opts:
            java_options.extend(java_opts)
        logger.debug("Java options: {}".format(java_options))

        super().__init__(command, jar=jar, java=java, java_opts=java_options,
                         logger=logger, env_vars=env_vars)

    def get_SCM_properties(self, logger):
        """
        Return list of Java System properties that contain valid paths to
        SCM commands.
        """
        SCM_COMMANDS = {
            'bk': '-Dorg.opengrok.indexer.history.BitKeeper',
            'hg': '-Dorg.opengrok.indexer.history.Mercurial',
            'cvs': '-Dorg.opengrok.indexer.history.cvs',
            'svn': '-Dorg.opengrok.indexer.history.Subversion',
            'sccs': '-Dorg.opengrok.indexer.history.SCCS',
            'cleartool': '-Dorg.opengrok.indexer.history.ClearCase',
            'git': '-Dorg.opengrok.indexer.history.git',
            'p4': '-Dorg.opengrok.indexer.history.Perforce',
            'mtn': '-Dorg.opengrok.indexer.history.Monotone',
            'blame': '-Dorg.opengrok.indexer.history.RCS',
            'bzr': '-Dorg.opengrok.indexer.history.Bazaar'}

        properties = []
        for cmd in SCM_COMMANDS.keys():
            executable = get_command(logger, None, cmd, level=logging.INFO)
            if executable:
                properties.append("{}={}".
                                  format(SCM_COMMANDS[cmd], executable))

        return properties


def FindCtags(logger):
    """
    Search for Universal ctags intelligently, skipping over other ctags
    implementations. Return path to the command or None if not found.
    """
    binary = None
    logger.debug("Trying to find ctags binary")
    for program in ['universal-ctags', 'ctags']:
        executable = get_command(logger, None, program, level=logging.DEBUG)
        if executable:
            # Verify that this executable is or is Universal Ctags
            # by matching the output when run with --version.
            logger.debug("Checking ctags command {}".format(executable))
            cmd = Command([executable, '--version'], logger=logger)
            cmd.execute()

            output_str = cmd.getoutputstr()
            if output_str and output_str.find("Universal Ctags") != -1:
                logger.debug("Got valid ctags binary: {}".format(executable))
                binary = executable
                break

    return binary


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description='OpenGrok indexer wrapper',
                                     parents=[get_javaparser()])
    parser.add_argument('-C', '--no_ctags_check', action='store_true',
                        default=False, help='Suppress checking for ctags')

    args = parser.parse_args()

    if args.debug:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig()

    logger = logging.getLogger(os.path.basename(sys.argv[0]))

    #
    # Since it is not possible to tell what kind of action is performed,
    # always check ctags presence unless told not to.
    #
    if not args.no_ctags_check and not FindCtags(logger):
        logger.warning("Unable to determine Universal CTags command")

    indexer = Indexer(args.options, logger=logger, java=args.java,
                      jar=args.jar, java_opts=args.java_opts,
                      env_vars=args.environment)
    indexer.execute()
    ret = indexer.getretcode()
    if ret is None or ret != 0:
        logger.error(indexer.getoutputstr())
        logger.error("Indexer command failed (return code {})".format(ret))
        sys.exit(1)
