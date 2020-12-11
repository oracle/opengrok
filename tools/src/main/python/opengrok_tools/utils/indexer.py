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


from .utils import get_command
from .command import Command
from .java import Java
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
                 java_opts=None, env_vars=None, doprint=False):

        java_options = []
        if java_opts:
            java_options.extend(java_opts)
        java_options = merge_properties(java_options,
                                        get_SCM_properties(logger))
        logger.debug("Java options: {}".format(java_options))

        super().__init__(command, jar=jar, java=java, java_opts=java_options,
                         logger=logger, env_vars=env_vars, doprint=doprint)


def get_SCM_properties(logger):
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


def merge_properties(base, extra):
    """
    Merge two lists of options (strings in the form of name=value).
    Take everything from base and add properties from extra
    (according to names) that are not present in the base.
    :param base: list of properties
    :param extra: list of properties
    :return: merged list
    """

    extra_prop_names = set(map(lambda x: x.split('=')[0], base))

    ret = set(base)
    for item in extra:
        nv = item.split("=")
        if nv[0] not in extra_prop_names:
            ret.add(item)

    return list(ret)


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
